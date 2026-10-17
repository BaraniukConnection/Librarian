package com.oxygen.librarian.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oxygen.librarian.R
import com.oxygen.librarian.data.BookRepository
import com.oxygen.librarian.data.db.Book
import com.oxygen.librarian.ui.theme.Elevation
import com.oxygen.librarian.ui.theme.Mark
import com.oxygen.librarian.ui.theme.Oxygen
import com.oxygen.librarian.ui.theme.OxygenType
import com.oxygen.librarian.ui.theme.Radius
import com.oxygen.librarian.ui.theme.Sizes
import com.oxygen.librarian.ui.theme.Space
import kotlinx.coroutines.launch

/*
 * Компоненти Oxygen Theme. Екрани збираються лише з них.
 * Іконки — Material Symbols (заповнені), перетворені у векторні ресурси `ic_*`.
 */

@Composable
fun OxIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, size: Dp = 20.dp, tint: Color = Color.Unspecified, label: String? = null) {
    Icon(
        painter = painterResource(icon),
        contentDescription = label,
        tint = if (tint == Color.Unspecified) androidx.compose.material3.LocalContentColor.current else tint,
        modifier = modifier.size(size),
    )
}

/** 44dp диск для навігації й дій екрана. [filled] — одна головна дія (м'ятна заливка). */
@Composable
fun OxIconButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val c = Oxygen.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (filled) c.accent else c.surfaceVariant,
        contentColor = if (filled) c.onAccent else c.accentInk,
        shadowElevation = if (filled) Elevation.fab else 0.dp,
        modifier = modifier.size(Sizes.target),
    ) {
        Box(contentAlignment = Alignment.Center) { OxIcon(icon, label = label) }
    }
}

/** Верх екрана. Без власного фону — сторінка «дихає» до самого краю. Назад — завжди зліва. */
@Composable
fun OxTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = Space.s2)
            .heightIn(min = 64.dp)
            .padding(horizontal = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        if (onBack != null) OxIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack)
        Text(
            text = title,
            style = OxygenType.screenTitle,
            color = Oxygen.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = Space.s2),
        )
        actions()
    }
}

/** Пігулка вибору. Чіпи обирають, а не запускають дії. */
@Composable
fun OxChip(label: String, selected: Boolean, onClick: () -> Unit, @DrawableRes icon: Int? = null) {
    val c = Oxygen.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) c.accent else c.surfaceVariant,
        contentColor = if (selected) c.onAccent else c.inkMuted,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = Space.s2),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) OxIcon(icon, size = 16.dp)
            Text(
                text = label,
                style = if (selected) OxygenType.label.copy(fontWeight = FontWeight.Bold) else OxygenType.label,
                maxLines = 1,
            )
        }
    }
}

/** Ряд чіпів. [wrap] — переносити на новий рядок (у шторці); інакше — горизонтальний скрол до краю екрана. */
@Composable
fun <T> ChipRow(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> Int?)? = null,
    edgePadding: Dp = 0.dp,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = edgePadding),
        horizontalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        options.forEach { option ->
            OxChip(label(option), option == selected, { onSelect(option) }, icon?.invoke(option))
        }
    }
}

/** Розділювач груп: ЧИТАЮ ЗАРАЗ, УСІ КНИГИ. */
@Composable
fun SectionHeader(label: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            style = OxygenType.overline,
            color = Oxygen.colors.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

/** Прев'ю однієї речі користувача. Кнопок усередині немає: тап відкриває. */
@Composable
fun OxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.md)
    Column(
        modifier
            .shadow(Elevation.card, shape)
            .clip(shape)
            .background(Oxygen.colors.surface)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
                } else {
                    Modifier
                },
            )
            .padding(Space.s4),
        content = content,
    )
}

enum class ButtonVariant { Primary, Secondary, Quiet, DangerFill }

@Composable
fun OxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    compact: Boolean = false,
) {
    val c = Oxygen.colors
    val container = when (variant) {
        ButtonVariant.Primary -> c.accent
        ButtonVariant.DangerFill -> c.danger
        else -> Color.Transparent
    }
    val content = when (variant) {
        ButtonVariant.Primary -> c.onAccent
        ButtonVariant.Secondary -> c.accentInk
        ButtonVariant.Quiet -> c.inkMuted
        ButtonVariant.DangerFill -> c.onDanger
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Radius.lg),
        color = container,
        contentColor = content,
        border = if (variant == ButtonVariant.Secondary) BorderStroke(2.dp, c.accentInk) else null,
        modifier = modifier.heightIn(min = if (compact) 40.dp else Sizes.row).alpha(if (enabled) 1f else 0.38f),
    ) {
        Row(
            Modifier.padding(horizontal = if (compact) Space.s3 else Space.s6),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = if (variant == ButtonVariant.Quiet) OxygenType.button.copy(fontWeight = FontWeight.SemiBold) else OxygenType.button,
                maxLines = 1,
            )
            if (icon != null) OxIcon(icon, size = 18.dp)
        }
    }
}

/** Коротке блокуюче питання. Для видалення заголовок danger-ink. Відмова зліва, підтвердження справа. */
@Composable
fun OxConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    val c = Oxygen.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        shape = RoundedCornerShape(Radius.xl),
        title = { Text(title, style = OxygenType.screenTitle, color = if (destructive) c.dangerInk else c.accentInk) },
        text = { Text(text, style = OxygenType.body, color = c.ink) },
        dismissButton = { OxButton(stringResource(R.string.action_cancel), onDismiss, variant = ButtonVariant.Quiet) },
        confirmButton = {
            OxButton(confirmLabel, onConfirm, variant = if (destructive) ButtonVariant.DangerFill else ButtonVariant.Primary)
        },
    )
}

