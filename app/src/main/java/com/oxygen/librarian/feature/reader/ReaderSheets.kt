package com.oxygen.librarian.feature.reader

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.oxygen.librarian.R
import com.oxygen.librarian.book.ParsedBook
import com.oxygen.librarian.core.SearchHit
import com.oxygen.librarian.data.db.Bookmark
import com.oxygen.librarian.data.db.Highlight
import com.oxygen.librarian.data.prefs.LineSpacing
import com.oxygen.librarian.data.prefs.PageTheme
import com.oxygen.librarian.data.prefs.Paging
import com.oxygen.librarian.data.prefs.ReaderFont
import com.oxygen.librarian.data.prefs.ReaderSettings
import com.oxygen.librarian.ui.ButtonVariant
import com.oxygen.librarian.ui.OxButton
import com.oxygen.librarian.ui.OxChip
import com.oxygen.librarian.ui.OxIcon
import com.oxygen.librarian.ui.OxIconButton
import com.oxygen.librarian.ui.OxListItem
import com.oxygen.librarian.ui.OxSheet
import com.oxygen.librarian.ui.OxSwitchRow
import com.oxygen.librarian.ui.SheetGroup
import com.oxygen.librarian.ui.theme.Elevation
import com.oxygen.librarian.ui.theme.Mark
import com.oxygen.librarian.ui.theme.Oxygen
import com.oxygen.librarian.ui.theme.OxygenType
import com.oxygen.librarian.ui.theme.QuoteStyle
import com.oxygen.librarian.ui.theme.Radius
import com.oxygen.librarian.ui.theme.Sizes
import com.oxygen.librarian.ui.theme.Space
import kotlin.math.roundToInt

enum class ReaderSheet { TOC, BOOKMARKS, HIGHLIGHTS, SEARCH, TEXT }

private val ListHeight = 480.dp

// ---------- Текст ----------

/** Шторка «Текст»: розмір, шрифт, інтервал, тема сторінки, гортання, переноси. Усе діє одразу. */
@Composable
fun TextSheet(settings: ReaderSettings, onChange: ((ReaderSettings) -> ReaderSettings) -> Unit, onDismiss: () -> Unit) {
    OxSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.reader_text),
        titleTrailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                OxIconButton(R.drawable.ic_text_decrease, stringResource(R.string.reader_smaller), {
                    onChange { it.copy(fontSize = (it.fontSize - 1).coerceAtLeast(ReaderSettings.MIN_SIZE)) }
                })
                Text(
                    settings.fontSize.toString(),
                    style = OxygenType.title,
                    color = Oxygen.colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 28.dp),
                )
                OxIconButton(R.drawable.ic_text_increase, stringResource(R.string.reader_bigger), {
                    onChange { it.copy(fontSize = (it.fontSize + 1).coerceAtMost(ReaderSettings.MAX_SIZE)) }
                })
            }
        },
    ) {
        SheetGroup(stringResource(R.string.reader_font)) {
            WrapChips(ReaderFont.entries, settings.font, { fontLabel(it) }) { f -> onChange { it.copy(font = f) } }
        }
        SheetGroup(stringResource(R.string.reader_spacing)) {
            WrapChips(LineSpacing.entries, settings.spacing, { spacingLabel(it) }) { v -> onChange { it.copy(spacing = v) } }
        }
        SheetGroup(stringResource(R.string.reader_page)) {
            WrapChips(PageTheme.entries, settings.page, { pageLabel(it) }, icon = ::pageIcon) { v -> onChange { it.copy(page = v) } }
        }
        SheetGroup(stringResource(R.string.reader_paging)) {
            WrapChips(Paging.entries, settings.paging, { pagingLabel(it) }) { v -> onChange { it.copy(paging = v) } }
        }
        OxSwitchRow(stringResource(R.string.reader_hyphenation), settings.hyphenation, { on -> onChange { it.copy(hyphenation = on) } })
    }
}

@Composable
private fun <T> WrapChips(options: List<T>, selected: T, label: @Composable (T) -> String, icon: ((T) -> Int?)? = null, onSelect: (T) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.s2),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        options.forEach { OxChip(label(it), it == selected, { onSelect(it) }, icon?.invoke(it)) }
    }
}

@Composable
private fun fontLabel(f: ReaderFont) = when (f) {
    ReaderFont.LITERATA -> "Literata"
    ReaderFont.MANROPE -> "Manrope"
    ReaderFont.ATKINSON -> "Atkinson"
    ReaderFont.SYSTEM -> stringResource(R.string.reader_font_system)
}

