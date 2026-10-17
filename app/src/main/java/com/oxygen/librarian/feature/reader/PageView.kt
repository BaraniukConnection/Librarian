package com.oxygen.librarian.feature.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.oxygen.librarian.core.Position
import com.oxygen.librarian.ui.theme.Motion
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Фон діапазону тексту: виділення, знахідка пошуку, поточне виділення пальцем. */
class Deco(val start: Int, val end: Int, val color: Color)

/** Виділений пальцем шматок тексту; [highlightId] — якщо це вже збережене виділення. */
data class Selection(val chapter: Int, val start: Int, val end: Int, val highlightId: Long? = null)

/** Де на екрані лежить текст сторінки, у пікселях. */
data class TextArea(val left: Float, val top: Float, val width: Int, val height: Int)

/**
 * Стан гортання сторінками: місце в книзі, розкладені сусідні розділи й зсув сторінки під пальцем.
 * Місце змінюється тут одразу, без кола через ViewModel: інакше на кадр блимнула б стара сторінка.
 */
@Stable
class PagerState(val paginator: Paginator, initial: Position) {
    var pos by mutableStateOf(initial)
    val drag = Animatable(0f)
    private var tick by mutableIntStateOf(0)

    val current: ChapterPages? get() = tick.let { paginator.peek(pos.chapter) }
    val index: Int get() = current?.pageOf(pos.offset) ?: 0

    /** Розкладає розділ під читачем і обидва сусідні — щоб перегорнути в них без очікування. */
    suspend fun load() {
        val ch = pos.chapter
        paginator.get(ch); tick++
        if (ch + 1 < paginator.chapterCount) { paginator.get(ch + 1); tick++ }
        if (ch > 0) { paginator.get(ch - 1); tick++ }
    }

    /** Сторінка поруч: [dir] +1 — наступна, −1 — попередня. `null` — край книги або розділ ще розкладається. */
    fun neighbor(dir: Int): Pair<ChapterPages, Int>? {
        tick
        val cur = current ?: return null
        val i = cur.pageOf(pos.offset)
        return if (dir > 0) {
            if (i + 1 < cur.pages.size) cur to i + 1 else paginator.peek(cur.chapter + 1)?.let { it to 0 }
        } else {
            if (i > 0) cur to i - 1 else paginator.peek(cur.chapter - 1)?.let { it to it.pages.lastIndex }
        }
    }

    suspend fun turn(dir: Int, width: Float, animate: Boolean) {
        val (pages, i) = neighbor(dir) ?: run { if (drag.value != 0f) drag.animateTo(0f, tween(150)); return }
        if (animate) drag.animateTo(-dir * width, tween(250, easing = Motion.standardOut))
        pos = Position(pages.chapter, pages.pages[i].start)
        drag.snapTo(0f)
    }

    /** Зміщення в тексті розділу під точкою екрана (або `null`, якщо палець поза текстом). */
    fun offsetAt(p: Offset, area: TextArea): Int? {
        val cur = current ?: return null
        val page = cur.pages[index]
        val y = p.y - area.top
        if (y < -24 || y > area.height + 24) return null
        return cur.offsetAt(Offset(p.x - area.left, page.top + y.coerceIn(0f, page.bottom - page.top - 1)))
    }
}

/** Малює одну сторінку: фони виділень під текстом, текст, ручки виділення. */
fun DrawScope.drawPage(pages: ChapterPages, index: Int, decos: List<Deco>, ink: Color, handles: Selection?, handleColor: Color) {
    val page = pages.pages.getOrNull(index) ?: return
    val layout = pages.layout
    clipRect(-8.dp.toPx(), -24.dp.toPx(), size.width + 8.dp.toPx(), page.bottom - page.top + 24.dp.toPx()) {
        translate(0f, -page.top) {
            clipRect(-8.dp.toPx(), page.top, size.width + 8.dp.toPx(), page.bottom) {
                for (d in decos) {
                    val s = max(d.start, page.start)
                    val e = min(d.end, page.end)
                    if (s < e) drawPath(pages.path(s, e), d.color)
                }
                drawText(layout, color = ink)
            }
            if (handles != null) {
                val r = 7.dp.toPx()
                val w = 2.dp.toPx()
                if (handles.start in page.start..page.end) {
                    val c = pages.cursor(handles.start)
                    drawRect(handleColor, Offset(c.left - w / 2, c.top), androidx.compose.ui.geometry.Size(w, c.height))
                    drawCircle(handleColor, r, Offset(c.left, c.top - r))
                }
                if (handles.end in page.start..page.end) {
                    val c = pages.cursor(handles.end, rangeEnd = true)
                    drawRect(handleColor, Offset(c.left - w / 2, c.top), androidx.compose.ui.geometry.Size(w, c.height))
                    drawCircle(handleColor, r, Offset(c.left, c.bottom + r))
                }
            }
        }
    }
}

/**
 * Гортання сторінками. Тап по краях — назад/вперед, у центр — керування; довгий тап — речення.
 * Сторінка їде за пальцем (motion: page.turn), без анімації — для e-ink і «зменшити рух».
 */
