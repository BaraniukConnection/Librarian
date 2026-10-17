package com.oxygen.librarian.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class BookStatus { NEW, READING, FINISHED }

/**
 * Книга на полиці. Сам файл лежить у filesDir/books/[file], обкладинка — у filesDir/covers/[cover].
 * Місце читання — розділ і зміщення ([chapter], [offset]), а не сторінка: сторінки залежать від шрифту.
 */
@Entity(tableName = "books", indices = [Index(value = ["hash"], unique = true)])
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val language: String,
    val format: String,
    val file: String,
    val cover: String?,
    /** Колір згенерованої обкладинки — індекс у `mark-*`. */
    val color: Int,
    val hash: String,
    val totalChars: Long,
    val chapter: Int = 0,
    val offset: Int = 0,
    val chapterTitle: String = "",
    val progress: Float = 0f,
    val status: BookStatus = BookStatus.NEW,
    val addedAt: Long,
    val openedAt: Long = 0,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(entity = Book::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId")],
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val chapter: Int,
    val offset: Int,
    val chapterTitle: String,
    val snippet: String,
    val createdAt: Long,
)

@Entity(
    tableName = "highlights",
    foreignKeys = [ForeignKey(entity = Book::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId")],
)
data class Highlight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val chapter: Int,
    val start: Int,
    val end: Int,
    val text: String,
    val chapterTitle: String,
    /** Індекс у восьми `mark-*`. */
    val color: Int,
    val note: String = "",
    val createdAt: Long,
)

@Dao
interface BookDao {
    @Query("SELECT * FROM books") fun observeAll(): Flow<List<Book>>
    @Query("SELECT * FROM books WHERE id = :id") fun observe(id: Long): Flow<Book?>
    @Query("SELECT * FROM books WHERE id = :id") suspend fun get(id: Long): Book?
    @Query("SELECT * FROM books WHERE hash = :hash") suspend fun byHash(hash: String): Book?
    @Query("SELECT color FROM books ORDER BY id DESC LIMIT 1") suspend fun lastColor(): Int?
    @Insert suspend fun insert(book: Book): Long
    @Update suspend fun update(book: Book)
    @Delete suspend fun delete(book: Book)

    @Query(
        "UPDATE books SET chapter = :chapter, `offset` = :offset, chapterTitle = :chapterTitle, progress = :progress, " +
            "status = :status, openedAt = :openedAt WHERE id = :id",
    )
    suspend fun savePosition(id: Long, chapter: Int, offset: Int, chapterTitle: String, progress: Float, status: BookStatus, openedAt: Long)

    @Query("UPDATE books SET status = :status WHERE id = :id") suspend fun setStatus(id: Long, status: BookStatus)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY chapter, `offset`") fun observe(bookId: Long): Flow<List<Bookmark>>
    @Insert suspend fun insert(bookmark: Bookmark): Long
    @Delete suspend fun delete(bookmark: Bookmark)
}

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY chapter, start") fun observe(bookId: Long): Flow<List<Highlight>>
    @Query("SELECT * FROM highlights ORDER BY bookId, chapter, start") fun observeAll(): Flow<List<Highlight>>
    @Insert suspend fun insert(highlight: Highlight): Long
    @Update suspend fun update(highlight: Highlight)
    @Delete suspend fun delete(highlight: Highlight)
}

@Database(entities = [Book::class, Bookmark::class, Highlight::class], version = 1)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun highlights(): HighlightDao
}
