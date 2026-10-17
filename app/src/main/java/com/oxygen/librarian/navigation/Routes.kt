package com.oxygen.librarian.navigation

import kotlinx.serialization.Serializable

@Serializable object LibraryRoute
@Serializable object HighlightsRoute
@Serializable object SettingsRoute
@Serializable data class LicenseRoute(val name: String)

/** [chapter] < 0 — відкрити там, де зупинились; інакше — на конкретному місці (з виділень). */
@Serializable data class ReaderRoute(val bookId: Long, val chapter: Int = -1, val offset: Int = 0)
