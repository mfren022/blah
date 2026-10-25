package com.draftpad.app

import android.content.Context
import com.draftpad.core.GitHub
import com.draftpad.core.Names

/** The editor's remembered choices: last draft, tool, colour, width and grid. */
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("draftpad", Context.MODE_PRIVATE)

    var lastDraft: String?
        get() = prefs.getString("last_draft", null)
        set(v) = prefs.edit().putString("last_draft", v).apply()

    var tool: Tool
        get() = Tool.entries.firstOrNull { it.name == prefs.getString("tool", null) } ?: Tool.PEN
        set(v) = prefs.edit().putString("tool", v.name).apply()

    var colorIndex: Int
        get() = prefs.getInt("color", 0).coerceIn(Pens.COLORS.indices)
        set(v) = prefs.edit().putInt("color", v).apply()

    var widthIndex: Int
        get() = prefs.getInt("width", 1).coerceIn(Pens.WIDTHS.indices)
        set(v) = prefs.edit().putInt("width", v).apply()

    var grid: Boolean
        get() = prefs.getBoolean("grid", true)
        set(v) = prefs.edit().putBoolean("grid", v).apply()
}

/**
 * Where "Push to GitHub" sends sketches. Kept in its own file, "github", which backups leave out
 * (res/xml/backup_rules.xml), so the token stays on this phone.
 */
class GitHubSettings(context: Context) {
    private val prefs = context.getSharedPreferences("github", Context.MODE_PRIVATE)

    var repoText: String
        get() = prefs.getString("repo", "") ?: ""
        set(v) = prefs.edit().putString("repo", v.trim()).apply()

    var branch: String
        get() = prefs.getString("branch", "") ?: ""
        set(v) = prefs.edit().putString("branch", v.trim()).apply()

    var folder: String
        get() = prefs.getString("folder", Names.DEFAULT_FOLDER) ?: Names.DEFAULT_FOLDER
        set(v) = prefs.edit().putString("folder", Names.cleanFolder(v)).apply()

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(v) = prefs.edit().putString("token", v.trim()).apply()

    val repo: GitHub.Repo? get() = GitHub.Repo.parse(repoText)

    val isReady: Boolean get() = repo != null && token.isNotEmpty()
}
