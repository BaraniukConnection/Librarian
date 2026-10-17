package com.oxygen.librarian.feature.settings

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.oxygen.librarian.BuildConfig
import com.oxygen.librarian.R
import com.oxygen.librarian.data.prefs.AppPreferences
import com.oxygen.librarian.data.prefs.ThemeMode
import com.oxygen.librarian.ui.ChipRow
import com.oxygen.librarian.ui.OxIcon
import com.oxygen.librarian.ui.OxListItem
import com.oxygen.librarian.ui.OxSwitchRow
import com.oxygen.librarian.ui.OxTopBar
import com.oxygen.librarian.ui.SectionHeader
import com.oxygen.librarian.ui.theme.Oxygen
import com.oxygen.librarian.ui.theme.OxygenType
import com.oxygen.librarian.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** Мови інтерфейсу. Перша — мова ресурсів без суфікса (values/). */
val APP_LANGUAGES = listOf("en", "uk")

@HiltViewModel
class SettingsViewModel @Inject constructor(private val prefs: AppPreferences) : ViewModel() {
    val theme: StateFlow<ThemeMode> = prefs.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.LIGHT)
    val pageAnimation: StateFlow<Boolean> = prefs.pageAnimation.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setPageAnimation(on: Boolean) {
        viewModelScope.launch { prefs.setPageAnimation(on) }
    }
}

/** Ліцензії того, що їде разом із застосунком. OFL і Apache вимагають, щоб текст ліцензії був поруч. */
enum class License(val title: String, @RawRes val text: Int, @StringRes val kind: Int) {
    MANROPE("Manrope", R.raw.manrope_ofl, R.string.license_ofl),
    LITERATA("Literata", R.raw.literata_ofl, R.string.license_ofl),
    ATKINSON("Atkinson Hyperlegible Next", R.raw.atkinson_ofl, R.string.license_ofl),
    SYMBOLS("Material Symbols", R.raw.apache_license, R.string.license_apache),
    JSOUP("jsoup", R.raw.jsoup_mit, R.string.license_mit),
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenLicense: (License) -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val pageAnimation by viewModel.pageAnimation.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        OxTopBar(stringResource(R.string.settings), onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Space.s4)
                .padding(bottom = Space.s6),
            verticalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            SectionHeader(stringResource(R.string.settings_appearance), Modifier.padding(top = Space.s4))
            Text(stringResource(R.string.settings_theme), style = OxygenType.label, color = Oxygen.colors.ink)
            // зміна одразу набирає чинності — тому чіпи, а не форма з кнопкою
            ChipRow(
                options = ThemeMode.entries,
                selected = theme,
                label = { stringResource(themeLabel(it)) },
                onSelect = viewModel::setTheme,
                icon = { themeIcon(it) },
            )
            LanguagePicker()

            SectionHeader(stringResource(R.string.settings_reading), Modifier.padding(top = Space.s6))
            OxSwitchRow(
                label = stringResource(R.string.settings_page_animation),
                supporting = stringResource(R.string.settings_page_animation_hint),
                checked = pageAnimation,
                onChange = viewModel::setPageAnimation,
            )

            SectionHeader(stringResource(R.string.settings_about), Modifier.padding(top = Space.s6))
            OxListItem(
                title = stringResource(R.string.app_name),
                supporting = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            )
            License.entries.forEach { license ->
                OxListItem(
                    title = license.title,
                    supporting = stringResource(license.kind),
                    trailing = { OxIcon(R.drawable.ic_chevron_right, tint = Oxygen.colors.inkMuted, size = 22.dp) },
                    onClick = { onOpenLicense(license) },
                )
            }
        }
    }
}

@Composable
fun LicenseScreen(license: License, onBack: () -> Unit) {
    val resources = LocalContext.current.resources
    val text = remember(license) { resources.openRawResource(license.text).bufferedReader().use { it.readText() } }
    Column(Modifier.fillMaxSize()) {
        OxTopBar(license.title, onBack)
        Text(
            text = text,
            style = OxygenType.body,
            color = Oxygen.colors.inkMuted,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Space.s4, vertical = Space.s2),
        )
    }
}

private fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun themeIcon(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> R.drawable.ic_brightness_auto
    ThemeMode.LIGHT -> R.drawable.ic_light_mode
    ThemeMode.DARK -> R.drawable.ic_dark_mode
}

/**
 * Мова застосунку — як у Oxygen Finance Manager. Назви мов — кожна своєю мовою: так людина знайде свою,
 * навіть якщо зараз інтерфейс незнайомою. Вибір зберігає AppCompat і сам перестворює екран.
 */
@Composable
private fun LanguagePicker() {
    val current = LocalConfiguration.current.locales[0].language.takeIf { it in APP_LANGUAGES } ?: APP_LANGUAGES.first()
    Column(Modifier.padding(top = Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text(stringResource(R.string.settings_language), style = OxygenType.label, color = Oxygen.colors.ink)
        ChipRow(
            options = APP_LANGUAGES,
            selected = current,
            label = { tag ->
                Locale.forLanguageTag(tag).let { it.getDisplayLanguage(it) }.replaceFirstChar { it.titlecase(Locale.forLanguageTag(tag)) }
            },
            onSelect = { tag ->
                if (tag != current) AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            },
        )
    }
}