/**
 * Шторка знизу. [content] отримує `close`: шторка спершу доанімовує закриття, а потім зникає.
 * Закривається потягом униз, тапом по затемненню й системним «назад».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OxSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    titleTrailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val c = Oxygen.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.surface,
        contentColor = c.ink,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.line, width = 36.dp, height = 4.dp) },
    ) {
        Column(
            Modifier.padding(start = Space.s5, end = Space.s5, bottom = Space.s6),
            verticalArrangement = Arrangement.spacedBy(Space.s5),
        ) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = OxygenType.screenTitle, color = c.accentInk, modifier = Modifier.weight(1f))
                    titleTrailing?.invoke(this)
                }
            }
            content(close)
        }
    }
}

/** Підписана група в шторці: «Шрифт», «Сторінка»… */
@Composable
fun SheetGroup(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text(label, style = OxygenType.label, color = Oxygen.colors.inkMuted)
        content()
    }
}

/** Рядок списку: заголовок і підпис обрізаються трьома крапками. Праворуч — рівно одна річ. */
@Composable
fun OxListItem(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = Oxygen.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.row)
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (selected) c.accentSoft else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.s2, vertical = Space.s2),
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = OxygenType.title.copy(fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (supporting != null) Text(supporting, style = OxygenType.body, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

/** Налаштування, що діє одразу: підпис зліва, перемикач справа, тапається весь рядок. */
@Composable
fun OxSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, supporting: String? = null) {
    val c = Oxygen.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.row)
            .clip(RoundedCornerShape(Radius.sm))
            .clickable { onChange(!checked) }
            .padding(vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = OxygenType.bodyLg.copy(lineHeight = OxygenType.title.lineHeight), color = c.ink)
            if (supporting != null) Text(supporting, style = OxygenType.body, color = c.inkMuted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------- Інструменти ----------

class Tool(@DrawableRes val icon: Int, val label: String, val active: Boolean = false, val onClick: () -> Unit)

/** Темна смуга інструментів (однакова в обох темах). Групи діляться тонкою рискою. */
@Composable
fun OxToolbar(groups: List<List<Tool>>, modifier: Modifier = Modifier) {
    val c = Oxygen.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.lg))
            .background(c.tool)
            .height(Sizes.row)
            .padding(horizontal = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        groups.forEachIndexed { gi, group ->
            if (gi > 0) Box(Modifier.padding(horizontal = Space.s1).width(1.dp).height(22.dp).background(c.toolDivider))
            group.forEach { tool ->
                Box(
                    modifier = Modifier
                        .size(Sizes.tool)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(if (tool.active) c.toolActive else Color.Transparent)
                        .clickable(onClick = tool.onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    OxIcon(tool.icon, size = 21.dp, tint = if (tool.active) c.accent else c.toolIcon, label = tool.label)
                }
            }
        }
    }
}

/** Вісім кольорів виділення. Вибраний — більший, з кільцем. */
@Composable
fun OxSwatches(selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Oxygen.colors
    val shape = RoundedCornerShape(Radius.lg)
    Row(
        modifier = modifier
            .shadow(Elevation.floating, shape)
            .clip(shape)
            .background(c.surface)
            .padding(horizontal = Space.s3, vertical = Space.s2),
        horizontalArrangement = Arrangement.spacedBy(Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mark.colors.forEachIndexed { i, color ->
            val on = i == selected
            Box(
                Modifier
                    .size(if (on) 30.dp else 26.dp)
                    .then(if (on) Modifier.border(2.dp, c.ink, CircleShape).padding(4.dp) else Modifier)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onSelect(i) },
            )
        }
    }
}

// ---------- Обкладинка ----------

/**
 * Обкладинка книги: картинка з файлу, а якщо її немає — згенерована з кольору `mark-*`:
 * автор дрібними великими, коротка риска й назва.
 */
@Composable
fun BookCover(book: Book, repository: BookRepository, modifier: Modifier = Modifier, compact: Boolean = false) {
    val image by produceState<ImageBitmap?>(null, book.cover) {
        value = book.cover?.let { repository.cover(it) }?.asImageBitmap()
    }
    val shape = RoundedCornerShape(if (compact) 4.dp else Radius.xs)
    Box(modifier.shadow(Elevation.card, shape).clip(shape).background(Mark.cover(book.color))) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (!compact) {
            val fg = Mark.onCover(book.color)
            Column(
                Modifier.fillMaxSize().padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    // розрив після дефіса: «НЕЧУЙ-» / «ЛЕВИЦЬКИЙ», а не «НЕЧУЙ-ЛЕВИЦЬКИ» / «Й»
                    book.author.uppercase().replace("-", "-\u200B"),
                    style = OxygenType.coverAuthor,
                    color = fg.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(18.dp).height(2.dp).background(fg.copy(alpha = 0.7f)))
                    Text(book.title, style = OxygenType.coverTitle, color = fg, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Тонка смуга прогресу: `surface-variant` під `accent-ink`. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val c = Oxygen.colors
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(c.accentInk))
    }
}

/** Порожній екран: один заголовок `display`, пояснення і (за потреби) одна дія. */
@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(PaddingValues(horizontal = Space.s6, vertical = Space.s12)),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OxIcon(R.drawable.ic_auto_stories, size = 48.dp, tint = Oxygen.colors.accentInk)
        Text(title, style = OxygenType.screenTitle, color = Oxygen.colors.ink)
        Text(text, style = OxygenType.body, color = Oxygen.colors.inkMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(Space.s2))
            action()
        }
    }
}
