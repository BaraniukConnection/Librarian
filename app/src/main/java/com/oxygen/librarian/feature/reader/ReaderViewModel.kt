package com.oxygen.librarian.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.oxygen.librarian.book.ParsedBook
import com.oxygen.librarian.core.Position
import com.oxygen.librarian.core.SearchHit
import com.oxygen.librarian.core.fraction
import com.oxygen.librarian.core.search
import com.oxygen.librarian.data.BookRepository
import com.oxygen.librarian.data.db.Book
import com.oxygen.librarian.data.db.BookDao
import com.oxygen.librarian.data.db.BookStatus
import com.oxygen.librarian.data.db.Bookmark
import com.oxygen.librarian.data.db.BookmarkDao
import com.oxygen.librarian.data.db.Highlight
import com.oxygen.librarian.data.db.HighlightDao
import com.oxygen.librarian.data.prefs.AppPreferences
import com.oxygen.librarian.data.prefs.ReaderSettings
import com.oxygen.librarian.navigation.ReaderRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Куди дивиться читалка. [jump] росте на кожен перехід ззовні (зміст, пошук, повзунок), а не на гортання. */
data class Located(val pos: Position, val jump: Int)

/** Що видно зараз — для збереження місця. */
private data class Shown(val pos: Position, val atEnd: Boolean)

/** Знайдене в пошуку підсвічується, доки людина не гортне далі. */
data class SearchMark(val chapter: Int, val start: Int, val end: Int)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val books: BookDao,
    private val bookmarkDao: BookmarkDao,
    private val highlightDao: HighlightDao,
    private val prefs: AppPreferences,
    private val repository: BookRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val route = handle.toRoute<ReaderRoute>()
    val bookId = route.bookId

    val book: StateFlow<Book?> = books.observe(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val content = MutableStateFlow<ParsedBook?>(null)
    val failed = MutableStateFlow(false)
    val located = MutableStateFlow<Located?>(null)
    val searchMark = MutableStateFlow<SearchMark?>(null)
    private val shown = MutableStateFlow<Shown?>(null)

    val settings: StateFlow<ReaderSettings?> = prefs.reader.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val pageAnimation: StateFlow<Boolean> = prefs.pageAnimation.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val highlights: StateFlow<List<Highlight>> = highlightDao.observe(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = bookmarkDao.observe(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val query = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    val results: StateFlow<List<SearchHit>> = combine(query.debounce(250), content.filterNotNull()) { q, c -> c.search(q) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val b = books.get(bookId)
            val parsed = b?.let { runCatching { repository.content(it) }.getOrNull() }
            if (b == null || parsed == null) {
                failed.value = true
                return@launch
            }
            content.value = parsed
            val start = if (route.chapter >= 0) Position(route.chapter, route.offset) else Position(b.chapter, b.offset)
            located.value = Located(clamp(parsed, start), 1)
        }
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            shown.filterNotNull().debounce(400).collect { save(it) }
        }
    }

    private fun clamp(book: ParsedBook, p: Position): Position {
        val ch = p.chapter.coerceIn(0, book.chapters.lastIndex)
        return Position(ch, p.offset.coerceIn(0, book.chapters[ch].text.length))
    }

    /** Перейти ззовні: зміст, закладка, пошук, повзунок. */
    fun go(p: Position, mark: SearchMark? = null) {
        val book = content.value ?: return
        val current = located.value?.jump ?: 0
        located.value = Located(clamp(book, p), current + 1)
        searchMark.value = mark
        holdMark = mark != null
    }

    /** Перша показана сторінка після переходу з пошуку — та, де знахідка; її підсвітку не знімаємо. */
    private var holdMark = false

    /** Сторінка (або верх прокрутки) показана. [atEnd] — видно кінець книги. */
    fun pageShown(p: Position, atEnd: Boolean) {
        located.value = located.value?.copy(pos = p)
        if (holdMark) holdMark = false else if (shown.value?.pos != p) searchMark.value = null
        shown.value = Shown(p, atEnd)
    }

    private suspend fun save(s: Shown) {
        val book = content.value ?: return
        val current = this.book.value ?: books.get(bookId) ?: return
        val status = if (s.atEnd || current.status == BookStatus.FINISHED) BookStatus.FINISHED else BookStatus.READING
        val progress = if (s.atEnd) 1f else book.fraction(s.pos)
        books.savePosition(bookId, s.pos.chapter, s.pos.offset, book.displayTitle(s.pos.chapter), progress, status, System.currentTimeMillis())
    }

    override fun onCleared() {
        // екран закрили до того, як спрацював debounce — дозберігаємо поза viewModelScope
        shown.value?.let { s -> appScope.launch { save(s) } }
    }

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        val current = settings.value ?: return
        viewModelScope.launch { prefs.setReader(transform(current)) }
    }

    fun setStatus(status: BookStatus) {
        viewModelScope.launch { books.setStatus(bookId, status) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            book.value?.let { repository.delete(it) }
            onDone()
        }
    }

    // ---------- Закладки ----------

    fun toggleBookmark(chapter: Int, start: Int, end: Int) {
        val book = content.value ?: return
        viewModelScope.launch {
            val existing = bookmarks.value.filter { it.chapter == chapter && it.offset in start until end.coerceAtLeast(start + 1) }
            if (existing.isNotEmpty()) {
                existing.forEach { bookmarkDao.delete(it) }
            } else {
                val text = book.chapters[chapter].text
                val snippet = text.substring(start.coerceIn(0, text.length), (start + 140).coerceAtMost(text.length)).trim()
                bookmarkDao.insert(
                    Bookmark(
                        bookId = bookId,
                        chapter = chapter,
                        offset = start,
                        chapterTitle = book.displayTitle(chapter),
                        snippet = snippet,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun deleteBookmark(b: Bookmark) {
        viewModelScope.launch { bookmarkDao.delete(b) }
    }

    // ---------- Виділення ----------

    suspend fun addHighlight(chapter: Int, start: Int, end: Int, color: Int, note: String = ""): Long {
        val book = content.value ?: return 0
        val text = book.chapters[chapter].text.substring(start, end)
        return highlightDao.insert(
            Highlight(
                bookId = bookId,
                chapter = chapter,
                start = start,
                end = end,
                text = text,
                chapterTitle = book.displayTitle(chapter),
                color = color,
                note = note,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    fun updateHighlight(h: Highlight, start: Int = h.start, end: Int = h.end, color: Int = h.color, note: String = h.note) {
        val book = content.value ?: return
        val text = book.chapters[h.chapter].text.substring(start, end)
        viewModelScope.launch { highlightDao.update(h.copy(start = start, end = end, text = text, color = color, note = note)) }
    }

    fun deleteHighlight(h: Highlight) {
        viewModelScope.launch { highlightDao.delete(h) }
    }
}
