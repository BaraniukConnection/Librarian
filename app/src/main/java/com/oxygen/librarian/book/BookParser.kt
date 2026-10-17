package com.oxygen.librarian.book

import java.io.File
import java.util.zip.ZipFile

enum class BookFormat(val extension: String) { EPUB("epub"), FB2("fb2"), FB2_ZIP("fb2.zip"), TXT("txt") }

/** Формат визначається за вмістом, а не за назвою: з пошти й месенджерів файли часто приходять без розширення. */
object BookParser {

    fun detect(file: File, name: String): BookFormat? {
        val head = file.inputStream().use { input -> ByteArray(512).let { it.copyOf(input.read(it).coerceAtLeast(0)) } }
        val isZip = head.size >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
        if (isZip) {
            return runCatching {
                ZipFile(file).use { zip ->
                    when {
                        zip.getEntry("META-INF/container.xml") != null -> BookFormat.EPUB
                        zip.entries().asSequence().any { it.name.endsWith(".fb2", ignoreCase = true) } -> BookFormat.FB2_ZIP
                        else -> null
                    }
                }
            }.getOrNull()
        }
        val start = String(head, Charsets.ISO_8859_1)
        if (start.contains("<FictionBook") || name.lowercase().endsWith(".fb2")) return BookFormat.FB2
        if (start.startsWith("%PDF")) return null
        val utf16 = head.size >= 2 && (head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte() || head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte())
        // двійкові файли (картинки, документи) відсіюємо: у тексті нульових байтів не буває
        return if (utf16 || head.none { it == 0.toByte() }) BookFormat.TXT else null
    }

    fun parse(file: File, format: BookFormat, name: String): ParsedBook = when (format) {
        BookFormat.EPUB -> EpubParser.parse(file)
        BookFormat.FB2 -> Fb2Parser.parse(file.readBytes())
        BookFormat.FB2_ZIP -> ZipFile(file).use { zip ->
            val entry = zip.entries().asSequence().first { it.name.endsWith(".fb2", ignoreCase = true) }
            Fb2Parser.parse(zip.getInputStream(entry).use { it.readBytes() })
        }
        BookFormat.TXT -> TxtParser.parse(file.readBytes(), name)
    }
}
