package com.oxygen.librarian.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oxygen.librarian.core.Shelf
import com.oxygen.librarian.core.currentlyReading
import com.oxygen.librarian.core.shelve
import com.oxygen.librarian.data.BookRepository
import com.oxygen.librarian.data.ImportResult
import com.oxygen.librarian.data.db.Book
import com.oxygen.librarian.data.db.BookDao
import com.oxygen.librarian.data.db.BookStatus
import com.oxygen.librarian.data.prefs.AppPreferences
import com.oxygen.librarian.data.prefs.SortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryState(
    val loaded: Boolean = false,
    val total: Int = 0,
    val reading: Book? = null,
    val books: List<Book> = emptyList(),
    val shelf: Shelf = Shelf.ALL,
    val sort: SortOrder = SortOrder.RECENT,
    val query: String = "",
)

sealed interface LibraryEvent {
    data class Added(val count: Int) : LibraryEvent
    data object AlreadyThere : LibraryEvent
    data class Failed(val count: Int) : LibraryEvent
    data class Open(val bookId: Long) : LibraryEvent
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val books: BookDao,
    private val prefs: AppPreferences,
    val repository: BookRepository,
) : ViewModel() {

    private val shelf = MutableStateFlow(Shelf.ALL)
    private val query = MutableStateFlow("")
    val importing = MutableStateFlow(false)

    private val events = Channel<LibraryEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val state: StateFlow<LibraryState> = combine(books.observeAll(), shelf, prefs.sort, query) { all, s, sort, q ->
        LibraryState(
            loaded = true,
            total = all.size,
            // «Читаю зараз» — лише на загальній полиці без пошуку, інакше картка заважає знайти своє
            reading = if (s == Shelf.ALL && q.isBlank()) currentlyReading(all) else null,
            books = shelve(all, s, sort, q),
            shelf = s,
            sort = sort,
            query = q,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun setShelf(s: Shelf) { shelf.value = s }
    fun setQuery(q: String) { query.value = q }
    fun setSort(order: SortOrder) { viewModelScope.launch { prefs.setSort(order) } }

    /** Додати книги з вибору файлів. [open] — одразу відкрити (книга прийшла з іншого застосунку). */
    fun import(uris: List<Uri>, open: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importing.value = true
            var added = 0
            var existing = 0
            var failed = 0
            var last: Long? = null
            for (uri in uris) {
                when (val r = runCatching { repository.import(uri) }.getOrDefault(ImportResult.Unsupported)) {
                    is ImportResult.Added -> { added++; last = r.id }
                    is ImportResult.Existing -> { existing++; last = r.id }
                    ImportResult.Unsupported -> failed++
                }
            }
            importing.value = false
            when {
                open && last != null -> events.send(LibraryEvent.Open(last))
                failed > 0 -> events.send(LibraryEvent.Failed(failed))
                added > 0 -> events.send(LibraryEvent.Added(added))
                existing > 0 -> events.send(LibraryEvent.AlreadyThere)
            }
        }
    }

    fun setStatus(book: Book, status: BookStatus) {
        viewModelScope.launch { books.setStatus(book.id, status) }
    }

    fun delete(book: Book) {
        viewModelScope.launch { repository.delete(book) }
    }
}
