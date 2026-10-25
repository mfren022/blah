package com.ultimatesudoku.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.ultimatesudoku.app.ui.LevelMapView
import com.ultimatesudoku.app.ui.Snack
import com.ultimatesudoku.app.ui.SquareGlowView
import com.ultimatesudoku.app.ui.systemBarInsets
import com.ultimatesudoku.core.LevelMap

/**
 * One story chapter, on the story's deep blue whatever the theme: its picture glowing through a grid of
 * squares, its part of Anna's dream, and underneath, how many of its levels are finished and the map of
 * them and the routes through them. Levels can't be played until story mode is built.
 */
class ChapterActivity : BaseActivity() {

    override fun themeOverlay(dark: Boolean): Int =
        if (dark) R.style.ThemeOverlay_Sudoku_Story_Dark else R.style.ThemeOverlay_Sudoku_Story

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chapter)
        fitUnderSystemBars()
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        val index = intent.getIntExtra(EXTRA_CHAPTER, 0).coerceIn(StoryActivity.CHAPTERS.indices)
        val chapter = StoryActivity.CHAPTERS[index]
        showArt(chapter)
        findViewById<TextView>(R.id.kicker).text = getString(R.string.chapter_kicker, "%02d".format(index + 1))
        findViewById<TextView>(R.id.chapter_title).text = chapter.title
        findViewById<TextView>(R.id.story).text = getString(chapter.story)

        val map = LevelMap.forChapter(index)
        findViewById<TextView>(R.id.levels).text = getString(R.string.chapter_levels, app.story.finishedIn(index, map), map.levels.size)
        val levels = resources.getQuantityString(R.plurals.levels, map.levels.size, map.levels.size)
        val routes = resources.getQuantityString(R.plurals.routes, map.routes(), map.routes())
        findViewById<LevelMapView>(R.id.map).apply {
            this.map = map
            this.open = setOf(map.entrance.number)
            contentDescription = getString(R.string.chapter_map, levels, routes)
            onLevelTap = LevelMapView.OnLevelTapListener { Snack.show(this@ChapterActivity, getString(R.string.story_locked)) }
        }
    }

    /** Lights the grid with the chapter's picture. */
    private fun showArt(chapter: StoryActivity.Chapter) {
        findViewById<SquareGlowView>(R.id.glow).apply {
            art = BitmapFactory.decodeResource(resources, chapter.art, BitmapFactory.Options().apply { inScaled = false })
            contentDescription = getString(chapter.artDescription)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }

    /**
     * Lets the grid run up behind the status bar: only the top bar is pushed below it, and the rest
     * of the page is kept clear of the other bars.
     */
    private fun fitUnderSystemBars() {
        val content = findViewById<View>(R.id.content)
        val bar = findViewById<View>(R.id.top_bar)
        val barHeight = bar.layoutParams.height
        val bottom = content.paddingBottom
        content.setOnApplyWindowInsetsListener { v, insets ->
            val (left, top, right, below) = systemBarInsets(insets)
            v.setPadding(left, 0, right, bottom + below)
            bar.layoutParams.height = barHeight + top
            bar.setPadding(bar.paddingLeft, top, bar.paddingRight, 0)
            insets
        }
        content.requestApplyInsets()
    }

    companion object {
        private const val EXTRA_CHAPTER = "chapter"

        /** Opens chapter [index], counting from 0. */
        fun open(context: Context, index: Int): Intent =
            Intent(context, ChapterActivity::class.java).putExtra(EXTRA_CHAPTER, index)
    }
}
