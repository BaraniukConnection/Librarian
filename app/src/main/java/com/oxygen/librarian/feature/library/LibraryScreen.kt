package com.oxygen.librarian.feature.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oxygen.librarian.R
import com.oxygen.librarian.core.Shelf
import com.oxygen.librarian.core.minutesLeft
import com.oxygen.librarian.data.BookRepository
import com.oxygen.librarian.data.db.Book
import com.oxygen.librarian.data.db.BookStatus
import com.oxygen.librarian.data.prefs.SortOrder
import com.oxygen.librarian.ui.BookCover
import com.oxygen.librarian.ui.ButtonVariant
import com.oxygen.librarian.ui.ChipRow
import com.oxygen.librarian.ui.EmptyState
import com.oxygen.librarian.ui.OxButton
import com.oxygen.librarian.ui.OxCard
import com.oxygen.librarian.ui.OxConfirmDialog
import com.oxygen.librarian.ui.OxIcon
import com.oxygen.librarian.ui.OxIconButton
import com.oxygen.librarian.ui.OxListItem
import com.oxygen.librarian.ui.OxSheet
import com.oxygen.librarian.ui.ProgressBar
import com.oxygen.librarian.ui.SectionHeader
import com.oxygen.librarian.ui.formatDuration
import com.oxygen.librarian.ui.theme.LocalReduceMotion
import com.oxygen.librarian.ui.theme.Motion
import com.oxygen.librarian.ui.theme.Oxygen
import com.oxygen.librarian.ui.theme.OxygenType
import com.oxygen.librarian.ui.theme.Radius
import com.oxygen.librarian.ui.theme.Sizes
import com.oxygen.librarian.ui.theme.Space
import kotlinx.coroutines.launch

/** Типи файлів для вибору. FB2 системи часто не знають — тож і `octet-stream`. */
private val BOOK_TYPES = arrayOf(
    "application/epub+zip",
    "application/x-fictionbook+xml",
    "application/x-fictionbook",
    "text/fb2+xml",
    "application/zip",
    "text/plain",
    "application/octet-stream",
)

