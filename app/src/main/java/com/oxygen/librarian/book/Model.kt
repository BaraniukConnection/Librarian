package com.oxygen.librarian.book

/*
 * Книга всередині застосунку — лише текст: розділи з абзаців і курсив/жирний усередині.
 * Картинки, таблиці стилів і верстка видавця відкидаються: читанню вони заважають частіше, ніж допомагають.
 */

enum class BlockKind { HEADING, PARAGRAPH, QUOTE, VERSE }

enum class Inline { ITALIC, BOLD }

/**
 * Абзац як діапазон у [Chapter.text]. Абзаци йдуть впритул, без `\n` між ними:
 * рядок розриває сама розмітка, а зміщення в тексті збігаються зі зміщеннями на екрані.
 * [continued] — продовження після `<br>`: той самий абзац, тож без червоного рядка.
 */
data class Block(val start: Int, val end: Int, val kind: BlockKind, val continued: Boolean = false)

data class Span(val start: Int, val end: Int, val style: Inline)

data class Chapter(val title: String, val text: String, val blocks: List<Block>, val spans: List<Span>) {
    fun blockAt(offset: Int): Block? {
        if (blocks.isEmpty()) return null
        var lo = 0
        var hi = blocks.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (blocks[mid].start <= offset) lo = mid else hi = mid - 1
        }
        return blocks[lo]
    }
}

data class BookMeta(val title: String, val author: String, val language: String)

class ParsedBook(val meta: BookMeta, val chapters: List<Chapter>, val cover: ByteArray?) {
    /** Початок кожного розділу в символах від початку книги; останній елемент — довжина всієї книги. */
    val starts: LongArray = LongArray(chapters.size + 1).also { acc ->
        chapters.forEachIndexed { i, c -> acc[i + 1] = acc[i] + c.text.length }
    }

    val totalChars: Long get() = starts.last()

    /** Назва для верху сторінки: у частин довгого розділу своєї немає — беремо найближчу вище. */
    fun displayTitle(chapter: Int): String {
        for (i in chapter.coerceAtMost(chapters.lastIndex) downTo 0) {
            if (chapters[i].title.isNotBlank()) return chapters[i].title
        }
        return meta.title
    }
}

/** Збирає розділ по шматочках: пробіли згортаються, як у браузері, порожні абзаци не лишаються. */
class ChapterBuilder {
    private val text = StringBuilder()
    private val blocks = mutableListOf<Block>()
    private val spans = mutableListOf<Span>()

    private val line = StringBuilder()
    private val lineSpans = mutableListOf<Span>()
    private var nextContinued = false

    fun text(raw: String) {
        for (ch in raw) {
            if (ch == '\u00AD') continue // м'які переноси видавця — переносимо самі
            // нерозривні пробіли (ініціали, «10 км») лишаються нерозривними
            if (ch.isWhitespace() && ch != '\u00A0' && ch != '\u202F') {
                if (line.isNotEmpty() && line.last() != ' ') line.append(' ')
            } else {
                line.append(ch)
            }
        }
    }

    // стиль може тягнутись через кілька абзаців (епіграф курсивом), тож відкриті стилі
    // живуть у лічильниках, а не прив'язані до одного рядка
    private val depth = IntArray(Inline.entries.size)
    private val openAt = IntArray(Inline.entries.size)

    fun begin(style: Inline) {
        if (depth[style.ordinal]++ == 0) openAt[style.ordinal] = line.length
    }

    fun end(style: Inline) {
        if (--depth[style.ordinal] == 0) lineSpans += Span(openAt[style.ordinal], line.length, style)
    }

    inline fun styled(style: Inline, body: () -> Unit) {
        begin(style)
        body()
        end(style)
    }

    /** Закінчити поточний абзац. [lineBreak] — це був `<br>`: наступний рядок — продовження. */
    fun endBlock(kind: BlockKind, lineBreak: Boolean = false) {
        Inline.entries.forEach { st ->
            if (depth[st.ordinal] > 0) lineSpans += Span(openAt[st.ordinal], line.length, st)
            openAt[st.ordinal] = 0
        }
        val lead = line.indexOfFirst { it != ' ' }
        if (lead < 0) {
            line.setLength(0)
            lineSpans.clear()
            if (lineBreak && blocks.isNotEmpty()) nextContinued = true
            return
        }
        var end = line.length
        while (end > lead && line[end - 1] == ' ') end--
        val start = text.length
        text.append(line, lead, end)
        blocks += Block(start, text.length, kind, continued = nextContinued)
        for (s in lineSpans) {
            val a = (s.start - lead).coerceIn(0, end - lead)
            val b = (s.end - lead).coerceIn(0, end - lead)
            if (b > a) spans += Span(start + a, start + b, s.style)
        }
        line.setLength(0)
        lineSpans.clear()
        nextContinued = lineBreak
    }

    fun build(title: String): Chapter {
        endBlock(BlockKind.PARAGRAPH)
        val heading = title.ifBlank {
            blocks.firstOrNull { it.kind == BlockKind.HEADING }?.let { text.substring(it.start, it.end) } ?: ""
        }
        return Chapter(heading.take(120), text.toString(), blocks.toList(), spans.toList())
    }
}

/** Максимум символів в одному розділі: довший ріжеться на частини, щоб розкладка сторінок не гальмувала. */
const val MAX_CHAPTER_CHARS = 40_000

/** Ріже задовгі розділи по межах абзаців. Частини після першої — без назви: у змісті вони не потрібні. */
fun splitLong(chapters: List<Chapter>, max: Int = MAX_CHAPTER_CHARS): List<Chapter> = chapters.flatMap { c ->
    if (c.text.length <= max || c.blocks.size < 2) return@flatMap listOf(c)
    val parts = mutableListOf<Chapter>()
    var from = 0
    while (from < c.blocks.size) {
        val base = c.blocks[from].start
        var to = from + 1
        while (to < c.blocks.size && c.blocks[to].end - base <= max) to++
        val end = c.blocks[to - 1].end
        parts += Chapter(
            title = if (parts.isEmpty()) c.title else "",
            text = c.text.substring(base, end),
            blocks = c.blocks.subList(from, to).map { it.copy(start = it.start - base, end = it.end - base) },
            spans = c.spans.filter { it.end > base && it.start < end }
                .map { Span((it.start - base).coerceAtLeast(0), (it.end - base).coerceAtMost(end - base), it.style) },
        )
        from = to
    }
    parts
}
