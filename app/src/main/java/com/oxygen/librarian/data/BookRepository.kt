package com.oxygen.librarian.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import com.oxygen.librarian.book.BookFormat
import com.oxygen.librarian.book.BookParser
import com.oxygen.librarian.book.ParsedBook
import com.oxygen.librarian.data.db.Book
import com.oxygen.librarian.data.db.BookDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

sealed interface ImportResult {
    data class Added(val id: Long) : ImportResult
    data class Existing(val id: Long) : ImportResult
    data object Unsupported : ImportResult
}

/** Файли книг і обкладинок та розібраний текст. Книга копіюється до застосунку: читалка офлайн і не залежить від того, де лежав оригінал. */
class BookRepository(private val context: Context, private val books: BookDao) {

    private val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    private val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
    private val parsed = LruCache<Long, ParsedBook>(2)
    private val covers = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val parseLock = Mutex()

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        val tmp = File(booksDir, "import-${UUID.randomUUID()}")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            } ?: return@withContext ImportResult.Unsupported
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            books.byHash(hash)?.let { return@withContext ImportResult.Existing(it.id) }

            val format = BookParser.detect(tmp, name) ?: return@withContext ImportResult.Unsupported
            val book = runCatching { BookParser.parse(tmp, format, name) }.getOrNull()
                ?: return@withContext ImportResult.Unsupported

            val stem = UUID.randomUUID().toString()
            val file = File(booksDir, "$stem.${format.extension}")
            if (!tmp.renameTo(file)) return@withContext ImportResult.Unsupported
            val cover = book.cover?.let { bytes -> saveCover(stem, bytes) }
            val title = book.meta.title.ifBlank { name.substringBeforeLast('.').ifBlank { name } }
            val id = books.insert(
                Book(
                    title = title,
                    author = book.meta.author,
                    language = book.meta.language,
                    format = format.name,
                    file = file.name,
                    cover = cover,
                    // наступний колір по колу: сусідні обкладинки на полиці ніколи не однакові
                    color = ((books.lastColor() ?: -1) + 1) % 8,
                    hash = hash,
                    totalChars = book.totalChars,
                    addedAt = System.currentTimeMillis(),
                ),
            )
            parsed.put(id, book)
            ImportResult.Added(id)
        } finally {
            tmp.delete()
        }
    }

    /** Розібраний текст книги; тримаємо дві останні — щоб повернення до читання було миттєвим. */
    suspend fun content(book: Book): ParsedBook = parseLock.withLock {
        parsed.get(book.id) ?: withContext(Dispatchers.IO) {
            BookParser.parse(File(booksDir, book.file), BookFormat.valueOf(book.format), book.file)
        }.also { parsed.put(book.id, it) }
    }

    suspend fun delete(book: Book) = withContext(Dispatchers.IO) {
        books.delete(book)
        parsed.remove(book.id)
        File(booksDir, book.file).delete()
        book.cover?.let { File(coversDir, it).delete() }
    }

    /** Обкладинка, зменшена до розміру полиці. */
    suspend fun cover(name: String): Bitmap? = covers.get(name) ?: withContext(Dispatchers.IO) {
        val file = File(coversDir, name)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 360) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }?.also { covers.put(name, it) }

    private fun saveCover(stem: String, bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        val name = "$stem.img"
        File(coversDir, name).writeBytes(bytes)
        return name
    }

    private fun displayName(uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "book"
}
