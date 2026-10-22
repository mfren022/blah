package com.ultimatesudoku.app.ui

import android.content.Context
import com.ultimatesudoku.app.R
import com.ultimatesudoku.core.GameMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Random", "Daily · Oct 22" or "Custom puzzle". */
fun Context.modeName(mode: GameMode, tag: String): String = when (mode) {
    GameMode.RANDOM, GameMode.STORY -> getString(R.string.mode_random)
    GameMode.CUSTOM -> getString(R.string.mode_custom)
    GameMode.DAILY -> {
        val date = runCatching { LocalDate.parse(tag) }.getOrNull()
        getString(R.string.mode_daily, date?.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())) ?: tag)
    }
}
