package com.ultimatesudoku.app.ui

import android.content.Context
import com.ultimatesudoku.app.R
import com.ultimatesudoku.app.StoryActivity
import com.ultimatesudoku.core.GameMode
import com.ultimatesudoku.core.LevelMap
import com.ultimatesudoku.core.StoryPuzzles
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Random", "Daily · Oct 22", "Custom puzzle" or "The Pond · Level 3". */
fun Context.modeName(mode: GameMode, tag: String): String = when (mode) {
    GameMode.RANDOM -> getString(R.string.mode_random)
    GameMode.STORY -> {
        val (chapter, level) = StoryPuzzles.parseTag(tag) ?: (0 to 1)
        val title = StoryActivity.CHAPTERS.getOrNull(chapter)?.title.orEmpty()
        if (level == LevelMap.forChapter(chapter).final.number) {
            getString(R.string.mode_story_final, title)
        } else {
            getString(R.string.mode_story, title, level)
        }
    }
    GameMode.CUSTOM -> getString(R.string.mode_custom)
    GameMode.DAILY -> {
        val date = runCatching { LocalDate.parse(tag) }.getOrNull()
        getString(R.string.mode_daily, date?.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())) ?: tag)
    }
}
