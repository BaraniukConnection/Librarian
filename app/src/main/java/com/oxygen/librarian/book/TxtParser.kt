package com.oxygen.librarian.book

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Звичайний текст: кодування вгадується (UTF-8, інакше Windows-1251), розділи — за рядками «Розділ 5», «Chapter V»… */
object TxtParser {

    // (?!\p{L}) замість \b: у Java \b бачить межу слова лише між латинськими літерами
    private val HEADING = Regex(
        """^\s*((chapter|part|book|prologue|epilogue|розділ|глава|частина|книга|пролог|епілог|передмова)(?!\p{L}).{0,60}|[IVXLC]{1,7}\.?|\d{1,3}\.?)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(bytes: ByteArray, fileName: String): ParsedBook {
        val text = decode(bytes).replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.split('\n')
        val blank = lines.count { it.isBlank() }
        // є порожні рядки між абзацами — усередині абзацу рядки лише перенесені, зшиваємо їх
        val byBlank = blank > lines.size / 10
        val paragraphs = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotBlank()) paragraphs += current.toString().trim()
            current.setLength(0)
        }
        for (line in lines) {
            when {
                line.isBlank() -> flush()
                !byBlank || HEADING.matches(line) -> { flush(); current.append(line); flush() }
                else -> current.append(' ').append(line.trim())
            }
        }
        flush()

        val chapters = mutableListOf<Chapter>()
        var builder = ChapterBuilder()
        var title = ""
        var hasContent = false
        for (p in paragraphs) {
            if (p.length < 80 && HEADING.matches(p)) {
                if (hasContent) chapters += builder.build(title)
                builder = ChapterBuilder()
                title = p
                hasContent = false
                builder.text(p)
                builder.endBlock(BlockKind.HEADING)
            } else {
                builder.text(p)
                builder.endBlock(BlockKind.PARAGRAPH)
                hasContent = true
            }
        }
        if (hasContent || chapters.isEmpty()) chapters += builder.build(title)
        val nonEmpty = chapters.filter { it.text.isNotBlank() }
        if (nonEmpty.isEmpty()) error("empty text")
        return ParsedBook(BookMeta(fileName.substringBeforeLast('.'), "", ""), splitLong(nonEmpty), null)
    }

    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, Charsets.UTF_16LE).drop(1)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, Charsets.UTF_16BE).drop(1)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1251"))
        }
    }
}