@Composable
private fun spacingLabel(s: LineSpacing) = stringResource(
    when (s) {
        LineSpacing.TIGHT -> R.string.reader_spacing_tight
        LineSpacing.NORMAL -> R.string.reader_spacing_normal
        LineSpacing.LOOSE -> R.string.reader_spacing_loose
    },
)

@Composable
private fun pageLabel(p: PageTheme) = stringResource(
    when (p) {
        PageTheme.APP -> R.string.reader_page_app
        PageTheme.DARK -> R.string.theme_dark
        PageTheme.LIGHT -> R.string.theme_light
    },
)

private fun pageIcon(p: PageTheme): Int? = when (p) {
    PageTheme.APP -> null
    PageTheme.DARK -> R.drawable.ic_dark_mode
    PageTheme.LIGHT -> R.drawable.ic_light_mode
}

@Composable
private fun pagingLabel(p: Paging) = stringResource(if (p == Paging.PAGES) R.string.reader_paging_pages else R.string.reader_paging_scroll)

// ---------- Зміст, закладки, виділення, пошук ----------

@Composable
fun TocSheet(book: ParsedBook, current: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    // частини довгих розділів без назви в зміст не йдуть; якщо назв немає зовсім — нумеруємо
    val titled = book.chapters.indices.filter { book.chapters[it].title.isNotBlank() }
    val entries = titled.ifEmpty { book.chapters.indices.toList() }
    val active = entries.lastOrNull { it <= current }
    OxSheet(onDismiss = onDismiss, title = stringResource(R.string.reader_toc)) { close ->
        LazyColumn(Modifier.heightIn(max = ListHeight)) {
            items(entries) { i ->
                OxListItem(
                    title = book.chapters[i].title.ifBlank { stringResource(R.string.reader_part, i + 1) },
                    selected = i == active,
                    onClick = {
                        onGo(i)
                        close()
                    },
                )
            }
        }
    }
}

@Composable
fun BookmarksSheet(bookmarks: List<Bookmark>, onGo: (Bookmark) -> Unit, onDelete: (Bookmark) -> Unit, onDismiss: () -> Unit) {
    OxSheet(onDismiss = onDismiss, title = stringResource(R.string.reader_bookmarks)) { close ->
        if (bookmarks.isEmpty()) {
            Text(stringResource(R.string.reader_bookmarks_empty), style = OxygenType.body, color = Oxygen.colors.inkMuted)
        }
        LazyColumn(Modifier.heightIn(max = ListHeight)) {
            items(bookmarks, key = { it.id }) { b ->
                OxListItem(
                    title = b.chapterTitle,
                    supporting = b.snippet,
                    leading = { OxIcon(R.drawable.ic_bookmark, tint = Oxygen.colors.attentionInk, size = 22.dp) },
                    trailing = {
                        OxIconButton(R.drawable.ic_delete, stringResource(R.string.action_delete), { onDelete(b) })
                    },
                    onClick = {
                        onGo(b)
                        close()
                    },
                )
            }
        }
    }
}

