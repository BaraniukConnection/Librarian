package com.oxygen.librarian

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oxygen.librarian.data.prefs.ThemeMode
import com.oxygen.librarian.feature.settings.SettingsViewModel
import com.oxygen.librarian.navigation.AppNavigation
import com.oxygen.librarian.ui.theme.LocalReduceMotion
import com.oxygen.librarian.ui.theme.OxygenTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

/** Застосунок «зовні» теж світлий чи темний за вибором у налаштуваннях; тема сторінки при читанні — окремо. */
@Composable
fun appIsDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    /** Книга, яку відкрили «за допомогою» Librarian з іншого застосунку. */
    private val incoming = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // дати й числа форматуються за Locale.getDefault — він має збігатися з мовою застосунку
        Locale.setDefault(resources.configuration.locales[0])
        if (savedInstanceState == null) incoming.value = intent.bookUri()
        val reduceMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

        setContent {
            val settings: SettingsViewModel = hiltViewModel()
            val mode by settings.theme.collectAsStateWithLifecycle()
            val dark = appIsDark(mode)

            // системні панелі беруть bg застосунку; іконки на них — за темою застосунку, а не системи
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                OxygenTheme(darkTheme = dark) {
                    AppNavigation(
                        incoming = incoming.value,
                        onIncomingHandled = { incoming.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.bookUri()?.let { incoming.value = it }
    }

    private fun Intent.bookUri(): Uri? = data?.takeIf { action == Intent.ACTION_VIEW }
}
