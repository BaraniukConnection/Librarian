package com.oxygen.librarian.feature.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.oxygen.librarian.book.BlockKind
import com.oxygen.librarian.book.Chapter
import com.oxygen.librarian.book.Inline
import com.oxygen.librarian.book.ParsedBook
import com.oxygen.librarian.data.prefs.ReaderSettings
import com.oxygen.librarian.ui.theme.readerFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import java.util.Locale
import kotlin.math.abs

/*
 * Сторінки. Розділ розкладається один раз цілим (TextLayoutResult), а сторінка — це вікно
 * в цю розкладку між двома рядками. Тому перенос і вирівнювання абзацу, що переходить на
 * наступну сторінку, такі самі, як були б без поділу, а малювання — лише видимих рядків.
 */

/** Розмір текстового поля сторінки в пікселях і налаштування, від яких залежить розкладка. */
data class PageSpec(val width: Int, val height: Int, val settings: ReaderSettings, val locale: Locale, val hyphenWidth: Float)

class Page(val first: Int, val last: Int, val top: Float, val bottom: Float, val start: Int, val end: Int)

class ChapterPages(
    val chapter: Int,
    private val text: Chapter,
    val layout: TextLayoutResult,
    val pages: List<Page>,
    /** Приблизна ширина дефіса переносу, px: він малюється, але в зміщеннях тексту його немає. */
    private val hyphenWidth: Float,
) {
    val length: Int get() = text.text.length

    fun pageOf(offset: Int): Int {
        var lo = 0
        var hi = pages.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (pages[mid].start <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    // ---------- Геометрія з урахуванням вирівнювання по ширині ----------
    // Compose малює рядок розтягнутим, але позиції символів (getHorizontalPosition, getPathForRange,
    // getOffsetForPosition) повертає нерозтягнутими — підсвітка з'їжджала б ліворуч. Тож розтягуємо самі:
    // зайва ширина рядка ділиться порівну між пробілами, як це робить Android.

    private val extra = FloatArray(layout.lineCount) { Float.NaN }

    private fun stretch(line: Int): Float {
        if (!extra[line].isNaN()) return extra[line]
        val start = layout.getLineStart(line)
        val end = layout.getLineEnd(line, visibleEnd = true)
        val block = text.blockAt(start)
        val justified = block != null && end < block.end &&
            (block.kind == BlockKind.PARAGRAPH || block.kind == BlockKind.QUOTE)
        var e = 0f
        if (justified) {
            val hyphenated = end > 0 && end < text.text.length && text.text[end - 1].isLetter() && text.text[end].isLetter()
            val natural = layout.getHorizontalPosition(end, true) + if (hyphenated) hyphenWidth else 0f
            e = (layout.size.width - natural).coerceAtLeast(0f)
        }
        extra[line] = e
        return e
    }

    /** X символу [offset] на рядку [line] так, як його видно на екрані. */
    fun x(offset: Int, line: Int = layout.getLineForOffset(offset)): Float {
        // кінець рядка: те саме зміщення вже належить початку наступного рядка (абзаци йдуть впритул),
        // тож беремо правий край цього рядка, а не позицію в наступному
        if (offset > 0 && layout.getLineForOffset(offset) != line) {
            return (layout.getLineRight(line) + stretch(line)).coerceAtMost(layout.size.width.toFloat())
        }
        val natural = layout.getHorizontalPosition(offset, true)
        val e = stretch(line)
        if (e == 0f) return natural
        val start = layout.getLineStart(line)
        val end = layout.getLineEnd(line, visibleEnd = true)
        var total = 0
        var before = 0
        for (i in start until end) {
            if (text.text[i] == ' ') {
                total++
                if (i < offset) before++
            }
        }
        return if (total == 0) natural else natural + e * before / total
    }

    /** Зміщення під точкою (координати розкладки). */
    fun offsetAt(p: Offset): Int {
        val line = layout.getLineForVerticalPosition(p.y)
        if (stretch(line) == 0f) return layout.getOffsetForPosition(p)
        val start = layout.getLineStart(line)
        val end = layout.getLineEnd(line, visibleEnd = true)
        var best = start
        var bestDistance = Float.MAX_VALUE
        for (i in start..end) {
            val d = abs(x(i, line) - p.x)
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        // точка за серединою символу — курсор після нього
        return if (best < end && p.x > (x(best, line) + x(best + 1, line)) / 2) best + 1 else best
    }

    /** Фон під діапазоном тексту — прямокутник на кожен рядок. */
    fun path(start: Int, end: Int): Path {
        val path = Path()
        if (end <= start) return path
        val first = layout.getLineForOffset(start)
        val last = layout.getLineForOffset((end - 1).coerceAtLeast(start))
        for (line in first..last) {
            val a = maxOf(start, layout.getLineStart(line))
            val b = minOf(end, layout.getLineEnd(line, visibleEnd = true))
            if (b <= a) continue
            path.addRect(Rect(x(a, line), layout.getLineTop(line), x(b, line), layout.getLineBottom(line)))
        }
        return path
    }

    /** Вертикальна риска курсора перед [offset]: x, верх і низ рядка. [rangeEnd] — кінець виділення: риска після попереднього символу. */
    fun cursor(offset: Int, rangeEnd: Boolean = false): Rect {
        val line = layout.getLineForOffset(if (rangeEnd && offset > 0) offset - 1 else offset)
        val x = x(offset, line)
        return Rect(x, layout.getLineTop(line), x, layout.getLineBottom(line))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private val layoutDispatcher = Dispatchers.Default.limitedParallelism(1)

/** Розкладає розділи у фоні й тримає їх, поки не зміниться розмір екрана чи налаштування. Доступ — з головного потоку. */
class Paginator(private val book: ParsedBook, private val measurer: TextMeasurer, val spec: PageSpec) {
    private val scope = CoroutineScope(SupervisorJob() + layoutDispatcher)
    private val cache = HashMap<Int, Deferred<ChapterPages>>()

    val chapterCount get() = book.chapters.size

    @OptIn(ExperimentalCoroutinesApi::class)
    fun peek(chapter: Int): ChapterPages? =
        cache[chapter]?.takeIf { it.isCompleted && !it.isCancelled }?.getCompleted()

    suspend fun get(chapter: Int): ChapterPages {
        // далекі розділи не тримаємо: кожен — це ціла розкладка в пам'яті
        cache.keys.filter { kotlin.math.abs(it - chapter) > 2 }.forEach { cache.remove(it)?.cancel() }
        return cache.getOrPut(chapter) { scope.async { layout(chapter) } }.await()
    }

    fun close() = scope.cancel()

    private fun layout(index: Int): ChapterPages {
        val chapter = book.chapters[index]
        val s = spec.settings
        val lineHeight = (s.fontSize * s.spacing.multiplier).sp
        val base = TextStyle(
            fontFamily = readerFamily(s.font),
            fontSize = s.fontSize.sp,
            lineHeight = lineHeight,
            localeList = LocaleList(spec.locale.toLanguageTag()),
            textAlign = TextAlign.Justify,
            hyphens = if (s.hyphenation) Hyphens.Auto else Hyphens.None,
            lineBreak = LineBreak.Paragraph,
            // без обрізання: інакше перший рядок сторінки був би нижчим за решту
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
        val result = measurer.measure(
            text = annotate(chapter, s.hyphenation),
            style = base,
            constraints = Constraints(maxWidth = spec.width),
        )
        return ChapterPages(index, chapter, result, paginate(result, chapter, spec.height.toFloat()), spec.hyphenWidth)
    }
}

/** Ділить розкладку на сторінки по межах рядків. Заголовок у самому низу сторінки переноситься на наступну. */
internal fun paginate(layout: TextLayoutResult, chapter: Chapter, height: Float): List<Page> {
    val pages = mutableListOf<Page>()
    val count = layout.lineCount
    var first = 0
    while (first < count) {
        val top = layout.getLineTop(first)
        var last = first
        while (last + 1 < count && layout.getLineBottom(last + 1) - top <= height) last++
        if (last > first && last + 1 < count && chapter.blockAt(layout.getLineStart(last))?.kind == BlockKind.HEADING) last--
        pages += Page(first, last, top, layout.getLineBottom(last), layout.getLineStart(first), layout.getLineEnd(last))
        first = last + 1
    }
    if (pages.isEmpty()) pages += Page(0, 0, 0f, 0f, 0, 0)
    return pages
}

/** Розмітка розділу: вигляд абзаців за їх видом, курсив і жирний. Зміщення збігаються з [Chapter.text]. */
internal fun annotate(chapter: Chapter, hyphenation: Boolean): AnnotatedString = buildAnnotatedString {
    append(chapter.text)
    val hyphens = if (hyphenation) Hyphens.Auto else Hyphens.None
    var previous: BlockKind? = null
    for (b in chapter.blocks) {
        val style = when (b.kind) {
            BlockKind.HEADING -> ParagraphStyle(
                textAlign = TextAlign.Start,
                hyphens = Hyphens.None,
                // півтора рядка з текстом посередині — повітря над і під заголовком
                lineHeight = 2.2.em,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            )
            BlockKind.PARAGRAPH -> ParagraphStyle(
                textAlign = TextAlign.Justify,
                hyphens = hyphens,
                // червоний рядок — лише між звичайними абзацами, як у книжці
                textIndent = if (previous == BlockKind.PARAGRAPH && !b.continued) TextIndent(firstLine = 1.4.em) else null,
            )
            BlockKind.QUOTE -> ParagraphStyle(textAlign = TextAlign.Justify, hyphens = hyphens, textIndent = TextIndent(2.em, 2.em))
            BlockKind.VERSE -> ParagraphStyle(textAlign = TextAlign.Start, hyphens = Hyphens.None, textIndent = TextIndent(1.5.em, 3.em))
        }
        addStyle(style, b.start, b.end)
        if (b.kind == BlockKind.HEADING) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 1.22.em), b.start, b.end)
        previous = b.kind
    }
    for (s in chapter.spans) {
        addStyle(
            when (s.style) {
                Inline.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                Inline.BOLD -> SpanStyle(fontWeight = FontWeight.SemiBold)
            },
            s.start,
            s.end,
        )
    }
}

/** Мова книги — для переносів і меж речень. Якщо в метаданих її немає, вгадуємо за літерами. */
fun bookLocale(book: ParsedBook): Locale {
    book.meta.language.takeIf { it.isNotBlank() }?.let { return Locale.forLanguageTag(it.replace('_', '-')) }
    val sample = book.chapters.firstOrNull()?.text?.take(4000).orEmpty()
    val cyrillic = sample.count { it in 'Ѐ'..'ӿ' }
    val latin = sample.count { it in 'a'..'z' || it in 'A'..'Z' }
    return when {
        cyrillic > latin && sample.any { it in "іїєґІЇЄҐ" } -> Locale.forLanguageTag("uk")
        cyrillic > latin && sample.any { it in "ыэъЫЭЪ" } -> Locale.forLanguageTag("ru")
        cyrillic > latin -> Locale.forLanguageTag("uk")
        else -> Locale.ENGLISH
    }
}
