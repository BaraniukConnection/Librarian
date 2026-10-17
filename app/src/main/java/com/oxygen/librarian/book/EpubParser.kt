package com.oxygen.librarian.book

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

/** EPUB 2 і 3: container.xml → OPF → spine; назви розділів — зі змісту (nav або NCX). */
object EpubParser {

    fun parse(file: File): ParsedBook = ZipFile(file).use { zip ->
        fun bytes(path: String): ByteArray? = zip.getEntry(path)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
        fun xml(path: String): Document? = bytes(path)?.let { Jsoup.parse(String(it, Charsets.UTF_8), "", Parser.xmlParser()) }

        val container = xml("META-INF/container.xml") ?: error("not an epub")
        val opfPath = container.byLocalName("rootfile").firstOrNull()?.attr("full-path")?.takeIf { it.isNotBlank() }
            ?: error("no rootfile")
        val opf = xml(opfPath) ?: error("no opf")
        val base = opfPath.substringBeforeLast('/', "")

        val title = opf.byLocalName("title").firstOrNull()?.text()?.trim().orEmpty()
        val author = opf.byLocalName("creator").firstOrNull()?.text()?.trim().orEmpty()
        val language = opf.byLocalName("language").firstOrNull()?.text()?.trim().orEmpty()

        class Item(val id: String, val href: String, val type: String, val props: String)
        val items = opf.byLocalName("item").map {
            Item(it.attr("id"), resolve(base, it.attr("href")), it.attr("media-type"), it.attr("properties"))
        }
        val byId = items.associateBy { it.id }

        val coverId = opf.byLocalName("meta").firstOrNull { it.attr("name") == "cover" }?.attr("content")
        val coverItem = items.firstOrNull { "cover-image" in it.props.split(' ') }
            ?: byId[coverId]?.takeIf { it.type.startsWith("image/") }
            ?: items.firstOrNull { it.type.startsWith("image/") && it.id.contains("cover", ignoreCase = true) }
        val cover = coverItem?.let { bytes(it.href) }

        val tocTitles = HashMap<String, String>()
        items.firstOrNull { "nav" in it.props.split(' ') }?.let { nav ->
            bytes(nav.href)?.let { raw ->
                val doc = Jsoup.parse(String(raw, Charsets.UTF_8), "", Parser.xmlParser())
                val navEl = doc.select("nav").firstOrNull { it.attr("epub:type") == "toc" } ?: doc.selectFirst("nav")
                val dir = nav.href.substringBeforeLast('/', "")
                navEl?.select("a[href]")?.forEach { a ->
                    tocTitles.putIfAbsent(resolve(dir, a.attr("href").substringBefore('#')), a.text().trim())
                }
            }
        }
        if (tocTitles.isEmpty()) {
            val ncxId = opf.byLocalName("spine").firstOrNull()?.attr("toc")
            val ncx = byId[ncxId] ?: items.firstOrNull { it.type == "application/x-dtbncx+xml" }
            ncx?.let { xml(it.href) }?.let { doc ->
                val dir = ncx.href.substringBeforeLast('/', "")
                doc.byLocalName("navPoint").forEach { point ->
                    val label = point.byLocalName("text").firstOrNull()?.text()?.trim().orEmpty()
                    val src = point.byLocalName("content").firstOrNull()?.attr("src").orEmpty()
                    if (src.isNotBlank()) tocTitles.putIfAbsent(resolve(dir, src.substringBefore('#')), label)
                }
            }
        }

        val chapters = opf.byLocalName("itemref").mapNotNull { ref ->
            val item = byId[ref.attr("idref")] ?: return@mapNotNull null
            if (!item.type.contains("html")) return@mapNotNull null
            val raw = bytes(item.href) ?: return@mapNotNull null
            // XML-, а не HTML-парсер: у XHTML буває `<title/>`, і HTML-парсер «з'їв» би за ним увесь розділ
            val doc = Jsoup.parse(raw.inputStream(), null, "", Parser.xmlParser())
            val builder = ChapterBuilder()
            builder.html(doc.byLocalName("body").firstOrNull() ?: doc)
            val chapter = builder.build(tocTitles[item.href].orEmpty())
            chapter.takeIf { it.text.isNotBlank() }
        }
        if (chapters.isEmpty()) error("empty epub")

        ParsedBook(BookMeta(title, author, language), splitLong(chapters), cover)
    }

    private fun resolve(dir: String, href: String): String {
        val decoded = runCatching { URLDecoder.decode(href.replace("+", "%2B"), "UTF-8") }.getOrDefault(href)
        val parts = ArrayDeque<String>()
        (if (dir.isEmpty()) decoded else "$dir/$decoded").split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> parts.removeLastOrNull()
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/")
    }
}

/** У OPF і NCX префікси просторів імен довільні (`dc:title`, `opf:item`…), тож шукаємо за локальним ім'ям. */
internal fun Element.byLocalName(name: String): List<Element> =
    getAllElements().filter { it.tagName().substringAfter(':') == name }
