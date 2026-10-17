package com.oxygen.librarian.feature.reader

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oxygen.librarian.R
import com.oxygen.librarian.book.ParsedBook
import com.oxygen.librarian.core.Position
import com.oxygen.librarian.core.fraction
import com.oxygen.librarian.core.globalOffset
import com.oxygen.librarian.core.minutesFor
import com.oxygen.librarian.core.positionAt
import com.oxygen.librarian.core.sentenceAt
import com.oxygen.librarian.core.wordBoundary
import com.oxygen.librarian.data.db.BookStatus
import com.oxygen.librarian.data.db.Highlight
import com.oxygen.librarian.data.prefs.PageTheme
import com.oxygen.librarian.data.prefs.Paging
import com.oxygen.librarian.data.prefs.ReaderSettings
import com.oxygen.librarian.ui.EmptyState
import com.oxygen.librarian.ui.OxConfirmDialog
import com.oxygen.librarian.ui.OxIconButton
import com.oxygen.librarian.ui.OxSwatches
import com.oxygen.librarian.ui.OxToolbar
import com.oxygen.librarian.ui.OxTopBar
import com.oxygen.librarian.ui.Tool
import com.oxygen.librarian.ui.theme.Elevation
import com.oxygen.librarian.ui.theme.LocalReduceMotion
import com.oxygen.librarian.ui.theme.Mark
import com.oxygen.librarian.ui.theme.Motion
import com.oxygen.librarian.ui.theme.Oxygen
import com.oxygen.librarian.ui.theme.OxygenTheme
import com.oxygen.librarian.ui.theme.OxygenType
import com.oxygen.librarian.ui.theme.Radius
import com.oxygen.librarian.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Те, що зараз на екрані: розділ і межі сторінки (для закладки, номера сторінки й часу до кінця розділу). */
private data class Visible(val pages: ChapterPages, val index: Int) {
    val page get() = pages.pages[index]
}

