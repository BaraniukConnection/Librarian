package com.oxygen.librarian.core

import com.oxygen.librarian.book.Chapter
import com.oxygen.librarian.book.ParsedBook
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.ceil

/** Середня швидкість читання художнього тексту: ≈ 200 слів, або ≈ 1100 символів за хвилину. */
const val CHARS_PER_MINUTE = 1100

fun minutesFor(chars: Long): Int = if (chars <= 0) 0 else ceil(chars / CHARS_PER_MINUTE.toDouble()).toInt()

/** Місце в книзі: розділ і зміщення в його тексті. Не номер сторінки — сторінки змінюються разом із шрифтом. */
data class Position(val chapter: Int, val offset: Int)

fun ParsedBook.globalOffset(p: Position): Long =
    starts[p.chapter.coerceIn(0, chapters.lastIndex)] + p.offset

fun ParsedBook.fraction(p: Position): Float =
    if (totalChars == 0L) 0f else (globalOffset(p).toDouble() / totalChars).toFloat().coerceIn(0f, 1f)

/** Зворотне до [fraction]: куди перейти за часткою книги (повзунок унизу). */
fun ParsedBook.positionAt(fraction: Float): Position {
    val target = (fraction.coerceIn(0f, 1f) * totalChars).toLong()
    var chapter = starts.indexOfLast { it <= target }.coerceIn(0, chapters.lastIndex)
    if (chapter > 0 && starts[chapter] == totalChars) chapter--
    val offset = (target - starts[chapter]).toInt().coerceIn(0, (chapters[chapter].text.length - 1).coerceAtLeast(0))
    return Position(chapter, offset)
}

/** Речення під пальцем — в межах одного абзацу, без пробілів по краях. */
fun Chapter.sentenceAt(offset: Int, locale: Locale): IntRange? {
    val block = blockAt(offset) ?: return null
    val text = this.text.substring(block.start, block.end)
    val local = (offset - block.start).coerceIn(0, (text.length - 1).coerceAtLeast(0))
    val it = BreakIterator.getSentenceInstance(locale).apply { setText(text) }
    val end = it.following(local).let { e -> if (e == BreakIterator.DONE) text.length else e }
    val start = it.previous().let { s -> if (s == BreakIterator.DONE) 0 else s }
    return trimmed(text, start, end)?.let { (s, e) -> (block.start + s) until (block.start + e) }
}

/** Слово під пальцем — для ручок виділення, що чіпляються до меж слів. */
fun Chapter.wordBoundary(offset: Int, locale: Locale, forEnd: Boolean): Int {
    val block = blockAt(offset) ?: return offset
    val text = this.text.substring(block.start, block.end)
    val local = (offset - block.start).coerceIn(0, text.length)
    val it = BreakIterator.getWordInstance(locale).apply { setText(text) }
    val b = if (forEnd) {
        if (it.isBoundary(local)) local else it.following(local)
    } else {
        if (it.isBoundary(local)) local else it.preceding(local)
    }
    return block.start + if (b == BreakIterator.DONE) (if (forEnd) text.length else 0) else b
}

private fun trimmed(text: String, start: Int, end: Int): Pair<Int, Int>? {
    var s = start
    var e = end
    while (s < e && text[s].isWhitespace()) s++
    while (e > s && text[e - 1].isWhitespace()) e--
    return if (e > s) s to e else null
}

data class SearchHit(val chapter: Int, val start: Int, val end: Int, val snippet: String, val matchInSnippet: IntRange)

/** Пошук у книзі без урахування регістру. Збіги шукаються в межах абзацу; уривок — з контекстом навколо. */
fun ParsedBook.search(query: String, limit: Int = 300): List<SearchHit> {
    val q = query.trim()
    if (q.length < 2) return emptyList()
    val hits = mutableListOf<SearchHit>()
    chapters.forEachIndexed { ci, chapter ->
        for (block in chapter.blocks) {
            val text = chapter.text.substring(block.start, block.end)
            var from = 0
            while (true) {
                val at = text.indexOf(q, from, ignoreCase = true)
                if (at < 0) break
                val s = (at - 40).coerceAtLeast(0).let { i -> text.lastIndexOf(' ', i).takeIf { it in 0 until at }?.plus(1) ?: i }
                val e = (at + q.length + 60).coerceAtMost(text.length).let { i -> text.indexOf(' ', i).takeIf { it >= 0 } ?: text.length }
                val prefix = if (s > 0) "…" else ""
                val snippet = prefix + text.substring(s, e) + if (e < text.length) "…" else ""
                val m = prefix.length + at - s
                hits += SearchHit(ci, block.start + at, block.start + at + q.length, snippet, m until m + q.length)
                if (hits.size >= limit) return hits
                from = at + q.length
            }
        }
    }
    return hits
}
