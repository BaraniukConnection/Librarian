package com.oxygen.librarian.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.oxygen.librarian.core.Position
import com.oxygen.librarian.ui.ButtonVariant
import com.oxygen.librarian.ui.OxButton
import com.oxygen.librarian.ui.theme.Space

/**
 * Прокрутка в межах розділу. Розділ розкладений так само, як для сторінок, тож прокрутка — це ті самі
 * сторінки впритул одна до одної. Між розділами — кнопки, щоб довгий розділ не підвантажувався під пальцем.
 */
@Composable
fun ScrollReader(
    paginator: Paginator,
    located: Located,
    area: TextArea,
    bottomPadding: Float,
    ink: Color,
    decosFor: (chapter: Int) -> List<Deco>,
    previousLabel: String?,
    nextLabel: String?,
    onGo: (Position) -> Unit,
    onShown: (Position, atEnd: Boolean) -> Unit,
    onCenterTap: () -> Unit,
    onTapOffset: (chapter: Int, offset: Int) -> Boolean,
    onLongPress: (chapter: Int, offset: Int) -> Unit,
) {
    val density = LocalDensity.current
    var chapter by remember { mutableIntStateOf(located.pos.chapter) }
    var ready by remember { mutableStateOf<ChapterPages?>(null) }
    val list = rememberLazyListState()
    val shown by rememberUpdatedState(onShown)

    LaunchedEffect(paginator, located.jump) {
        chapter = located.pos.chapter
        val pages = paginator.get(chapter)
        ready = pages
        val i = pages.pageOf(located.pos.offset)
        val page = pages.pages[i]
        val line = pages.layout.getLineForOffset(located.pos.offset.coerceAtMost(pages.length))
        list.scrollToItem(i + 1, (pages.layout.getLineTop(line) - page.top).toInt().coerceAtLeast(0))
    }
    val cur = ready?.takeIf { it.chapter == chapter } ?: return

    LaunchedEffect(cur) {
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.collect { (item, scroll) ->
            val i = (item - 1).coerceIn(0, cur.pages.lastIndex)
            val page = cur.pages[i]
            val y = page.top + if (item >= 1) scroll.toFloat() else 0f
            val line = cur.layout.getLineForVerticalPosition(y)
            val atEnd = chapter == paginator.chapterCount - 1 && !list.canScrollForward
            shown(Position(chapter, cur.layout.getLineStart(line)), atEnd)
        }
    }

    val tapOffset by rememberUpdatedState(onTapOffset)
    val centerTap by rememberUpdatedState(onCenterTap)
    val longPress by rememberUpdatedState(onLongPress)
    val decos = decosFor(chapter)

    LazyColumn(
        state = list,
        contentPadding = PaddingValues(
            top = with(density) { area.top.toDp() },
            bottom = with(density) { bottomPadding.toDp() },
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "prev") {
            Box(Modifier.fillMaxWidth().padding(bottom = Space.s4), contentAlignment = Alignment.Center) {
                if (previousLabel != null) {
                    OxButton(previousLabel, { onGo(Position(chapter - 1, Int.MAX_VALUE)) }, variant = ButtonVariant.Quiet, compact = true)
                }
            }
        }
        items(cur.pages.size, key = { "${cur.chapter}-$it" }) { i ->
            val page = cur.pages[i]
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { (page.bottom - page.top).toDp() })
                    .pointerInput(cur, i) {
                        fun offsetAt(p: Offset) = cur.offsetAt(Offset(p.x - area.left, page.top + p.y))
                        detectTapGestures(
                            onTap = { p -> if (!tapOffset(cur.chapter, offsetAt(p))) centerTap() },
                            onLongPress = { p -> longPress(cur.chapter, offsetAt(p)) },
                        )
                    },
            ) {
                translate(area.left, 0f) { drawPage(cur, i, decos, ink, null, Color.Transparent) }
            }
        }
        item(key = "next") {
            Box(Modifier.fillMaxWidth().padding(top = Space.s8), contentAlignment = Alignment.Center) {
                if (nextLabel != null) {
                    OxButton(nextLabel, { onGo(Position(chapter + 1, 0)) }, variant = ButtonVariant.Secondary, compact = true)
                }
            }
        }
    }
}