@Composable
fun ReaderScreen(onBack: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val located by viewModel.located.collectAsStateWithLifecycle()

    val appDark = Oxygen.colors.isDark
    val s = settings
    val pageDark = when (s?.page) {
        PageTheme.DARK -> true
        PageTheme.LIGHT -> false
        else -> appDark
    }

    OxygenTheme(darkTheme = pageDark) {
        Box(Modifier.fillMaxSize().background(Oxygen.colors.bg)) {
            val book = content
            val at = located
            when {
                failed -> EmptyState(
                    stringResource(R.string.reader_failed_title),
                    stringResource(R.string.reader_failed_text),
                    Modifier.align(Alignment.Center),
                )
                book == null || s == null || at == null -> CircularProgressIndicator(
                    color = Oxygen.colors.accentInk,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> Reader(book, s, at, pageDark, appDark, viewModel, onBack)
            }
        }
    }
}

@Composable
private fun Reader(
    book: ParsedBook,
    settings: ReaderSettings,
    located: Located,
    pageDark: Boolean,
    appDark: Boolean,
    vm: ReaderViewModel,
    onBack: () -> Unit,
) {
    val c = Oxygen.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    val entity by vm.book.collectAsStateWithLifecycle()
    val highlights by vm.highlights.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val searchMark by vm.searchMark.collectAsStateWithLifecycle()
    val pageAnimation by vm.pageAnimation.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val locale = remember(book) { bookLocale(book) }

    var controls by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<ReaderSheet?>(null) }
    var selection by remember { mutableStateOf<Selection?>(null) }
    var noteFor by remember { mutableStateOf<Selection?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var seekPreview by remember { mutableStateOf<Float?>(null) }
    var fresh by remember { mutableStateOf<Long?>(null) }
    val freshAlpha = remember { Animatable(Mark.HIGHLIGHT_ALPHA) }

    // керування ховається саме через 5 с бездіяльності
    LaunchedEffect(controls, touches, sheet) {
        if (controls && sheet == null) {
            delay(5_000)
            controls = false
        }
    }

    // читання без хрому: системні панелі сховані, поки не покликали керування
    val activity = LocalActivity.current
    DisposableEffect(controls, pageDark) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                isAppearanceLightStatusBars = !pageDark
                isAppearanceLightNavigationBars = !pageDark
                if (controls) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {}
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    show(WindowInsetsCompat.Type.systemBars())
                    isAppearanceLightStatusBars = !appDark
                    isAppearanceLightNavigationBars = !appDark
                }
            }
        }
    }

    BackHandler(enabled = selection != null) { selection = null }

    fun highlightAt(ch: Int, off: Int): Highlight? = highlights.lastOrNull { it.chapter == ch && off >= it.start && off < it.end }

    val onTapOffset: (Int, Int) -> Boolean = { ch, off ->
        highlightAt(ch, off)?.let { h ->
            controls = false
            selection = Selection(ch, h.start, h.end, h.id)
            true
        } ?: false
    }
    val onLongPress: (Int, Int) -> Unit = { ch, off ->
        controls = false
        val h = highlightAt(ch, off)
        selection = if (h != null) {
            Selection(ch, h.start, h.end, h.id)
        } else {
            book.chapters[ch].sentenceAt(off, locale)?.let { Selection(ch, it.first, it.last + 1) }
        }
    }
    val onMoveHandle: (Boolean, Int) -> Unit = { isStart, off ->
        selection?.let { sel ->
            val chapter = book.chapters[sel.chapter]
            selection = if (isStart) {
                val start = chapter.wordBoundary(off, locale, forEnd = false)
                if (start < sel.end) sel.copy(start = start) else sel
            } else {
                val end = chapter.wordBoundary(off, locale, forEnd = true)
                if (end > sel.start) sel.copy(end = end) else sel
            }
        }
    }

    val selectionColor = c.accent.copy(alpha = 0.3f)
    val searchColor = c.accentInk.copy(alpha = 0.3f)
    val decosFor: (Int) -> List<Deco> = { ch ->
        buildList {
            highlights.forEach { h ->
                if (h.chapter == ch) {
                    val alpha = if (h.id == fresh) freshAlpha.value else Mark.HIGHLIGHT_ALPHA
                    add(Deco(h.start, h.end, Mark.colors[h.color.mod(8)].copy(alpha = alpha)))
                }
            }
            searchMark?.takeIf { it.chapter == ch }?.let { add(Deco(it.start, it.end, searchColor)) }
            selection?.takeIf { it.chapter == ch }?.let { add(Deco(it.start, it.end, selectionColor)) }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cutout = WindowInsets.displayCutout
        val cutTop = cutout.getTop(density).toFloat()
        val cutBottom = cutout.getBottom(density).toFloat()
        val area = with(density) {
            val side = 28.dp.toPx()
            val width = min(constraints.maxWidth - 2 * side, Space.measure.toPx()).roundToInt()
            val top = cutTop + 52.dp.toPx()
            val bottom = cutBottom + 56.dp.toPx()
            TextArea((constraints.maxWidth - width) / 2f, top, width, (constraints.maxHeight - top - bottom).roundToInt())
        }
        val fontResolver = LocalFontFamilyResolver.current
        val layoutDirection = LocalLayoutDirection.current
        val measurer = remember(fontResolver, density, layoutDirection) { TextMeasurer(fontResolver, density, layoutDirection, 0) }
        val hyphen = with(density) { (settings.fontSize * 0.33f).sp.toPx() }
        val spec = PageSpec(area.width, area.height, settings.copy(page = PageTheme.APP, paging = Paging.PAGES), locale, hyphen)
        val paginator = remember(book, measurer, spec) { Paginator(book, measurer, spec) }
        DisposableEffect(paginator) { onDispose { paginator.close() } }

        var visible by remember { mutableStateOf<Visible?>(null) }

        if (settings.paging == Paging.PAGES) {
            val pager = remember(paginator) { PagerState(paginator, vm.located.value?.pos ?: located.pos) }
            LaunchedEffect(pager, located.jump) { pager.pos = vm.located.value?.pos ?: located.pos }
            LaunchedEffect(pager, pager.pos.chapter) { pager.load() }
            val cur = pager.current
            val index = pager.index
            LaunchedEffect(cur, index) {
                if (cur == null) return@LaunchedEffect
                visible = Visible(cur, index)
                val page = cur.pages[index]
                val atEnd = cur.chapter == book.chapters.lastIndex && index == cur.pages.lastIndex
                vm.pageShown(Position(cur.chapter, page.start), atEnd)
            }
            PagedReader(
                state = pager,
                area = area,
                animate = pageAnimation && !reduceMotion,
                ink = c.ink,
                accent = c.accent,
                selection = selection,
                decosFor = decosFor,
                onCenterTap = { controls = !controls },
                onTapOffset = onTapOffset,
                onLongPress = onLongPress,
                onMoveHandle = onMoveHandle,
                onClearSelection = { selection = null },
            )
        } else {
            ScrollReader(
                paginator = paginator,
                located = located,
                area = area.copy(top = cutTop + with(density) { 16.dp.toPx() }),
                bottomPadding = with(density) { WindowInsets.navigationBars.getBottom(this) + 96.dp.toPx() },
                ink = c.ink,
                decosFor = decosFor,
                previousLabel = located.pos.chapter.takeIf { it > 0 }?.let { stringResource(R.string.reader_previous, book.displayTitle(it - 1)) },
                nextLabel = located.pos.chapter.takeIf { it < book.chapters.lastIndex }?.let { stringResource(R.string.reader_next, book.displayTitle(it + 1)) },
                onGo = { vm.go(it) },
                onShown = { p, atEnd ->
                    paginator.peek(p.chapter)?.let { pages -> visible = Visible(pages, pages.pageOf(p.offset)) }
                    vm.pageShown(p, atEnd)
                },
                onCenterTap = {
                    if (selection != null) selection = null else controls = !controls
                },
                onTapOffset = { ch, off -> if (selection != null) { selection = null; true } else onTapOffset(ch, off) },
                onLongPress = onLongPress,
            )
        }

        val v = visible
        val pos = located.pos
        // ---------- верх і низ сторінки ----------
        if (settings.paging == Paging.PAGES && v != null) {
            Text(
                book.displayTitle(v.pages.chapter),
                style = OxygenType.caption,
                color = c.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = with(density) { cutTop.toDp() } + 16.dp, start = 48.dp, end = 48.dp),
            )
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = with(density) { cutBottom.toDp() } + 20.dp)
                    .padding(horizontal = with(density) { area.left.toDp() }),
            ) {
                Text(
                    stringResource(R.string.percent, (book.fraction(Position(v.pages.chapter, v.page.end)) * 100).toInt()),
                    style = OxygenType.caption,
                    color = c.inkMuted,
                    modifier = Modifier.weight(1f),
                )
                val left = minutesFor((v.pages.length - v.page.end).toLong())
                Text(
                    if (v.index == v.pages.pages.lastIndex) stringResource(R.string.reader_chapter_end) else stringResource(R.string.reader_minutes_left, max(left, 1)),
                    style = OxygenType.caption,
                    color = c.inkMuted,
                )
            }
        }

        // ---------- керування ----------
        val topEnter: EnterTransition
        val topExit: ExitTransition
        val bottomEnter: EnterTransition
        val bottomExit: ExitTransition
        if (reduceMotion) {
            topEnter = fadeIn(tween(Motion.REDUCED_MS)); bottomEnter = topEnter
            topExit = fadeOut(tween(Motion.REDUCED_MS)); bottomExit = topExit
        } else {
            val up = with(density) { 16.dp.roundToPx() }
            val down = with(density) { 24.dp.roundToPx() }
            topEnter = slideInVertically(tween(200, easing = Motion.standardOut)) { -up } + fadeIn(tween(200, easing = Motion.standardOut))
            topExit = slideOutVertically(tween(160, easing = Motion.standardIn)) { -up } + fadeOut(tween(160, easing = Motion.standardIn))
            bottomEnter = slideInVertically(tween(200, easing = Motion.standardOut)) { down } + fadeIn(tween(200, easing = Motion.standardOut))
            bottomExit = slideOutVertically(tween(160, easing = Motion.standardIn)) { down } + fadeOut(tween(160, easing = Motion.standardIn))
        }
        val keepAlive = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    touches++
                }
            }
        }
        val pageStart = v?.page?.start ?: pos.offset
        val pageEnd = v?.page?.end ?: pos.offset
        val bookmarked = bookmarks.any { it.chapter == pos.chapter && it.offset >= pageStart && it.offset < max(pageEnd, pageStart + 1) }

        AnimatedVisibility(controls, enter = topEnter, exit = topExit, modifier = keepAlive) {
            Box(Modifier.fillMaxWidth().shadow(Elevation.card).background(c.bg)) {
                OxTopBar(entity?.title ?: book.meta.title, onBack) {
                    OxIconButton(
                        if (bookmarked) R.drawable.ic_bookmark_added else R.drawable.ic_bookmark_add,
                        stringResource(if (bookmarked) R.string.reader_bookmark_remove else R.string.reader_bookmark_add),
                        { vm.toggleBookmark(pos.chapter, pageStart, pageEnd) },
                    )
                    Box {
                        OxIconButton(R.drawable.ic_more_vert, stringResource(R.string.action_more), { menuOpen = true })
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            containerColor = c.surface,
                            shape = RoundedCornerShape(Radius.md),
                        ) {
                            val finished = entity?.status == BookStatus.FINISHED
                            DropdownMenuItem(
                                text = { Text(stringResource(if (finished) R.string.book_mark_unread else R.string.book_mark_finished), style = OxygenType.body, color = c.ink) },
                                onClick = {
                                    menuOpen = false
                                    vm.setStatus(if (finished) BookStatus.READING else BookStatus.FINISHED)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.book_delete), style = OxygenType.body, color = c.dangerInk) },
                                onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            controls,
            enter = bottomEnter,
            exit = bottomExit,
            modifier = keepAlive.align(Alignment.BottomCenter),
        ) {
            val fraction = book.fraction(pos)
            val preview = seekPreview
            val shownChapter = preview?.let { book.positionAt(it).chapter } ?: pos.chapter
            // відсоток — за кінцем видимої сторінки, як і внизу сторінки; повзунок — за її початком
            val shownFraction = preview ?: v?.let { book.fraction(Position(it.pages.chapter, it.page.end)) } ?: fraction
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = Space.s4, end = Space.s4, bottom = Space.s6)
                    .widthIn(max = Space.measure)
                    .fillMaxWidth()
                    .shadow(Elevation.floating, RoundedCornerShape(Radius.xl))
                    .background(c.surface, RoundedCornerShape(Radius.xl))
                    .padding(Space.s4),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.padding(horizontal = Space.s1), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    Text(
                        book.displayTitle(shownChapter),
                        style = OxygenType.title,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val percent = (shownFraction * 100).toInt()
                    val pages = v?.pages
                    val label = if (pages != null && pages.pages.isNotEmpty()) {
                        val perPage = max(1f, pages.length.toFloat() / pages.pages.size)
                        val total = max(1, ceil(book.totalChars / perPage).toInt())
                        val global = if (preview != null) preview * book.totalChars else book.globalOffset(pos).toFloat()
                        val current = (global / perPage).toInt().plus(1).coerceIn(1, total)
                        stringResource(R.string.reader_page_of, current, total, percent)
                    } else {
                        stringResource(R.string.percent, percent)
                    }
                    Text(label, style = OxygenType.caption, color = c.inkMuted, maxLines = 1)
                }
                ProgressSlider(
                    fraction = fraction,
                    onPreview = { seekPreview = it },
                    onSeek = { f -> vm.go(book.positionAt(f)) },
                )
                OxToolbar(
                    groups = listOf(
                        listOf(
                            Tool(R.drawable.ic_toc, stringResource(R.string.reader_toc)) { controls = false; sheet = ReaderSheet.TOC },
                            Tool(R.drawable.ic_bookmarks, stringResource(R.string.reader_bookmarks)) { controls = false; sheet = ReaderSheet.BOOKMARKS },
                            Tool(R.drawable.ic_ink_highlighter, stringResource(R.string.highlights)) { controls = false; sheet = ReaderSheet.HIGHLIGHTS },
                            Tool(R.drawable.ic_search, stringResource(R.string.reader_search)) { controls = false; sheet = ReaderSheet.SEARCH },
                        ),
                        listOf(
                            Tool(R.drawable.ic_contrast, stringResource(R.string.reader_theme)) {
                                vm.updateSettings { it.copy(page = if (pageDark) PageTheme.LIGHT else PageTheme.DARK) }
                            },
                            Tool(R.drawable.ic_format_size, stringResource(R.string.reader_text)) { controls = false; sheet = ReaderSheet.TEXT },
                        ),
                    ),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }

        // ---------- меню виділення ----------
        var menuHeight by remember { mutableIntStateOf(0) }
        val sel = selection
        val menuTop: Float? = sel?.let {
            val vis = v?.takeIf { p -> p.pages.chapter == it.chapter }
            if (settings.paging == Paging.PAGES && vis != null) {
                val pages = vis.pages
                val page = vis.page
                val gap = with(density) { 28.dp.toPx() }
                val top = pages.layout.getBoundingBox(max(it.start, page.start).coerceAtMost(page.end - 1).coerceAtLeast(0)).top - page.top + area.top
                val bottom = pages.layout.getBoundingBox((min(it.end, page.end) - 1).coerceAtLeast(0)).bottom - page.top + area.top
                val below = bottom + gap
                if (below + menuHeight < constraints.maxHeight - gap) below else max(cutTop + gap, top - gap - menuHeight)
            } else {
                null
            }
        }
        AnimatedVisibility(
            visible = sel != null,
            enter = if (reduceMotion) fadeIn(tween(Motion.REDUCED_MS)) else scaleIn(tween(160, easing = Motion.standardOut), initialScale = 0.92f) + fadeIn(tween(160, easing = Motion.standardOut)),
            exit = if (reduceMotion) fadeOut(tween(Motion.REDUCED_MS)) else scaleOut(tween(120, easing = Motion.standardIn), targetScale = 0.92f) + fadeOut(tween(120, easing = Motion.standardIn)),
            modifier = if (menuTop != null) {
                Modifier.offset { IntOffset(0, menuTop.roundToInt()) }
            } else {
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = Space.s6)
            }.fillMaxWidth().onSizeChanged { menuHeight = it.height },
        ) {
            val last = remember { mutableStateOf(sel) }
            if (sel != null) last.value = sel
            val current = last.value ?: return@AnimatedVisibility
            val existing = current.highlightId?.let { id -> highlights.firstOrNull { it.id == id } }
            val quote = book.chapters[current.chapter].text.substring(current.start, current.end)
            val chooser = stringResource(R.string.action_open_with)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                OxSwatches(existing?.color, onSelect = { color ->
                    selection = null
                    if (existing != null) {
                        vm.updateHighlight(existing, current.start, current.end, color = color)
                    } else {
                        scope.launch {
                            val id = vm.addHighlight(current.chapter, current.start, current.end, color)
                            freshAlpha.snapTo(0f)
                            fresh = id
                            freshAlpha.animateTo(Mark.HIGHLIGHT_ALPHA, tween(200, easing = LinearEasing))
                        }
                    }
                })
                OxToolbar(
                    listOf(
                        listOf(
                            Tool(R.drawable.ic_edit_note, stringResource(R.string.reader_note)) { noteFor = current; selection = null },
                            Tool(R.drawable.ic_translate, stringResource(R.string.reader_translate)) { translate(context, quote, chooser) },
                            Tool(R.drawable.ic_dictionary, stringResource(R.string.reader_dictionary)) { define(context, quote, chooser) },
                        ),
                        listOfNotNull(
                            Tool(R.drawable.ic_content_copy, stringResource(R.string.reader_copy)) { copy(context, quote); selection = null },
                            Tool(R.drawable.ic_search, stringResource(R.string.reader_find)) {
                                vm.query.value = quote.take(60).trim()
                                selection = null
                                sheet = ReaderSheet.SEARCH
                            },
                            existing?.let { h -> Tool(R.drawable.ic_delete, stringResource(R.string.action_delete)) { vm.deleteHighlight(h); selection = null } },
                        ),
                    ),
                )
            }
        }
    }

    // ---------- шторки й діалоги ----------
    when (sheet) {
        ReaderSheet.TEXT -> TextSheet(settings, vm::updateSettings) { sheet = null }
        ReaderSheet.TOC -> TocSheet(book, located.pos.chapter, onGo = { vm.go(Position(it, 0)) }) { sheet = null }
        ReaderSheet.BOOKMARKS -> BookmarksSheet(bookmarks, onGo = { vm.go(Position(it.chapter, it.offset)) }, onDelete = vm::deleteBookmark) { sheet = null }
        ReaderSheet.HIGHLIGHTS -> BookHighlightsSheet(highlights, onGo = { vm.go(Position(it.chapter, it.start)) }) { sheet = null }
        ReaderSheet.SEARCH -> SearchSheet(
            query = query,
            onQuery = { vm.query.value = it },
            results = results,
            book = book,
            onGo = { hit -> vm.go(Position(hit.chapter, hit.start), SearchMark(hit.chapter, hit.start, hit.end)) },
        ) { sheet = null }
        null -> Unit
    }

    noteFor?.let { target ->
        val existing = target.highlightId?.let { id -> highlights.firstOrNull { it.id == id } }
        NoteDialog(
            initial = existing?.note.orEmpty(),
            quote = book.chapters[target.chapter].text.substring(target.start, target.end),
            onSave = { note ->
                noteFor = null
                if (existing != null) {
                    vm.updateHighlight(existing, target.start, target.end, note = note)
                } else {
                    scope.launch { vm.addHighlight(target.chapter, target.start, target.end, Mark.DEFAULT_HIGHLIGHT, note) }
                }
            },
            onDismiss = { noteFor = null },
        )
    }

    if (confirmDelete) {
        OxConfirmDialog(
            title = stringResource(R.string.book_delete_title),
            text = stringResource(R.string.book_delete_text, entity?.title ?: book.meta.title),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                vm.delete(onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
