package com.ultimatesudoku.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.ultimatesudoku.app.data.GameStore
import com.ultimatesudoku.app.ui.Dialogs
import com.ultimatesudoku.app.ui.LevelMapView
import com.ultimatesudoku.app.ui.Snack
import com.ultimatesudoku.app.ui.SquareGlowView
import com.ultimatesudoku.app.ui.systemBarInsets
import com.ultimatesudoku.core.LevelMap
import com.ultimatesudoku.core.StoryPuzzles

/**
 * One story chapter, on the story's deep blue whatever the theme: its picture glowing through a grid of
 * squares, its part of Anna's dream, and underneath, how many of its levels are finished and the map of
 * them and the routes through them. A chapter with its puzzles can be played: the entrance first, then
 * each level a finished one leads to.
 */
class ChapterActivity : BaseActivity() {

    private var index = 0
    private lateinit var map: LevelMap
    private lateinit var mapView: LevelMapView

    override fun themeOverlay(dark: Boolean): Int =
        if (dark) R.style.ThemeOverlay_Sudoku_Story_Dark else R.style.ThemeOverlay_Sudoku_Story

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chapter)
        fitUnderSystemBars()
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        index = intent.getIntExtra(EXTRA_CHAPTER, 0).coerceIn(StoryActivity.CHAPTERS.indices)
        val chapter = StoryActivity.CHAPTERS[index]
        showArt(chapter)
        findViewById<TextView>(R.id.kicker).text = getString(R.string.chapter_kicker, "%02d".format(index + 1))
        findViewById<TextView>(R.id.chapter_title).text = chapter.title
        findViewById<TextView>(R.id.story).text = getString(chapter.story)

        map = LevelMap.forChapter(index)
        mapView = findViewById(R.id.map)
        mapView.map = map
        mapView.onLevelTap = LevelMapView.OnLevelTapListener(::play)
        findViewById<View>(R.id.footnote).visibility = if (StoryPuzzles.playable(index)) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        // Back from a level, perhaps finished: the count and the map move on.
        showProgress()
    }

    /** How many levels are finished, and on the map, which are finished and which are ready to play next. */
    private fun showProgress() {
        val numbers = map.levels.map { it.number }.toSet()
        val finished = app.story.finished(index) intersect numbers
        // The entrance is always open, and finishing a level opens those its paths lead to.
        val ready = (setOf(map.entrance.number) + map.paths.filter { it.from in finished }.map { it.to }) - finished
        findViewById<TextView>(R.id.levels).text = getString(R.string.chapter_levels, finished.size, map.levels.size)
        val levels = resources.getQuantityString(R.plurals.levels, map.levels.size, map.levels.size)
        val routes = resources.getQuantityString(R.plurals.routes, map.routes(), map.routes())
        mapView.finished = finished
        mapView.open = ready
        mapView.contentDescription = getString(R.string.chapter_map, levels, routes, finished.size, ready.size)
    }

    private fun play(level: LevelMap.Level) {
        when {
            !StoryPuzzles.playable(index) -> Snack.show(this, getString(R.string.story_locked))
            level.number !in mapView.open && level.number !in mapView.finished -> Snack.show(this, getString(R.string.level_locked))
            else -> start(level.number)
        }
    }

    /** Plays [level]: picks it up where it was left if it's the one in progress, else asks before leaving that one. */
    private fun start(level: Int) {
        val saved = app.store.game(GameStore.Slot.STORY)
        val inProgress = saved != null && saved.isPlaying && !saved.values.contentEquals(saved.givens)
        when {
            saved != null && saved.isPlaying && saved.tag == StoryPuzzles.tag(index, level) ->
                startActivity(GameActivity.resume(this, GameStore.Slot.STORY))
            inProgress -> Dialogs.show(
                this,
                getString(R.string.level_replace_title),
                getString(R.string.level_replace_message),
                listOf(
                    Dialogs.Action(getString(R.string.level_replace_confirm), primary = true) {
                        startActivity(GameActivity.newStory(this, index, level))
                    },
                    Dialogs.Action(getString(R.string.cancel)),
                ),
            )
            else -> startActivity(GameActivity.newStory(this, index, level))
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
