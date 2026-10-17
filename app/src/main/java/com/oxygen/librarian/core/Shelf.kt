package com.oxygen.librarian.core

import com.oxygen.librarian.data.db.Book
import com.oxygen.librarian.data.db.BookStatus
import com.oxygen.librarian.data.prefs.SortOrder
import java.text.Collator
import java.util.Locale

/** Полиці-фільтри бібліотеки: чіпи під заголовком. */
enum class Shelf { ALL, READING, TO_READ, FINISHED }

fun Book.on(shelf: Shelf): Boolean = when (shelf) {
    Shelf.ALL -> true
    Shelf.READING -> status == BookStatus.READING
    Shelf.TO_READ -> status == BookStatus.NEW
    Shelf.FINISHED -> status == BookStatus.FINISHED
}

/** Що показати на полиці: фільтр, пошук за назвою й автором, порядок. */
fun shelve(books: List<Book>, shelf: Shelf, sort: SortOrder, query: String, locale: Locale = Locale.getDefault()): List<Book> {
    val q = query.trim()
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    val filtered = books.filter { b ->
        b.on(shelf) && (q.isEmpty() || b.title.contains(q, ignoreCase = true) || b.author.contains(q, ignoreCase = true))
    }
    return when (sort) {
        // нещодавні — за останнім відкриттям, а ще не відкриті — за часом додавання
        SortOrder.RECENT -> filtered.sortedByDescending { maxOf(it.openedAt, it.addedAt) }
        SortOrder.TITLE -> filtered.sortedWith { a, b -> collator.compare(a.title, b.title) }
        SortOrder.AUTHOR -> filtered.sortedWith { a, b ->
            collator.compare(a.author.ifBlank { "￿" }, b.author.ifBlank { "￿" }).takeIf { it != 0 }
                ?: collator.compare(a.title, b.title)
        }
    }
}

/** Картка «Читаю зараз» — остання відкрита недочитана книга. */
fun currentlyReading(books: List<Book>): Book? =
    books.filter { it.status == BookStatus.READING }.maxByOrNull { it.openedAt }

/** Скільки лишилось читати, у хвилинах. */
fun Book.minutesLeft(): Int = minutesFor((totalChars * (1f - progress)).toLong())
