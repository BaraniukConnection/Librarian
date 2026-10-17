package com.oxygen.librarian.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.oxygen.librarian.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** «≈ 3 год 20 хв» — скільки ще читати. */
@Composable
fun formatDuration(minutes: Int): String {
    val m = minutes.coerceAtLeast(1)
    return if (m < 60) stringResource(R.string.duration_m, m) else stringResource(R.string.duration_hm, m / 60, m % 60)
}

/** Дати — як їх пишуть люди: 12.09. */
private val DAY_MONTH = DateTimeFormatter.ofPattern("dd.MM.")

fun formatDay(millis: Long): String = DAY_MONTH.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