@Composable
fun LibraryScreen(
    incoming: Uri?,
    onIncomingHandled: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenHighlights: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var trayOpen by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Book?>(null) }
    var confirmDelete by remember { mutableStateOf<Book?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.import(uris)
    }
    val addBook = {
        trayOpen = false
        picker.launch(BOOK_TYPES)
    }

    LaunchedEffect(incoming) {
        if (incoming != null) {
            viewModel.import(listOf(incoming), open = true)
            onIncomingHandled()
        }
    }

    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val messageExisting = stringResource(R.string.import_existing)
    val messageFailed = stringResource(R.string.import_failed)
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { e ->
            when (e) {
                is LibraryEvent.Open -> onOpenBook(e.bookId)
                is LibraryEvent.Added -> scope.launch { snackbar.showSnackbar(resources.getQuantityString(R.plurals.import_added, e.count, e.count)) }
                LibraryEvent.AlreadyThere -> scope.launch { snackbar.showSnackbar(messageExisting) }
                is LibraryEvent.Failed -> scope.launch { snackbar.showSnackbar(messageFailed) }
            }
        }
    }

    BackHandler(enabled = searching || trayOpen) {
        if (searching) {
            searching = false
            viewModel.setQuery("")
        } else {
            trayOpen = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (searching) {
                SearchHeader(
                    query = state.query,
                    onQuery = viewModel::setQuery,
                    onClose = {
                        searching = false
                        viewModel.setQuery("")
                    },
                )
            } else {
                LibraryHeader(
                    open = trayOpen,
                    onToggle = { trayOpen = !trayOpen },
                    onSearch = {
                        trayOpen = false
                        searching = true
                    },
                    onHighlights = {
                        trayOpen = false
                        onOpenHighlights()
                    },
                    onAdd = addBook,
                    onSettings = {
                        trayOpen = false
                        onOpenSettings()
                    },
                )
            }
            if (importing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s4).height(2.dp),
                    color = Oxygen.colors.accentInk,
                    trackColor = Oxygen.colors.surfaceVariant,
                )
            }

            if (state.loaded && state.total == 0) {
                EmptyState(
                    title = stringResource(R.string.library_empty_title),
                    text = stringResource(R.string.library_empty_text),
                    action = { OxButton(stringResource(R.string.action_add_book), addBook) },
                )
            } else if (state.loaded) {
                ChipRow(
                    options = Shelf.entries,
                    selected = state.shelf,
                    label = { stringResource(shelfLabel(it)) },
                    onSelect = viewModel::setShelf,
                    edgePadding = Space.s4,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.s1, bottom = Space.s2),
                )
                Shelf(
                    state = state,
                    repository = viewModel.repository,
                    onOpen = onOpenBook,
                    onLongPress = { actionsFor = it },
                    onSort = viewModel::setSort,
                )
            }
        }

        SnackbarHost(
            snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(Space.s4),
        ) { data ->
            Snackbar(
                data,
                containerColor = Oxygen.colors.tool,
                contentColor = Oxygen.colors.toolIcon,
                shape = RoundedCornerShape(Radius.lg),
            )
        }
    }

    actionsFor?.let { book ->
        OxSheet(onDismiss = { actionsFor = null }, title = book.title) { close ->
            Column {
                if (book.status == BookStatus.FINISHED) {
                    OxListItem(
                        title = stringResource(R.string.book_mark_unread),
                        leading = { OxIcon(R.drawable.ic_auto_stories, tint = Oxygen.colors.accentInk, size = 22.dp) },
                        onClick = {
                            viewModel.setStatus(book, if (book.progress > 0f) BookStatus.READING else BookStatus.NEW)
                            close()
                        },
                    )
                } else {
                    OxListItem(
                        title = stringResource(R.string.book_mark_finished),
                        leading = { OxIcon(R.drawable.ic_check_circle, tint = Oxygen.colors.accentInk, size = 22.dp) },
                        onClick = {
                            viewModel.setStatus(book, BookStatus.FINISHED)
                            close()
                        },
                    )
                }
                OxListItem(
                    title = stringResource(R.string.book_delete),
                    leading = { OxIcon(R.drawable.ic_delete, tint = Oxygen.colors.dangerInk, size = 22.dp) },
                    onClick = {
                        confirmDelete = book
                        close()
                    },
                )
            }
        }
    }

    confirmDelete?.let { book ->
        OxConfirmDialog(
            title = stringResource(R.string.book_delete_title),
            text = stringResource(R.string.book_delete_text, book.title),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                viewModel.delete(book)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

/**
 * Заголовок бібліотеки. Нижньої навігації немає: пошук, виділення, «додати» і налаштування
 * сховані в меню під стрілкою праворуч. Анімації — librarian-design/motion/MOTION.md.
 */
@Composable
private fun LibraryHeader(
    open: Boolean,
    onToggle: () -> Unit,
    onSearch: () -> Unit,
    onHighlights: () -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val rotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = if (reduce) tween(0) else tween(if (open) 160 else 120, easing = if (open) Motion.standardOut else Motion.standardIn),
        label = "trayChevron",
    )
    // назва зникає одразу на відкритті й з'являється лише після того, як меню закрилось
    val titleAlpha by animateFloatAsState(
        targetValue = if (open) 0f else 1f,
        animationSpec = tween(120, delayMillis = if (open) 0 else 180, easing = LinearEasing),
        label = "trayTitle",
    )
    val actions = listOf<@Composable () -> Unit>(
        { OxIconButton(R.drawable.ic_search, stringResource(R.string.action_search), onSearch) },
        { OxIconButton(R.drawable.ic_ink_highlighter, stringResource(R.string.highlights), onHighlights) },
        { OxIconButton(R.drawable.ic_add, stringResource(R.string.action_add_book), onAdd, filled = true) },
        { OxIconButton(R.drawable.ic_settings, stringResource(R.string.settings), onSettings) },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = Space.s2)
            .heightIn(min = 64.dp)
            .padding(horizontal = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        Text(
            stringResource(R.string.library),
            style = OxygenType.screenTitle,
            color = Oxygen.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = Space.s2).alpha(titleAlpha),
        )
        val shift = with(LocalDensity.current) { 24.dp.roundToPx() }
        actions.forEachIndexed { i, action ->
            // першою з'являється найближча до стрілки, першою зникає найдальша
            val index = actions.lastIndex - i
            AnimatedVisibility(
                visible = open,
                enter = if (reduce) fadeIn(tween(Motion.REDUCED_MS)) else
                    fadeIn(tween(160, index * 30, Motion.standardOut)) +
                        slideInHorizontally(tween(160, index * 30, Motion.standardOut)) { shift } +
                        scaleIn(tween(160, index * 30, Motion.standardOut), initialScale = 0.85f),
                exit = if (reduce) fadeOut(tween(Motion.REDUCED_MS)) else
                    fadeOut(tween(120, (actions.lastIndex - index) * 20, Motion.standardIn)) +
                        slideOutHorizontally(tween(120, (actions.lastIndex - index) * 20, Motion.standardIn)) { shift } +
                        scaleOut(tween(120, (actions.lastIndex - index) * 20, Motion.standardIn), targetScale = 0.85f),
            ) { action() }
        }
        OxIconButton(
            R.drawable.ic_chevron_left,
            stringResource(if (open) R.string.action_hide_actions else R.string.action_show_actions),
            onToggle,
            modifier = Modifier.rotate(rotation),
        )
    }
}

