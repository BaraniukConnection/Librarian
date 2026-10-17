package com.oxygen.librarian.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ReaderFont { LITERATA, MANROPE, ATKINSON, SYSTEM }

enum class LineSpacing(val multiplier: Float) { TIGHT(1.4f), NORMAL(1.667f), LOOSE(1.95f) }

/** Тема сторінки при читанні. [APP] — як у застосунку (а той, своєю чергою, може йти за системою). */
enum class PageTheme { APP, DARK, LIGHT }

enum class Paging { PAGES, SCROLL }

enum class SortOrder { RECENT, TITLE, AUTHOR }

data class ReaderSettings(
    val fontSize: Int = 18,
    val font: ReaderFont = ReaderFont.LITERATA,
    val spacing: LineSpacing = LineSpacing.NORMAL,
    val page: PageTheme = PageTheme.APP,
    val paging: Paging = Paging.PAGES,
    val hyphenation: Boolean = true,
) {
    companion object {
        const val MIN_SIZE = 12
        const val MAX_SIZE = 32
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")
private val THEME = stringPreferencesKey("theme_mode")
private val FONT_SIZE = intPreferencesKey("reader_font_size")
private val FONT = stringPreferencesKey("reader_font")
private val SPACING = stringPreferencesKey("reader_spacing")
private val PAGE = stringPreferencesKey("reader_page")
private val PAGING = stringPreferencesKey("reader_paging")
private val HYPHENS = booleanPreferencesKey("reader_hyphenation")
private val PAGE_ANIMATION = booleanPreferencesKey("page_animation")
private val SORT = stringPreferencesKey("library_sort")

private inline fun <reified T : Enum<T>> Preferences.enum(key: Preferences.Key<String>, default: T): T =
    this[key]?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

class AppPreferences(private val context: Context) {

    /** Первинна тема — світла; «Як у системі» людина вмикає сама. */
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { it.enum(THEME, ThemeMode.LIGHT) }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[THEME] = mode.name }

    val reader: Flow<ReaderSettings> = context.dataStore.data.map { p ->
        ReaderSettings(
            fontSize = p[FONT_SIZE] ?: 18,
            font = p.enum(FONT, ReaderFont.LITERATA),
            spacing = p.enum(SPACING, LineSpacing.NORMAL),
            page = p.enum(PAGE, PageTheme.APP),
            paging = p.enum(PAGING, Paging.PAGES),
            hyphenation = p[HYPHENS] ?: true,
        )
    }

    suspend fun setReader(s: ReaderSettings) = context.dataStore.edit {
        it[FONT_SIZE] = s.fontSize.coerceIn(ReaderSettings.MIN_SIZE, ReaderSettings.MAX_SIZE)
        it[FONT] = s.font.name
        it[SPACING] = s.spacing.name
        it[PAGE] = s.page.name
        it[PAGING] = s.paging.name
        it[HYPHENS] = s.hyphenation
    }

    /** Вимкнене — сторінка змінюється миттєво: для e-ink екранів, де анімація лише смугує. */
    val pageAnimation: Flow<Boolean> = context.dataStore.data.map { it[PAGE_ANIMATION] ?: true }

    suspend fun setPageAnimation(on: Boolean) = context.dataStore.edit { it[PAGE_ANIMATION] = on }

    val sort: Flow<SortOrder> = context.dataStore.data.map { it.enum(SORT, SortOrder.RECENT) }

    suspend fun setSort(order: SortOrder) = context.dataStore.edit { it[SORT] = order.name }
}
