package com.ultimatesudoku.app.data

import android.content.SharedPreferences
import com.ultimatesudoku.core.Technique

/** The strategies whose challenge has been solved at least once. */
class StrategyProgress(private val prefs: SharedPreferences) {

    fun practised(): Set<Technique> {
        val names = prefs.getStringSet(KEY, null).orEmpty()
        return Technique.entries.filter { it.name in names }.toSet()
    }

    fun practise(technique: Technique) {
        val names = practised().map { it.name }.toSet() + technique.name
        prefs.edit().putStringSet(KEY, names).apply()
    }

    private companion object {
        const val KEY = "strategies.practised"
    }
}