@Composable
private fun SearchHeader(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val c = Oxygen.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = Space.s2)
            .heightIn(min = 64.dp)
            .padding(horizontal = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(Sizes.target)
                .background(c.surfaceVariant, CircleShape)
                .padding(horizontal = Space.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            OxIcon(R.drawable.ic_search, tint = c.inkMuted)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(stringResource(R.string.library_search_hint), style = OxygenType.bodyLg, color = c.inkMuted, maxLines = 1)
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = OxygenType.bodyLg.copy(color = c.ink),
                    cursorBrush = SolidColor(c.accentInk),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
        OxIconButton(R.drawable.ic_close, stringResource(R.string.action_close), onClose)
    }
}

@Composable
private fun Shelf(
    state: LibraryState,
    repository: BookRepository,
    onOpen: (Long) -> Unit,
    onLongPress: (Book) -> Unit,
    onSort: (SortOrder) -> Unit,
) {
    val nav = WindowInsets.navigationBars.asPaddingValues()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = PaddingValues(start = Space.s4, end = Space.s4, top = Space.s4, bottom = nav.calculateBottomPadding() + Space.s6),
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s4),
        modifier = Modifier.fillMaxSize(),
    ) {
        state.reading?.let { book ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "reading") {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    SectionHeader(stringResource(R.string.library_reading_now))
                    ReadingCard(book, repository, onClick = { onOpen(book.id) }, onLongClick = { onLongPress(book) })
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            SectionHeader(
                stringResource(R.string.library_all_books, state.books.size),
                modifier = Modifier.padding(top = if (state.reading != null) Space.s2 else 0.dp),
            ) { SortButton(state.sort, onSort) }
        }
        if (state.books.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "nothing") {
                Text(
                    stringResource(if (state.query.isNotBlank()) R.string.library_nothing_found else R.string.library_shelf_empty),
                    style = OxygenType.body,
                    color = Oxygen.colors.inkMuted,
                    modifier = Modifier.padding(vertical = Space.s6),
                )
            }
        }
        items(state.books, key = { it.id }) { book ->
            BookTile(book, repository, onClick = { onOpen(book.id) }, onLongClick = { onLongPress(book) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookTile(book: Book, repository: BookRepository, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        BookCover(book, repository, Modifier.fillMaxWidth().height(164.dp))
        Text(bookState(book), style = OxygenType.caption, color = Oxygen.colors.inkMuted, maxLines = 1)
    }
}

@Composable
private fun bookState(book: Book): String = when (book.status) {
    BookStatus.NEW -> stringResource(R.string.book_new)
    BookStatus.FINISHED -> stringResource(R.string.book_finished)
    BookStatus.READING -> stringResource(R.string.percent, (book.progress * 100).toInt())
}

@Composable
private fun ReadingCard(book: Book, repository: BookRepository, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = Oxygen.colors
    OxCard(Modifier.fillMaxWidth(), onClick = onClick, onLongClick = onLongClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
            BookCover(book, repository, Modifier.size(width = 84.dp, height = 126.dp))
            Column(Modifier.weight(1f).height(126.dp).padding(vertical = 2.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(book.title, style = OxygenType.title, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (book.author.isNotBlank()) Text(book.author, style = OxygenType.body, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        val percent = stringResource(R.string.percent, (book.progress * 100).toInt())
                        Text(
                            if (book.chapterTitle.isNotBlank()) "${book.chapterTitle} · $percent" else percent,
                            style = OxygenType.caption,
                            color = c.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatDuration(book.minutesLeft()), style = OxygenType.caption, color = c.inkMuted, maxLines = 1)
                    }
                    ProgressBar(book.progress)
                }
            }
        }
    }
}

@Composable
private fun SortButton(sort: SortOrder, onSort: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OxButton(
            stringResource(sortLabel(sort)),
            onClick = { open = true },
            variant = ButtonVariant.Quiet,
            icon = R.drawable.ic_swap_vert,
            compact = true,
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Oxygen.colors.surface,
            shape = RoundedCornerShape(Radius.md),
        ) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(sortLabel(order)),
                            style = OxygenType.body,
                            color = if (order == sort) Oxygen.colors.accentInk else Oxygen.colors.ink,
                        )
                    },
                    onClick = {
                        onSort(order)
                        open = false
                    },
                )
            }
        }
    }
    Spacer(Modifier.width(0.dp))
}

private fun shelfLabel(s: Shelf) = when (s) {
    Shelf.ALL -> R.string.shelf_all
    Shelf.READING -> R.string.shelf_reading
    Shelf.TO_READ -> R.string.shelf_to_read
    Shelf.FINISHED -> R.string.shelf_finished
}

private fun sortLabel(s: SortOrder) = when (s) {
    SortOrder.RECENT -> R.string.sort_recent
    SortOrder.TITLE -> R.string.sort_title
    SortOrder.AUTHOR -> R.string.sort_author
}
