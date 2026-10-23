package com.ultimatesudoku.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.ultimatesudoku.app.ui.LevelMapView
import com.ultimatesudoku.app.ui.Snack
import com.ultimatesudoku.core.LevelMap

/**
 * One story chapter, on the story's deep blue whatever the theme: placeholder art (a glowing grid of
 * squares), what the chapter teaches, and underneath, the map of its levels and the routes through
 * them. Levels can't be played until story mode is built.
 */
class ChapterActivity : BaseActivity() {

    override fun themeOverlay(dark: Boolean): Int =
        if (dark) R.style.ThemeOverlay_Sudoku_Story_Dark else R.style.ThemeOverlay_Sudoku_Story

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreen(R.layout.activity_chapter)
        val index = intent.getIntExtra(EXTRA_CHAPTER, 0).coerceIn(StoryActivity.CHAPTERS.indices)
        val chapter = StoryActivity.CHAPTERS[index]
        val open = StoryActivity.isOpen(index)
        val number = "%02d".format(index + 1)
        findViewById<TextView>(R.id.kicker).text =
            getString(if (open) R.string.chapter_kicker else R.string.chapter_kicker_locked, number)
        findViewById<TextView>(R.id.chapter_title).text = chapter.title
        findViewById<TextView>(R.id.lesson).text = chapter.lesson
        findViewById<TextView>(R.id.techniques).text = chapter.techniques.joinToString("  ·  ") { it.title }

        val map = LevelMap.forChapter(index)
        val levels = resources.getQuantityString(R.plurals.levels, map.levels.size, map.levels.size)
        val routes = resources.getQuantityString(R.plurals.routes, map.routes(), map.routes())
        val hint = if (open) {
            getString(R.string.chapter_open_hint)
        } else {
            getString(R.string.chapter_locked_hint, StoryActivity.CHAPTERS[index - 1].title)
        }
        findViewById<TextView>(R.id.summary).text = "${getString(R.string.chapter_summary, levels, routes)}\n$hint"
        findViewById<LevelMapView>(R.id.map).apply {
            this.map = map
            this.open = if (open) setOf(map.entrance.number) else emptySet()
            contentDescription = getString(if (open) R.string.chapter_map_open else R.string.chapter_map_locked, levels, routes)
            onLevelTap = LevelMapView.OnLevelTapListener { Snack.show(this@ChapterActivity, getString(R.string.story_locked)) }
        }
    }

    companion object {
        private const val EXTRA_CHAPTER = "chapter"

        /** Opens chapter [index], counting from 0. */
        fun open(context: Context, index: Int): Intent =
            Intent(context, ChapterActivity::class.java).putExtra(EXTRA_CHAPTER, index)
    }
}
