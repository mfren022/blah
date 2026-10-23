package com.ultimatesudoku.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.ultimatesudoku.app.ui.LevelMapView
import com.ultimatesudoku.app.ui.PixelArtView
import com.ultimatesudoku.app.ui.Snack
import com.ultimatesudoku.app.ui.dp
import com.ultimatesudoku.app.ui.systemBarInsets
import com.ultimatesudoku.core.LevelMap

/**
 * One story chapter, on the story's deep blue whatever the theme: its art (or, until it has some, a
 * glowing grid of squares), what it teaches, and underneath, the map of its levels and the routes
 * through them. Levels can't be played until story mode is built.
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
        val open = StoryActivity.isOpen(index)
        if (chapter.art != 0) showArt(chapter)
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

    /** Swaps the placeholder grid for the chapter's art, and starts the title where the art ends. */
    private fun showArt(chapter: StoryActivity.Chapter) {
        val bitmap = BitmapFactory.decodeResource(resources, chapter.art, BitmapFactory.Options().apply { inScaled = false })
        findViewById<View>(R.id.glow).visibility = View.GONE
        findViewById<PixelArtView>(R.id.art).apply {
            visibility = View.VISIBLE
            setArt(bitmap)
            contentDescription = getString(chapter.artDescription)
        }
        findViewById<View>(R.id.header).apply { setPadding(paddingLeft, dp(12), paddingRight, paddingBottom) }
        if (Build.VERSION.SDK_INT < 35) {
            // Before Android 15 the status bar has its own colour: the sky's.
            @Suppress("DEPRECATION")
            window.statusBarColor = bitmap.getPixel(bitmap.width / 2, 0)
        }
    }

    /**
     * Lets the art and the glow run up behind the status bar: only the top bar is pushed below it,
     * and the rest of the page is kept clear of the other bars.
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
