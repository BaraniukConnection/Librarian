package com.oxygen.librarian.feature.highlights

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.oxygen.librarian.R
import com.oxygen.librarian.data.BookRepository
import com.oxygen.librarian.data.db.Book
import com.oxygen.librarian.data.db.BookDao
import com.oxygen.librarian.data.db.Highlight
import com.oxygen.librarian.data.db.HighlightDao
import com.oxygen.librarian.ui.BookCover
import com.oxygen.librarian.ui.ChipRow
import com.oxygen.librarian.ui.EmptyState
import com.oxygen.librarian.ui.OxCard
import com.oxygen.librarian.ui.OxConfirmDialog
import com.oxygen.librarian.ui.OxIcon
import com.oxygen.librarian.ui.OxIconButton
import com.oxygen.librarian.ui.OxTopBar
import com.oxygen.librarian.ui.formatDay
import com.oxygen.librarian.ui.theme.Mark
import com.oxygen.librarian.ui.theme.Oxygen
import com.oxygen.librarian.ui.theme.OxygenType
import com.oxygen.librarian.ui.theme.QuoteStyle
import com.oxygen.librarian.ui.theme.Radius
import com.oxygen.librarian.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Фільтр над списком: усі, лише з нотатками, або одна книга. */
sealed interface HighlightFilter {
    data object All : HighlightFilter
    data object WithNotes : HighlightFilter
    data class OfBook(val book: Book) : HighlightFilter
}

data class BookHighlights(val book: Book, val items: List<Highlight>)

data class HighlightsState(
    val loaded: Boolean = false,
    val filters: List<HighlightFilter> = listOf(HighlightFilter.All, HighlightFilter.WithNotes),
    val filter: HighlightFilter = HighlightFilter.All,
    val groups: List<BookHighlights> = emptyList(),
)

