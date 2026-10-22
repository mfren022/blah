package com.ultimatesudoku.app.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class Settings(private val prefs: SharedPreferences) {
    enum class Theme { SYSTEM, LIGHT, DARK }

    var theme: Theme
        get() = Theme.entries.getOrElse(prefs.getInt("theme", 0)) { Theme.SYSTEM }
        set(value) = prefs.edit().putInt("theme", value.ordinal).apply()

    /** End the game after [MISTAKE_LIMIT] mistakes. Only applies while [checkMistakes] is on. */
    var mistakeLimit by flag("mistake_limit", true)

    /** Show wrong digits in red as soon as they are entered, and count them as mistakes. */
    var checkMistakes by flag("check_mistakes", true)
    var autoRemoveNotes by flag("auto_remove_notes", true)
    var highlightPeers by flag("highlight_peers", true)
    var highlightSame by flag("highlight_same", true)
    var hideFinished by flag("hide_finished", true)
    var showTimer by flag("show_timer", true)
    var keepScreenOn by flag("keep_screen_on", true)
    var haptics by flag("haptics", true)

    val limitMistakes: Boolean get() = checkMistakes && mistakeLimit

    fun isDark(context: Context) = when (theme) {
        Theme.LIGHT -> false
        Theme.DARK -> true
        Theme.SYSTEM -> context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    private fun flag(key: String, default: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getBoolean(key, default)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            prefs.edit().putBoolean(key, value).apply()
        }
    }

    companion object {
        const val MISTAKE_LIMIT = 3
    }
}
