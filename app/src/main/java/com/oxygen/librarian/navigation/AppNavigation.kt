package com.oxygen.librarian.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.oxygen.librarian.feature.highlights.HighlightsScreen
import com.oxygen.librarian.feature.library.LibraryScreen
import com.oxygen.librarian.feature.reader.ReaderScreen
import com.oxygen.librarian.feature.settings.License
import com.oxygen.librarian.feature.settings.LicenseScreen
import com.oxygen.librarian.feature.settings.SettingsScreen
import com.oxygen.librarian.ui.theme.LocalReduceMotion
import com.oxygen.librarian.ui.theme.Motion
import com.oxygen.librarian.ui.theme.Oxygen

/** Одне головне місце — Бібліотека; решта відкривається з неї і повертається до неї. */
@Composable
fun AppNavigation(
    incoming: Uri?,
    onIncomingHandled: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    // перехід між екранами: fade + масштаб 0.98 → 1 за 250 мс (motion: screen.transition)
    val reduce = LocalReduceMotion.current
    val ms = if (reduce) Motion.REDUCED_MS else 250
    NavHost(
        navController = navController,
        startDestination = LibraryRoute,
        modifier = Modifier.fillMaxSize().background(Oxygen.colors.bg),
        enterTransition = {
            if (reduce) fadeIn(tween(ms)) else fadeIn(tween(ms, easing = Motion.standardOut)) + scaleIn(tween(ms, easing = Motion.standardOut), initialScale = 0.98f)
        },
        exitTransition = { fadeOut(tween(ms / 2)) },
        popEnterTransition = {
            if (reduce) fadeIn(tween(ms)) else fadeIn(tween(ms, easing = Motion.standardOut)) + scaleIn(tween(ms, easing = Motion.standardOut), initialScale = 0.98f)
        },
        popExitTransition = { fadeOut(tween(ms / 2)) },
    ) {
        composable<LibraryRoute> {
            LibraryScreen(
                incoming = incoming,
                onIncomingHandled = onIncomingHandled,
                onOpenBook = { id -> navController.navigate(ReaderRoute(id)) { launchSingleTop = true } },
                onOpenHighlights = { navController.navigate(HighlightsRoute) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
            )
        }
        composable<ReaderRoute> {
            ReaderScreen(onBack = { navController.popBackStack() })
        }
        composable<HighlightsRoute> {
            HighlightsScreen(
                onBack = { navController.popBackStack() },
                onOpen = { h -> navController.navigate(ReaderRoute(h.bookId, h.chapter, h.start)) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenLicense = { navController.navigate(LicenseRoute(it.name)) },
            )
        }
        composable<LicenseRoute> { entry ->
            LicenseScreen(License.valueOf(entry.toRoute<LicenseRoute>().name), onBack = { navController.popBackStack() })
        }
    }
}
