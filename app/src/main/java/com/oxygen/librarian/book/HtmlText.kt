package com.oxygen.librarian.book

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private val SKIP = setOf("script", "style", "head", "img", "svg", "image", "math", "video", "audio", "object", "iframe")
private val BLOCKS = setOf(
    "p", "div", "section", "article", "li", "dd", "dt", "tr", "td", "th", "pre", "figure", "figcaption",
    "header", "footer", "aside", "nav", "table", "ul", "ol", "dl", "body", "main", "hr", "center", "address",
)
private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")

/** Обходить XHTML-розділ EPUB і складає з нього абзаци. */
fun ChapterBuilder.html(node: Node, kind: BlockKind = BlockKind.PARAGRAPH) {
    for (child in node.childNodes()) {
        when (child) {
            is TextNode -> text(child.wholeText)
            is Element -> {
                val tag = child.normalName().substringAfter(':')
                when {
                    tag in SKIP -> Unit
                    tag == "br" -> endBlock(kind, lineBreak = true)
                    tag in HEADINGS -> blockOf(child, BlockKind.HEADING, kind)
                    tag == "blockquote" -> blockOf(child, BlockKind.QUOTE, kind)
                    tag in BLOCKS -> blockOf(child, kind, kind)
                    tag == "i" || tag == "em" || tag == "cite" || tag == "dfn" || tag == "var" ->
                        styled(Inline.ITALIC) { html(child, kind) }
                    tag == "b" || tag == "strong" -> styled(Inline.BOLD) { html(child, kind) }
                    else -> html(child, kind)
                }
            }
        }
    }
}

private fun ChapterBuilder.blockOf(el: Element, inner: BlockKind, outer: BlockKind) {
    endBlock(outer)
    html(el, inner)
    endBlock(inner)
}