@Composable
fun PagedReader(
    state: PagerState,
    area: TextArea,
    animate: Boolean,
    ink: Color,
    accent: Color,
    selection: Selection?,
    decosFor: (chapter: Int) -> List<Deco>,
    onCenterTap: () -> Unit,
    onTapOffset: (chapter: Int, offset: Int) -> Boolean,
    onLongPress: (chapter: Int, offset: Int) -> Unit,
    onMoveHandle: (start: Boolean, offset: Int) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val cur = state.current ?: return
    val index = state.index
    val latestSelection by rememberUpdatedState(selection)
    // жести перезапускаються лише коли з'являється чи зникає виділення — решту беремо свіжим
    val animateTurns by rememberUpdatedState(animate)
    val clear by rememberUpdatedState(onClearSelection)
    val tapOffset by rememberUpdatedState(onTapOffset)
    val centerTap by rememberUpdatedState(onCenterTap)
    val longPress by rememberUpdatedState(onLongPress)
    val moveHandle by rememberUpdatedState(onMoveHandle)

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(state, area, selection == null) {
                detectTapGestures(
                    onTap = { p ->
                        if (latestSelection != null) {
                            clear()
                            return@detectTapGestures
                        }
                        val w = size.width
                        val central = p.x > w * 0.25f && p.x < w * 0.75f
                        val offset = if (central) state.offsetAt(p, area) else null
                        val ch = state.current?.chapter
                        if (offset != null && ch != null && tapOffset(ch, offset)) return@detectTapGestures
                        when {
                            p.x <= w * 0.25f -> scope.launch { state.turn(-1, w.toFloat(), animateTurns) }
                            p.x >= w * 0.75f -> scope.launch { state.turn(1, w.toFloat(), animateTurns) }
                            else -> centerTap()
                        }
                    },
                    onLongPress = { p ->
                        val ch = state.current?.chapter ?: return@detectTapGestures
                        state.offsetAt(p, area)?.let { longPress(ch, it) }
                    },
                )
            }
            .pointerInput(state, area, selection == null) {
                if (latestSelection == null) {
                    // гортання за пальцем
                    val tracker = VelocityTracker()
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            total = 0f
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            total += amount
                            if (animateTurns) {
                                val canNext = state.neighbor(1) != null
                                val canPrev = state.neighbor(-1) != null
                                val next = (state.drag.value + amount).let {
                                    when {
                                        it < 0 && !canNext -> it * 0.2f
                                        it > 0 && !canPrev -> it * 0.2f
                                        else -> it
                                    }
                                }.coerceIn(-size.width.toFloat(), size.width.toFloat())
                                scope.launch { state.drag.snapTo(next) }
                            }
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity().x
                            val w = size.width.toFloat()
                            val dir = when {
                                total < -w * 0.2f || (v < -900f && total < 0) -> 1
                                total > w * 0.2f || (v > 900f && total > 0) -> -1
                                else -> 0
                            }
                            scope.launch {
                                if (dir != 0) state.turn(dir, w, animateTurns) else state.drag.animateTo(0f, tween(200, easing = Motion.standardOut))
                            }
                        },
                        onDragCancel = { scope.launch { state.drag.animateTo(0f, tween(200)) } },
                    )
                } else {
                    // ручки виділення: тягнеться та, біля якої почали
                    var moving: Boolean? = null
                    val grab = 36.dp.toPx()
                    detectDragGestures(
                        onDragStart = { p ->
                            val sel = latestSelection ?: return@detectDragGestures
                            val c = state.current ?: return@detectDragGestures
                            val page = c.pages[state.index]
                            fun screen(o: Int, end: Boolean) = c.cursor(o, end).let { Offset(it.left + area.left, it.center.y - page.top + area.top) }
                            val ds = (screen(sel.start, false) - p).getDistance()
                            val de = (screen(sel.end, true) - p).getDistance()
                            moving = when {
                                ds < grab && ds <= de -> true
                                de < grab -> false
                                else -> null
                            }
                        },
                        onDrag = { change, _ ->
                            val which = moving ?: return@detectDragGestures
                            change.consume()
                            // трохи вище пальця — щоб бачити, куди тягнеш
                            state.offsetAt(change.position - Offset(0f, 24.dp.toPx()), area)?.let { moveHandle(which, it) }
                        },
                        onDragEnd = { moving = null },
                    )
                }
            },
    ) {
        val drag = state.drag.value
        val decos = decosFor(cur.chapter)
        Canvas(
            Modifier
                .offset { IntOffset((area.left + drag).roundToInt(), area.top.roundToInt()) }
                .size(with(density) { area.width.toDp() }, with(density) { area.height.toDp() }),
        ) { drawPage(cur, index, decos, ink, selection?.takeIf { it.chapter == cur.chapter }, accent) }

        if (drag != 0f) {
            state.neighbor(if (drag < 0) 1 else -1)?.let { (pages, i) ->
                val w = area.width + area.left * 2
                val x = area.left + drag + if (drag < 0) w else -w
                Canvas(
                    Modifier
                        .offset { IntOffset(x.roundToInt(), area.top.roundToInt()) }
                        .size(with(density) { area.width.toDp() }, with(density) { area.height.toDp() }),
                ) { drawPage(pages, i, decosFor(pages.chapter), ink, null, accent) }
            }
        }
    }
}
