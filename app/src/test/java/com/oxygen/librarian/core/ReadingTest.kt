package com.oxygen.librarian.core

import com.oxygen.librarian.book.BlockKind
import com.oxygen.librarian.book.BookMeta
import com.oxygen.librarian.book.ChapterBuilder
import com.oxygen.librarian.book.ParsedBook
import com.oxygen.librarian.data.db.Book
import com.oxygen.librarian.data.db.BookStatus
import com.oxygen.librarian.data.prefs.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class ReadingTest {

    private fun book(vararg chapters: List<String>) = ParsedBook(
        BookMeta("T", "A", "en"),
        chapters.map { paragraphs ->
            ChapterBuilder().apply { paragraphs.forEach { text(it); endBlock(BlockKind.PARAGRAPH) } }.build("")
        },
        null,
    )

    @Test
    fun `fraction and positionAt are inverse`() {
        val b = book(listOf("a".repeat(100)), listOf("b".repeat(300)))
        assertEquals(400L, b.totalChars)
        assertEquals(0.5f, b.fraction(Position(1, 100)), 0.0001f)
        assertEquals(Position(1, 100), b.positionAt(0.5f))
        assertEquals(Position(0, 0), b.positionAt(0f))
        assertEquals(Position(1, 299), b.positionAt(1f))
    }

    @Test
    fun `sentence stays inside its paragraph`() {
        val b = book(listOf("Title", "I arrived here yesterday. My first task is to assure you. Done"))
        val c = b.chapters[0]
        val at = c.text.indexOf("first")
        val r = c.sentenceAt(at, Locale.ENGLISH)!!
        assertEquals("My first task is to assure you.", c.text.substring(r.first, r.last + 1))
        // заголовок без крапки не зливається з першим реченням абзацу
        val t = c.sentenceAt(2, Locale.ENGLISH)!!
        assertEquals("Title", c.text.substring(t.first, t.last + 1))
    }

    @Test
    fun `search is case insensitive with context`() {
        val b = book(listOf("The Breeze blew.", "Another breeze here."))
        val hits = b.search("breeze")
        assertEquals(2, hits.size)
        val h = hits[1]
        assertEquals("breeze", b.chapters[0].text.substring(h.start, h.end))
        assertEquals("breeze", h.snippet.substring(h.matchInSnippet.first, h.matchInSnippet.last + 1))
        assertEquals(emptyList<SearchHit>(), b.search("x"))
    }

    private fun entity(id: Long, title: String, author: String, status: BookStatus, opened: Long, added: Long) = Book(
        id = id, title = title, author = author, language = "", format = "EPUB", file = "", cover = null, color = 0,
        hash = "$id", totalChars = 1000, status = status, addedAt = added, openedAt = opened,
    )

    @Test
    fun `shelves filter and sort`() {
        val books = listOf(
            entity(1, "Кобзар", "Шевченко", BookStatus.FINISHED, 50, 1),
            entity(2, "Енеїда", "Котляревський", BookStatus.NEW, 0, 90),
            entity(3, "Frankenstein", "Shelley", BookStatus.READING, 100, 2),
        )
        assertEquals(listOf(3L, 2L, 1L), shelve(books, Shelf.ALL, SortOrder.RECENT, "").map { it.id })
        assertEquals(listOf(2L), shelve(books, Shelf.TO_READ, SortOrder.RECENT, "").map { it.id })
        assertEquals(listOf(3L), shelve(books, Shelf.ALL, SortOrder.TITLE, "shel").map { it.id })
        assertEquals(listOf(2L, 1L), shelve(books, Shelf.ALL, SortOrder.TITLE, "", Locale.forLanguageTag("uk")).filter { it.id != 3L }.map { it.id })
        assertEquals(3L, currentlyReading(books)?.id)
        assertNull(currentlyReading(books.filter { it.status != BookStatus.READING }))
    }

    @Test
    fun `minutes are rounded up`() {
        assertEquals(0, minutesFor(0))
        assertEquals(1, minutesFor(10))
        assertEquals(2, minutesFor(CHARS_PER_MINUTE + 1L))
    }
}
