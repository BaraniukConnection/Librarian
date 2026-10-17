package com.oxygen.librarian.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ParsersTest {

    @Test
    fun `builder collapses whitespace and keeps styles inside the paragraph`() {
        val b = ChapterBuilder()
        b.text("  Hello \n  ")
        b.styled(Inline.ITALIC) { b.text("dear") }
        b.text("   world  ")
        b.endBlock(BlockKind.PARAGRAPH)
        val c = b.build("")
        assertEquals("Hello dear world", c.text)
        assertEquals(listOf(Span(6, 10, Inline.ITALIC)), c.spans)
    }

    @Test
    fun `paragraphs sit back to back and line break marks continuation`() {
        val b = ChapterBuilder()
        b.text("One")
        b.endBlock(BlockKind.PARAGRAPH, lineBreak = true)
        b.text("Two")
        b.endBlock(BlockKind.PARAGRAPH)
        b.text("Three")
        val c = b.build("T")
        assertEquals("OneTwoThree", c.text)
        assertEquals(listOf(false, true, false), c.blocks.map { it.continued })
        assertEquals(Block(3, 6, BlockKind.PARAGRAPH, true), c.blockAt(4))
    }

    @Test
    fun `style spanning several paragraphs is split per paragraph`() {
        val b = ChapterBuilder()
        b.styled(Inline.ITALIC) {
            b.text("First")
            b.endBlock(BlockKind.QUOTE)
            b.text("Second")
            b.endBlock(BlockKind.QUOTE)
        }
        val c = b.build("")
        assertEquals(listOf(Span(0, 5, Inline.ITALIC), Span(5, 11, Inline.ITALIC)), c.spans)
    }

    @Test
    fun `long chapters are split on paragraph boundaries`() {
        val b = ChapterBuilder()
        repeat(10) { b.text("x".repeat(10)); b.endBlock(BlockKind.PARAGRAPH) }
        val parts = splitLong(listOf(b.build("Big")), max = 35)
        assertEquals(4, parts.size)
        assertEquals("Big", parts[0].title)
        assertEquals("", parts[1].title)
        assertEquals(100, parts.sumOf { it.text.length })
        assertTrue(parts.all { p -> p.blocks.first().start == 0 && p.blocks.last().end == p.text.length })
    }

    @Test
    fun `txt finds chapters and joins wrapped lines`() {
        val text = "Розділ 1\n\nПерший рядок\nпродовження.\n\nДругий абзац.\n\nРозділ 2\n\nТекст."
        val book = TxtParser.parse(text.toByteArray(), "Книга.txt")
        assertEquals("Книга", book.meta.title)
        assertEquals(2, book.chapters.size)
        assertEquals("Розділ 1", book.chapters[0].title)
        assertTrue(book.chapters[0].text.contains("Перший рядок продовження."))
        assertEquals(BlockKind.HEADING, book.chapters[0].blocks[0].kind)
    }

    @Test
    fun `txt falls back to windows-1251`() {
        val bytes = "Привіт, світе".toByteArray(charset("windows-1251"))
        assertEquals("Привіт, світе", TxtParser.decode(bytes))
    }

    @Test
    fun `fb2 reads meta, sections, epigraph and cover`() {
        val fb2 = """<?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
              <description><title-info>
                <author><first-name>Леся</first-name><last-name>Українка</last-name></author>
                <book-title>Лісова пісня</book-title><lang>uk</lang>
                <coverpage><image l:href="#c.jpg"/></coverpage>
              </title-info></description>
              <body>
                <section><title><p>Пролог</p></title>
                  <epigraph><p>Епіграф</p></epigraph>
                  <p>Текст <emphasis>курсивом</emphasis>.</p>
                </section>
                <section><title><p>Дія перша</p></title>
                  <section><title><p>Ява 1</p></title><p>Я в серці маю те, що не вмирає.</p></section>
                </section>
              </body>
              <body name="notes"><section><p>Примітка</p></section></body>
              <binary id="c.jpg" content-type="image/jpeg">AAEC</binary>
            </FictionBook>"""
        val book = Fb2Parser.parse(fb2.toByteArray())
        assertEquals("Лісова пісня", book.meta.title)
        assertEquals("Леся Українка", book.meta.author)
        assertEquals("uk", book.meta.language)
        assertNotNull(book.cover)
        assertEquals(listOf("Пролог", "Ява 1"), book.chapters.map { it.title })
        val prologue = book.chapters[0]
        assertEquals(BlockKind.QUOTE, prologue.blocks[1].kind)
        assertTrue(prologue.spans.any { it.style == Inline.ITALIC && prologue.text.substring(it.start, it.end) == "курсивом" })
        // назва «Дія перша» без власного тексту стає заголовком першого розділу всередині
        assertTrue(book.chapters[1].text.startsWith("Дія перша"))
        assertFalse(book.chapters.any { it.text.contains("Примітка") })
    }

    @Test
    fun `epub follows spine, toc titles and skips xhtml title trap`() {
        val file = File.createTempFile("book", ".epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
            }
            put("mimetype", "application/epub+zip")
            put(
                "META-INF/container.xml",
                """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
            )
            put(
                "OEBPS/content.opf",
                """<package xmlns:dc="http://purl.org/dc/elements/1.1/"><metadata>
                   <dc:title>Frankenstein</dc:title><dc:creator>Mary Shelley</dc:creator><dc:language>en</dc:language>
                   </metadata><manifest>
                   <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                   <item id="c1" href="text/letter%201.xhtml" media-type="application/xhtml+xml"/>
                   <item id="c2" href="text/c2.xhtml" media-type="application/xhtml+xml"/>
                   </manifest><spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>""",
            )
            put(
                "OEBPS/nav.xhtml",
                """<html><body><nav epub:type="toc"><ol><li><a href="text/letter%201.xhtml#p">Letter 1</a></li></ol></nav></body></html>""",
            )
            put(
                "OEBPS/text/letter 1.xhtml",
                """<html><head><title/></head><body><h1>Letter 1</h1><p>You will <i>rejoice</i>&nbsp;to hear.<br/>New line</p></body></html>""",
            )
            put("OEBPS/text/c2.xhtml", """<html><body><h2>Letter 2</h2><p>How slowly the time passes.</p></body></html>""")
        }
        val book = EpubParser.parse(file)
        file.delete()
        assertEquals("Frankenstein", book.meta.title)
        assertEquals("Mary Shelley", book.meta.author)
        assertEquals(listOf("Letter 1", "Letter 2"), book.chapters.map { it.title })
        val c = book.chapters[0]
        assertEquals("Letter 1You will rejoice to hear.New line", c.text)
        assertEquals(listOf(BlockKind.HEADING, BlockKind.PARAGRAPH, BlockKind.PARAGRAPH), c.blocks.map { it.kind })
        assertTrue(c.blocks[2].continued)
        assertEquals("rejoice", c.text.substring(c.spans[0].start, c.spans[0].end))
    }
}
