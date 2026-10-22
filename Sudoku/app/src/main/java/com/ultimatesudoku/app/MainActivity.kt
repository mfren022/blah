package com.ultimatesudoku.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.ultimatesudoku.app.data.GameStore
import com.ultimatesudoku.app.ui.Dialogs
import com.ultimatesudoku.app.ui.TileView
import com.ultimatesudoku.app.ui.formatTime
import com.ultimatesudoku.core.Daily
import com.ultimatesudoku.core.GameMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreen(R.layout.activity_main)

        findViewById<TileView>(R.id.tile_story).apply {
            bind(R.drawable.ic_book, getString(R.string.home_story), getString(R.string.home_story_subtitle), getString(R.string.badge_soon))
            setOnClickListener { startActivity(Intent(this@MainActivity, StoryActivity::class.java)) }
        }
        findViewById<TileView>(R.id.tile_random).apply {
            bind(R.drawable.ic_shuffle, getString(R.string.home_random), getString(R.string.home_random_subtitle), primary = true)
            setOnClickListener { startActivity(Intent(this@MainActivity, RandomActivity::class.java)) }
        }
        findViewById<TileView>(R.id.tile_daily).setOnClickListener { openDaily() }
        findViewById<TileView>(R.id.tile_solver).apply {
            bind(R.drawable.ic_grid, getString(R.string.home_solver), getString(R.string.home_solver_subtitle))
            setOnClickListener { startActivity(Intent(this@MainActivity, SolverActivity::class.java)) }
        }
        findViewById<View>(R.id.continue_card).setOnClickListener {
            startActivity(GameActivity.resume(this, GameStore.Slot.MAIN))
        }
        findViewById<View>(R.id.button_stats).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        findViewById<View>(R.id.button_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        bindContinue()
        bindDaily()
    }

    private fun bindContinue() {
        val card = findViewById<View>(R.id.continue_card)
        val game = app.store.game(GameStore.Slot.MAIN)?.takeIf { it.isPlaying }
        if (game == null) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        val mode = if (game.mode == GameMode.CUSTOM) getString(R.string.mode_custom) else getString(R.string.mode_random)
        findViewById<TextView>(R.id.continue_title).text = "${game.difficulty.title} · $mode"
        findViewById<TextView>(R.id.continue_time).text = formatTime(game.elapsedMs)
        val progress = (game.progress * 100).toInt().coerceIn(2, 100)
        findViewById<View>(R.id.continue_progress).layoutParams =
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, progress.toFloat())
        findViewById<View>(R.id.continue_rest).layoutParams =
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (100 - progress).toFloat())
    }

    private fun bindDaily() {
        val today = LocalDate.now()
        val tag = today.toString()
        val difficulty = Daily.difficulty(today.dayOfWeek.value)
        val date = today.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
        val streak = app.stats.dailyStreak(today)
        val saved = app.store.game(GameStore.Slot.DAILY)?.takeIf { it.tag == tag && it.isPlaying }
        val badge = when {
            app.stats.isDailyDone(tag) -> getString(R.string.badge_done)
            saved != null -> getString(R.string.badge_resume)
            else -> null
        }
        val subtitle = buildString {
            append(date).append(" · ").append(difficulty.title)
            if (streak > 0) append(" · ").append(getString(R.string.daily_streak, streak))
        }
        findViewById<TileView>(R.id.tile_daily).bind(R.drawable.ic_calendar, getString(R.string.home_daily), subtitle, badge)
    }

    private fun openDaily() {
        val today = LocalDate.now()
        val tag = today.toString()
        if (app.stats.isDailyDone(tag)) {
            Dialogs.show(
                this,
                getString(R.string.daily_done_title),
                getString(
                    R.string.daily_done_message,
                    Daily.difficulty(today.dayOfWeek.value).title,
                    formatTime(app.stats.dailyTime(tag)),
                ),
                listOf(Dialogs.Action(getString(R.string.ok), primary = true)),
            )
            return
        }
        val saved = app.store.game(GameStore.Slot.DAILY)
        if (saved != null && saved.tag == tag && saved.isPlaying) {
            startActivity(GameActivity.resume(this, GameStore.Slot.DAILY))
        } else {
            startActivity(GameActivity.newDaily(this))
        }
    }
}