@HiltViewModel
class HighlightsViewModel @Inject constructor(
    books: BookDao,
    private val highlights: HighlightDao,
    val repository: BookRepository,
) : ViewModel() {
    private val filter = MutableStateFlow<HighlightFilter>(HighlightFilter.All)

    val state: StateFlow<HighlightsState> = combine(highlights.observeAll(), books.observeAll(), filter) { all, shelf, f ->
        val byBook = all.groupBy { it.bookId }
        // книги — у порядку останнього читання: свіже зверху
        val withHighlights = shelf.filter { it.id in byBook }.sortedByDescending { maxOf(it.openedAt, it.addedAt) }
        val active = if (f is HighlightFilter.OfBook && withHighlights.none { it.id == f.book.id }) HighlightFilter.All else f
        val groups = withHighlights.mapNotNull { book ->
            val items = byBook.getValue(book.id).filter { h ->
                when (active) {
                    HighlightFilter.All -> true
                    HighlightFilter.WithNotes -> h.note.isNotBlank()
                    is HighlightFilter.OfBook -> h.bookId == active.book.id
                }
            }
            items.takeIf { it.isNotEmpty() }?.let { BookHighlights(book, it) }
        }
        HighlightsState(
            loaded = true,
            filters = listOf(HighlightFilter.All, HighlightFilter.WithNotes) + withHighlights.map { HighlightFilter.OfBook(it) },
            filter = active,
            groups = groups,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HighlightsState())

    fun setFilter(f: HighlightFilter) { filter.value = f }

    fun delete(h: Highlight) {
        viewModelScope.launch { highlights.delete(h) }
    }
}

@Composable
fun HighlightsScreen(
    onBack: () -> Unit,
    onOpen: (Highlight) -> Unit,
    viewModel: HighlightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf<Highlight?>(null) }

    Column(Modifier.fillMaxSize()) {
        OxTopBar(stringResource(R.string.highlights), onBack) {
            if (state.groups.isNotEmpty()) {
                val exportLabel = stringResource(R.string.action_export)
                OxIconButton(R.drawable.ic_ios_share, exportLabel, { export(context, state.groups, exportLabel) })
            }
        }
        if (state.loaded && state.filters.size <= 2 && state.groups.isEmpty()) {
            EmptyState(stringResource(R.string.highlights_empty_title), stringResource(R.string.highlights_empty_text))
            return
        }
        ChipRow(
            options = state.filters,
            selected = state.filter,
            label = { f ->
                when (f) {
                    HighlightFilter.All -> stringResource(R.string.shelf_all)
                    HighlightFilter.WithNotes -> stringResource(R.string.highlights_with_notes)
                    is HighlightFilter.OfBook -> f.book.title
                }
            },
            icon = { f -> if (f == HighlightFilter.WithNotes) R.drawable.ic_edit_note else null },
            onSelect = viewModel::setFilter,
            edgePadding = Space.s4,
            modifier = Modifier.fillMaxWidth().padding(top = Space.s1, bottom = Space.s2),
        )
        val nav = WindowInsets.navigationBars.asPaddingValues()
        LazyColumn(
            contentPadding = PaddingValues(start = Space.s4, end = Space.s4, top = Space.s4, bottom = nav.calculateBottomPadding() + Space.s6),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            state.groups.forEachIndexed { gi, group ->
                item(key = "book-${group.book.id}") {
                    BookHeader(group, viewModel.repository, Modifier.padding(top = if (gi > 0) Space.s3 else 0.dp))
                }
                items(group.items, key = { it.id }) { h ->
                    HighlightCard(h, onClick = { onOpen(h) }, onLongClick = { confirmDelete = h })
                }
            }
            if (state.groups.isEmpty()) {
                item {
                    Text(stringResource(R.string.highlights_filter_empty), style = OxygenType.body, color = Oxygen.colors.inkMuted)
                }
            }
        }
    }

    confirmDelete?.let { h ->
        OxConfirmDialog(
            title = stringResource(R.string.highlight_delete_title),
            text = h.text.take(160),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                viewModel.delete(h)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun BookHeader(group: BookHighlights, repository: BookRepository, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
        BookCover(group.book, repository, Modifier.size(width = 28.dp, height = 42.dp), compact = true)
        Column {
            Text(group.book.title, style = OxygenType.title, color = Oxygen.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val count = pluralStringResource(R.plurals.highlights_count, group.items.size, group.items.size)
            Text(
                listOf(group.book.author, count).filter { it.isNotBlank() }.joinToString(" · "),
                style = OxygenType.caption,
                color = Oxygen.colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HighlightCard(h: Highlight, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = Oxygen.colors
    OxCard(Modifier.fillMaxWidth(), onClick = onClick, onLongClick = onLongClick) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.padding(top = 6.dp).size(10.dp).background(Mark.colors[h.color.mod(8)], CircleShape))
                Text(h.text, style = QuoteStyle, color = c.ink)
            }
            if (h.note.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().background(c.surfaceVariant, RoundedCornerShape(Radius.sm)).padding(horizontal = Space.s3, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.s2),
                ) {
                    OxIcon(R.drawable.ic_edit_note, size = 18.dp, tint = c.ink)
                    Text(h.note, style = OxygenType.body, color = c.ink)
                }
            }
        }
        Text(
            listOf(h.chapterTitle, formatDay(h.createdAt)).filter { it.isNotBlank() }.joinToString(" · "),
            style = OxygenType.caption,
            color = c.inkMuted,
            modifier = Modifier.padding(top = Space.s3),
        )
    }
}

/** Експорт — звичайним текстом у Markdown через «Поділитися»: забрати свої виділення можна будь-куди. */
private fun export(context: Context, groups: List<BookHighlights>, chooserTitle: String) {
    val text = buildString {
        groups.forEach { g ->
            append("# ").append(g.book.title)
            if (g.book.author.isNotBlank()) append(" — ").append(g.book.author)
            append("\n\n")
            g.items.forEach { h ->
                append("> ").append(h.text).append("\n")
                if (h.chapterTitle.isNotBlank()) append(">\n> — ").append(h.chapterTitle).append("\n")
                if (h.note.isNotBlank()) append("\n").append(h.note).append("\n")
                append("\n")
            }
        }
    }.trimEnd()
    val send = Intent(Intent.ACTION_SEND).apply {
        // text/plain, а не text/markdown: markdown приймає мало застосунків, а текст лишається Markdown-ом
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, groups.singleOrNull()?.book?.title ?: "Librarian")
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}
