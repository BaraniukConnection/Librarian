package com.oxygen.librarian.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxygen.librarian.R
import com.oxygen.librarian.data.prefs.ReaderFont

/*
 * Oxygen Theme — токени з librarian-design/tokens/tokens.json, змаплені рівно один раз.
 * Поза цим файлом hex-кольорів, розмірів шрифтів і радіусів у застосунку бути не повинно.
 */

// ---------- Колір ----------

@Immutable
data class OxygenColors(
    val bg: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val line: Color,
    val ink: Color,
    val inkMuted: Color,
    val accent: Color,
    val onAccent: Color,
    /** Акцент як передній план: посилання, активні іконки, фокус. Не `accent` — м'ята на білому 1.5:1. */
    val accentInk: Color,
    val accentSoft: Color,
    val attention: Color,
    val attentionInk: Color,
    val danger: Color,
    val onDanger: Color,
    val dangerInk: Color,
    val scrim: Color,
    /** Смуга інструментів лишається темною в обох темах. */
    val tool: Color = Color(0xFF16332A),
    val toolIcon: Color = Color(0xFFBFD9CE),
    val toolActive: Color = Color(0xFF15543F),
    val toolDivider: Color = Color(0xFF2A4C40),
    val isDark: Boolean,
)

private val Dark = OxygenColors(
    bg = Color(0xFF10221C),
    surface = Color(0xFF162E26),
    surfaceVariant = Color(0xFF233932),
    line = Color(0xFF23483C),
    ink = Color(0xFFE6F2EC),
    inkMuted = Color(0xFF92C9B7),
    accent = Color(0xFF13ECA4),
    onAccent = Color(0xFF0B1F18),
    accentInk = Color(0xFF13ECA4),
    accentSoft = Color(0xFF15543F),
    attention = Color(0xFFF59E0B),
    attentionInk = Color(0xFFF59E0B),
    danger = Color(0xFFDC2626),
    onDanger = Color(0xFFFFFFFF),
    dangerInk = Color(0xFFFCA5A5),
    scrim = Color(0x8C000000),
    isDark = true,
)

private val Light = OxygenColors(
    bg = Color(0xFFF3FAF7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFDFF2EB),
    line = Color(0xFFD3E8DF),
    ink = Color(0xFF1A2F24),
    inkMuted = Color(0xFF4A6B5A),
    accent = Color(0xFF13ECA4),
    onAccent = Color(0xFF0B1F18),
    accentInk = Color(0xFF047857),
    accentSoft = Color(0xFFD0FBED),
    attention = Color(0xFFF59E0B),
    attentionInk = Color(0xFFB45309),
    danger = Color(0xFFDC2626),
    onDanger = Color(0xFFFFFFFF),
    dangerInk = Color(0xFFDC2626),
    scrim = Color(0x66000000),
    isDark = false,
)

/**
 * Вісім кольорів вмісту: згенеровані обкладинки й виділення. Ніколи для станів UI.
 * Порядок — як у шторці виділення; індекс зберігається в базі.
 */
object Mark {
    val colors = listOf(
        Color(0xFF13ECA4), // mint
        Color(0xFF3B82F6), // blue
        Color(0xFFF59E0B), // amber
        Color(0xFFEF4444), // red
        Color(0xFFA855F7), // purple
        Color(0xFF10B981), // emerald
        Color(0xFFEC4899), // pink
        Color(0xFF64748B), // slate
    )
    const val SLATE = 7

    /** Нове виділення без вибору кольору — бурштинове, як класичний маркер. */
    const val DEFAULT_HIGHLIGHT = 2

    /** Порядок кольорів обкладинок, як на полиці в дизайні: смарагдова, синя, бурштинова, червона… */
    private val coverOrder = intArrayOf(5, 1, 2, 3, 6, 4, 0, 7)

    fun cover(index: Int): Color = colors[coverOrder[index.mod(8)]]

    /** Напис на обкладинці: темний на всіх, крім сланцевої. */
    fun onCover(index: Int): Color = if (coverOrder[index.mod(8)] == SLATE) Color.White else Color(0xFF0B1F18)

    /** Фон виділення в тексті — 32% кольору (motion: highlight.create). */
    const val HIGHLIGHT_ALPHA = 0.32f
}