@Composable
fun BookHighlightsSheet(highlights: List<Highlight>, onGo: (Highlight) -> Unit, onDismiss: () -> Unit) {
    OxSheet(onDismiss = onDismiss, title = stringResource(R.string.highlights)) { close ->
        if (highlights.isEmpty()) {
            Text(stringResource(R.string.reader_highlights_empty), style = OxygenType.body, color = Oxygen.colors.inkMuted)
        }
        LazyColumn(Modifier.heightIn(max = ListHeight), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
            items(highlights, key = { it.id }) { h ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm))
                        .pointerInput(h.id) { detectTapGestures { onGo(h); close() } }
                        .padding(Space.s2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.padding(top = 6.dp).size(10.dp).background(Mark.colors[h.color.mod(8)], CircleShape))
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                        Text(h.text, style = QuoteStyle, color = Oxygen.colors.ink, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        if (h.note.isNotBlank()) Text(h.note, style = OxygenType.body, color = Oxygen.colors.inkMuted)
                        Text(h.chapterTitle, style = OxygenType.caption, color = Oxygen.colors.inkMuted, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchSheet(
    query: String,
    onQuery: (String) -> Unit,
    results: List<SearchHit>,
    book: ParsedBook,
    onGo: (SearchHit) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Oxygen.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OxSheet(onDismiss = onDismiss, title = stringResource(R.string.reader_search)) { close ->
        Row(
            Modifier.fillMaxWidth().height(Sizes.target).background(c.surfaceVariant, CircleShape).padding(horizontal = Space.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            OxIcon(R.drawable.ic_search, tint = c.inkMuted)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(stringResource(R.string.reader_search_hint), style = OxygenType.bodyLg, color = c.inkMuted, maxLines = 1)
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
        if (query.trim().length >= 2 && results.isEmpty()) {
            Text(stringResource(R.string.reader_search_nothing), style = OxygenType.body, color = c.inkMuted)
        }
        LazyColumn(Modifier.heightIn(max = ListHeight)) {
            items(results) { hit ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm))
                        .pointerInput(hit) { detectTapGestures { onGo(hit); close() } }
                        .padding(vertical = Space.s2, horizontal = Space.s2),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(book.displayTitle(hit.chapter), style = OxygenType.caption, color = c.inkMuted, maxLines = 1)
                    Text(
                        buildAnnotatedString {
                            append(hit.snippet)
                            addStyle(SpanStyle(background = c.accentSoft, fontWeight = FontWeight.Bold), hit.matchInSnippet.first, hit.matchInSnippet.last + 1)
                        },
                        style = OxygenType.body,
                        color = c.ink,
                    )
                }
            }
        }
    }
}

// ---------- Повзунок і нотатка ----------

/** Повзунок місця в книзі: тягнеш — бачиш, куди потрапиш; відпускаєш — переходиш. */
@Composable
fun ProgressSlider(fraction: Float, onPreview: (Float?) -> Unit, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    val c = Oxygen.colors
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: fraction
    BoxWithConstraints(modifier.fillMaxWidth().height(24.dp).padding(horizontal = Space.s1)) {
        val width = constraints.maxWidth.toFloat()
        fun at(x: Float) = (x / width).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(width) {
                    detectTapGestures { p -> onSeek(at(p.x)) }
                }
                .pointerInput(width) {
                    detectHorizontalDragGestures(
                        onDragStart = { p -> dragging = at(p.x); onPreview(dragging) },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragging = at(change.position.x)
                            onPreview(dragging)
                        },
                        onDragEnd = {
                            dragging?.let(onSeek)
                            dragging = null
                            onPreview(null)
                        },
                        onDragCancel = {
                            dragging = null
                            onPreview(null)
                        },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(c.surfaceVariant, CircleShape))
            Box(Modifier.fillMaxWidth(shown).height(4.dp).background(c.accentInk, CircleShape))
            val thumb = with(LocalDensity.current) { 10.dp.toPx() }
            Box(
                Modifier
                    .offset { IntOffset((shown * width - thumb).roundToInt(), 0) }
                    .size(20.dp)
                    .shadow(Elevation.fab, CircleShape)
                    .background(c.accent, CircleShape),
            )
        }
    }
}

/** Нотатка до виділення. */
@Composable
fun NoteDialog(initial: String, quote: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Oxygen.colors
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        shape = RoundedCornerShape(Radius.xl),
        title = { Text(stringResource(R.string.reader_note), style = OxygenType.screenTitle, color = c.accentInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                Text(quote, style = QuoteStyle, color = c.inkMuted, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .background(c.surfaceVariant, RoundedCornerShape(Radius.md))
                        .padding(Space.s3),
                ) {
                    if (text.isEmpty()) Text(stringResource(R.string.reader_note_hint), style = OxygenType.body, color = c.inkMuted)
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = OxygenType.body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accentInk),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
        },
        dismissButton = { OxButton(stringResource(R.string.action_cancel), onDismiss, variant = ButtonVariant.Quiet) },
        confirmButton = { OxButton(stringResource(R.string.action_save), { onSave(text.trim()) }) },
    )
}

// ---------- Дії з текстом ----------

/** Переклад: системна дія «Перекласти», інакше — меню обробки тексту (Google Перекладач, DeepL…). */
fun translate(context: Context, text: String, chooserTitle: String) {
    try {
        context.startActivity(Intent("android.intent.action.TRANSLATE").putExtra(Intent.EXTRA_TEXT, text))
    } catch (_: ActivityNotFoundException) {
        processText(context, text, chooserTitle)
    }
}

/** Словник: системна дія «Визначення», інакше — обробка тексту, інакше — пошук у вебі. */
fun define(context: Context, text: String, chooserTitle: String) {
    try {
        context.startActivity(Intent("android.intent.action.DEFINE").putExtra(Intent.EXTRA_TEXT, text))
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text))
        } catch (_: ActivityNotFoundException) {
            processText(context, text, chooserTitle)
        }
    }
}

private fun processText(context: Context, text: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_PROCESS_TEXT)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Librarian", text))
}
