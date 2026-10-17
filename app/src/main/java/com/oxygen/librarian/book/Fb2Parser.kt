package com.oxygen.librarian.book

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.util.Base64

/** FictionBook 2: розділ — секція верхнього рівня; вкладені секції без власного тексту передають назву першій дочірній. */
object Fb2Parser {

    fun parse(bytes: ByteArray): ParsedBook {
        val doc = Jsoup.parse(bytes.inputStream(), null, "", Parser.xmlParser())
        val root = doc.byLocalName("FictionBook").firstOrNull() ?: error("not fb2")
        val info = root.byLocalName("title-info").firstOrNull()

        val title = info?.byLocalName("book-title")?.firstOrNull()?.text()?.trim().orEmpty()
        val author = info?.byLocalName("author")?.firstOrNull()?.let { a ->
            listOf("first-name", "middle-name", "last-name")
                .mapNotNull { n -> a.byLocalName(n).firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() } }
                .joinToString(" ")
                .ifEmpty { a.byLocalName("nickname").firstOrNull()?.text()?.trim().orEmpty() }
        }.orEmpty()
        val language = info?.byLocalName("lang")?.firstOrNull()?.text()?.trim().orEmpty()

        val coverRef = info?.byLocalName("coverpage")?.firstOrNull()?.byLocalName("image")?.firstOrNull()
            ?.attributes()?.firstOrNull { it.key.substringAfter(':') == "href" }?.value?.removePrefix("#")
        val cover = coverRef?.let { id ->
            root.byLocalName("binary").firstOrNull { it.attr("id") == id }?.let { bin ->
                runCatching { Base64.getMimeDecoder().decode(bin.text()) }.getOrNull()
            }
        }

        val body = root.children().firstOrNull { it.localName() == "body" && it.attr("name") != "notes" }
            ?: error("no body")
        val chapters = mutableListOf<Chapter>()
        val sections = body.children().filter { it.localName() == "section" }
        if (sections.isEmpty()) {
            chapterOf(body, emptyList())?.let(chapters::add)
        } else {
            sections.forEach { collect(it, emptyList(), chapters) }
        }
        if (chapters.isEmpty()) error("empty fb2")
        return ParsedBook(BookMeta(title, author, language), splitLong(chapters), cover)
    }

    private fun collect(section: Element, inherited: List<Element>, out: MutableList<Chapter>) {
        val children = section.children().filter { it.localName() == "section" }
        val ownText = section.children().any { it.localName() !in setOf("section", "title", "epigraph", "image", "annotation") }
        if (children.isEmpty() || ownText) {
            chapterOf(section, inherited)?.let(out::add)
            children.forEach { collect(it, emptyList(), out) }
        } else {
            // «Частина перша» без власного тексту: її назва стає першим заголовком першого розділу
            val heads = inherited + section.children().filter { it.localName() in setOf("title", "epigraph") }
            children.forEachIndexed { i, child -> collect(child, if (i == 0) heads else emptyList(), out) }
        }
    }

    private fun chapterOf(section: Element, inherited: List<Element>): Chapter? {
        val b = ChapterBuilder()
        inherited.forEach { b.node(it, BlockKind.PARAGRAPH) }
        b.fb2(section, BlockKind.PARAGRAPH)
        val title = section.children().firstOrNull { it.localName() == "title" }
            ?.children()?.joinToString(". ") { it.text().trim() }.orEmpty()
        return b.build(title).takeIf { it.text.isNotBlank() }
    }

    private fun ChapterBuilder.fb2(parent: Node, kind: BlockKind) {
        parent.childNodes().forEach { node(it, kind) }
    }

    private fun ChapterBuilder.node(child: Node, kind: BlockKind) {
        when (child) {
            is TextNode -> text(child.wholeText)
            is Element -> when (child.localName()) {
                // кожна вкладена секція — окремий розділ (див. collect)
                "section", "image", "binary" -> Unit
                "title", "subtitle" -> block(child, BlockKind.HEADING)
                "p", "text-author", "td", "th" -> block(child, kind)
                "v", "poem", "stanza" -> block(child, BlockKind.VERSE)
                "epigraph" -> { endBlock(kind); styled(Inline.ITALIC) { fb2(child, BlockKind.QUOTE) }; endBlock(BlockKind.QUOTE) }
                "cite" -> block(child, BlockKind.QUOTE)
                "empty-line" -> endBlock(kind)
                "emphasis" -> styled(Inline.ITALIC) { fb2(child, kind) }
                "strong" -> styled(Inline.BOLD) { fb2(child, kind) }
                else -> fb2(child, kind)
            }
            else -> Unit
        }
    }

    private fun ChapterBuilder.block(el: Element, kind: BlockKind) {
        endBlock(kind)
        fb2(el, kind)
        endBlock(kind)
    }

    private fun Element.localName() = tagName().substringAfter(':')
}