/** Слоти M3 за platforms.md. Решта заповнена токенами явно, щоб базова фіолетова палітра M3 ніде не просочилась. */
private fun OxygenColors.toScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = ink,
        inversePrimary = accentInk,
        secondary = accentInk,
        onSecondary = bg,
        secondaryContainer = accentSoft,
        onSecondaryContainer = ink,
        tertiary = attentionInk,
        onTertiary = bg,
        tertiaryContainer = surfaceVariant,
        onTertiaryContainer = ink,
        background = bg,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = inkMuted,
        surfaceTint = Color.Transparent,
        inverseSurface = ink,
        inverseOnSurface = bg,
        error = danger,
        onError = onDanger,
        errorContainer = danger,
        onErrorContainer = onDanger,
        outline = inkMuted,
        outlineVariant = line,
        scrim = scrim,
        surfaceBright = surface,
        surfaceDim = bg,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceVariant,
    )
}

// ---------- Шрифт ----------

/** Змінні шрифти: вага задається віссю `wght`, а не окремими файлами. */
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int, style: FontStyle = FontStyle.Normal) = Font(
    resId = res,
    weight = FontWeight(weight),
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val Manrope = FontFamily(listOf(400, 500, 600, 700, 800).map { variable(R.font.manrope, it) })

private val Literata = FontFamily(
    variable(R.font.literata, 400),
    variable(R.font.literata, 600),
    variable(R.font.literata_italic, 400, FontStyle.Italic),
    variable(R.font.literata_italic, 600, FontStyle.Italic),
)

private val Atkinson = FontFamily(variable(R.font.atkinson, 400), variable(R.font.atkinson, 600))

/** Шрифт тексту книги. */
fun readerFamily(font: ReaderFont): FontFamily = when (font) {
    ReaderFont.LITERATA -> Literata
    ReaderFont.MANROPE -> Manrope
    ReaderFont.ATKINSON -> Atkinson
    ReaderFont.SYSTEM -> FontFamily.Default
}

/** Цитати з книги поза читанням (список виділень) — тим самим шрифтом, що й книга. */
val QuoteStyle = TextStyle(fontFamily = Literata, fontSize = 15.sp, lineHeight = 22.sp)

private fun style(size: Int, line: Int, weight: Int, tracking: Float = 0f) = TextStyle(
    fontFamily = Manrope,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.sp,
)

/** Іменовані стилі Oxygen. Використовувати лише їх — жодних «довільних» розмірів. */
object OxygenType {
    val display = style(40, 44, 800, -0.5f)
    val screenTitle = style(22, 28, 700)
    val title = style(16, 22, 700)
    val bodyLg = style(16, 26, 400)
    val body = style(14, 20, 400)
    val label = style(13, 18, 600)
    val button = style(15, 20, 700)
    val overline = style(13, 18, 700, 1.6f)
    val caption = style(12, 16, 500)
    val micro = style(11, 14, 600)

    /** Згенерована обкладинка: автор дрібними великими, назва жирно. */
    val coverAuthor = style(9, 12, 700, 1.2f)
    val coverTitle = style(15, 18, 800)
}

private val OxygenTypography = Typography(
    titleLarge = OxygenType.screenTitle,
    titleMedium = OxygenType.title,
    bodyLarge = OxygenType.bodyLg,
    bodyMedium = OxygenType.body,
    labelLarge = OxygenType.button,
    labelMedium = OxygenType.label,
    labelSmall = OxygenType.micro,
)

// ---------- Форма, простір, глибина ----------

private val OxygenShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

object Space {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp
    val s12 = 48.dp

    /** Колонка читання не ширша за це — на планшеті поля лишаються порожніми. */
    val measure = 680.dp
}

object Radius {
    val xs = 8.dp
    val sm = 10.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

object Sizes {
    val target = 44.dp
    val row = 56.dp
    val tool = 42.dp
}

object Elevation {
    val card = 1.dp
    val fab = 6.dp
    val floating = 10.dp
}

// ---------- Рух (librarian-design/motion/motion.json) ----------

object Motion {
    val standardOut = FastOutSlowInEasing
    val standardIn = FastOutLinearInEasing
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** «Зменшити рух»: усе — простий fade до 150 мс, гортання — миттєве. */
    const val REDUCED_MS = 150
}

/** Система просить менше руху (масштаб анімацій 0) — вмикається в MainActivity. */
val LocalReduceMotion = staticCompositionLocalOf { false }

// ---------- Тема ----------

private val LocalOxygenColors = staticCompositionLocalOf { Light }

object Oxygen {
    val colors: OxygenColors
        @Composable get() = LocalOxygenColors.current
}

/** Динамічні кольори (Material You) вимкнені свідомо: Oxygen має виглядати як Oxygen. */
@Composable
fun OxygenTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) Dark else Light
    CompositionLocalProvider(LocalOxygenColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toScheme(),
            typography = OxygenTypography,
            shapes = OxygenShapes,
            content = content,
        )
    }
}
