package com.ultimatesudoku.app.data

import android.content.SharedPreferences
import com.ultimatesudoku.core.LevelMap

/** The story levels finished in each chapter, chapters counted from 0. Nothing finishes one until they can be played. */
class StoryProgress(private val prefs: SharedPreferences) {

    /** The numbers of the levels finished in [chapter]. */
    fun finished(chapter: Int): Set<Int> =
        prefs.getStringSet(key(chapter), null).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    fun finish(chapter: Int, level: Int) {
        val levels = finished(chapter) + level
        prefs.edit().putStringSet(key(chapter), levels.map { it.toString() }.toSet()).apply()
    }

    /** How many of [chapter]'s levels, laid out in [map], are finished. */
    fun finishedIn(chapter: Int, map: LevelMap): Int {
        val done = finished(chapter)
        return map.levels.count { it.number in done }
    }

    private fun key(chapter: Int) = "story.finished.$chapter"
}
