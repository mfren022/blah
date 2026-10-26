package com.ultimatesudoku.app

import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ultimatesudoku.app.data.GameStore
import com.ultimatesudoku.app.ui.BoardView
import com.ultimatesudoku.app.ui.Dialogs
import com.ultimatesudoku.app.ui.GrowOverlay
import com.ultimatesudoku.app.ui.TabBar
import com.ultimatesudoku.app.ui.TileView
import com.ultimatesudoku.app.ui.appliedSystemBars
import com.ultimatesudoku.app.ui.formatTime
import com.ultimatesudoku.app.ui.modeName
import com.ultimatesudoku.core.Daily
import com.ultimatesudoku.core.Difficulty
import com.ultimatesudoku.core.Strategies
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : BaseActivity() {

    /** The continue card growing into the game, while it does. */
    private var growing: GrowOverlay? = null

    private lateinit var tabs: TabBar

    /** The page each tab shows, in tab order: Play, Learn, Records and Settings. */
    private lateinit var pages: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreen(R.layout.activity_main)
        showLogo()

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
        findViewById<TileView>(R.id.tile_strategies).setOnClickListener {
            startActivity(Intent(this@MainActivity, StrategiesActivity::class.java))
        }
        findViewById<TileView>(R.id.tile_history).apply {
            bind(R.drawable.ic_history, getString(R.string.home_history), getString(R.string.home_history_empty))
            setOnClickListener { startActivity(Intent(this@MainActivity, HistoryActivity::class.java)) }
        }
        findViewById<TileView>(R.id.tile_stats).setOnClickListener {
            startActivity(Intent(this@MainActivity, StatsActivity::class.java))
        }
        findViewById<View>(R.id.continue_card).setOnClickListener { openContinue() }
        // A picture only: taps on it go to the card.
        findViewById<BoardView>(R.id.continue_board).isEnabled = false
        SettingsPanel(this, findViewById(R.id.page_settings)).build()

        pages = listOf(R.id.page_play, R.id.page_learn, R.id.page_records, R.id.page_settings).map { findViewById<View>(it) }
        tabs = findViewById(R.id.tabs)
        tabs.setTabs(
            listOf(
                TabBar.Tab(R.drawable.ic_play, getString(R.string.tab_play)),
                TabBar.Tab(R.drawable.ic_school, getString(R.string.tab_learn)),
                TabBar.Tab(R.drawable.ic_trophy, getString(R.string.tab_records)),
                TabBar.Tab(R.drawable.ic_settings, getString(R.string.tab_settings)),
            ),
        )
        tabs.onTabSelected = TabBar.OnTabSelectedListener(::showTab)
        // Back on the same tab after a turn of the phone or a change of theme.
        showTab(savedInstanceState?.getInt(STATE_TAB, TAB_PLAY) ?: TAB_PLAY)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, tabs.selected)
    }

    /** Shows the page of tab [index], from the top. */
    private fun showTab(index: Int) {
        tabs.select(index)
        pages.forEachIndexed { i, page -> page.visibility = if (i == index) View.VISIBLE else View.GONE }
        findViewById<ScrollView>(R.id.scroll).scrollTo(0, 0)
    }

    /** The name as a wordmark: "SUDOKU" in ink and "BLUE" in blue, in capitals however the type renders them. */
    private fun showLogo() {
        val name = getString(R.string.home_title).uppercase()
        val accent = getString(R.string.home_title_accent).uppercase()
        val logo = SpannableString("$name $accent")
        logo.setSpan(ForegroundColorSpan(palette.userDigit), name.length + 1, logo.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        findViewById<TextView>(R.id.home_title).apply {
            text = logo
            contentDescription = getString(R.string.app_name)
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from the game, or it closed before showing: the card is a card again.
        endGrowing()
        bindContinue()
        bindDaily()
        bindStrategies()
        bindHistory()
        bindStats()
    }

    /** How many games have been won, once any have been played. */
    private fun bindStats() {
        val records = Difficulty.entries.map { app.stats.record(it) }
        val started = records.sumOf { it.started }
        val subtitle = if (started == 0) {
            getString(R.string.home_stats_empty)
        } else {
            resources.getQuantityString(R.plurals.home_stats_summary, started, records.sumOf { it.won }, started)
        }
        findViewById<TileView>(R.id.tile_stats).bind(R.drawable.ic_stats, getString(R.string.home_stats), subtitle)
    }

    /** How many strategies have been practised, once there are any. */
    private fun bindStrategies() {
        val total = Strategies.ALL.size
        val practised = app.strategies.practised().size
        val subtitle = if (practised == 0) {
            getString(R.string.home_strategies_subtitle, total)
        } else {
            getString(R.string.home_strategies_progress, practised, total)
        }
        findViewById<TileView>(R.id.tile_strategies).bind(R.drawable.ic_school, getString(R.string.home_strategies), subtitle)
    }

    override fun onStop() {
        super.onStop()
        // Gone unseen: the game covers this screen by now, or the app left mid-growth.
        endGrowing()
    }

    @Deprecated("Still the back hook for apps not opted in to predictive back")
    override fun onBackPressed() {
        val overlay = growing
        when {
            // Unless it's done growing and the game is already opening.
            overlay != null -> if (overlay.isGrowing) overlay.shrink(::endGrowing)
            // Back from another tab goes to Play before it leaves.
            tabs.selected != TAB_PLAY -> showTab(TAB_PLAY)
            else -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    private fun bindHistory() {
        app.io.execute {
            val summary = app.history.summary()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val subtitle = if (summary.count == 0) {
                    getString(R.string.home_history_empty)
                } else {
                    getString(
                        R.string.home_history_summary,
                        resources.getQuantityString(R.plurals.boards, summary.count, summary.count),
                        formatTime(summary.totalMs),
                    )
                }
                findViewById<TileView>(R.id.tile_history)
                    .bind(R.drawable.ic_history, getString(R.string.home_history), subtitle)
            }
        }
    }

    private fun bindContinue() {
        val card = findViewById<View>(R.id.continue_card)
        val game = app.store.game(GameStore.Slot.MAIN)?.takeIf { it.isPlaying }
        val visibility = if (game == null) View.GONE else View.VISIBLE
        card.visibility = visibility
        findViewById<View>(R.id.continue_header).visibility = visibility
        if (game == null) return
        findViewById<BoardView>(R.id.continue_board).showGame(game, app.settings.checkMistakes)
        val mode = modeName(game.mode, game.tag)
        val time = formatTime(game.elapsedMs)
        val percent = (game.progress * 100).toInt()
        findViewById<TextView>(R.id.continue_mode).text = mode
        findViewById<TextView>(R.id.continue_title).text = game.difficulty.title
        findViewById<TextView>(R.id.continue_time).text = time
        findViewById<TextView>(R.id.continue_percent).text = getString(R.string.percent, percent)
        val bar = percent.coerceIn(2, 100)
        findViewById<View>(R.id.continue_progress).layoutParams =
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, bar.toFloat())
        findViewById<View>(R.id.continue_rest).layoutParams =
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (100 - bar).toFloat())
        card.contentDescription = getString(R.string.home_continue_description, game.difficulty.title, mode, time, percent)
    }

    /**
     * Opens the game in progress by growing its card into the game screen: the preview board lands
     * exactly where the game will draw its board, then the game opens on top without a window
     * animation and brings in its controls around the board.
     */
    private fun openContinue() {
        if (growing != null) return
        val game = app.store.game(GameStore.Slot.MAIN)?.takeIf { it.isPlaying }
        if (game == null) {
            bindContinue()
            return
        }
        val replacing = app.store.isGenerating && app.store.generatingSlot == GameStore.Slot.MAIN
        if (!ValueAnimator.areAnimatorsEnabled() || replacing) {
            // No motion wanted, or the game opens on a new puzzle that is still being generated.
            startActivity(GameActivity.resume(this, GameStore.Slot.MAIN))
            return
        }
        val content = findViewById<ViewGroup>(android.R.id.content)
        val preview = findViewById<BoardView>(R.id.continue_board)
        val target = GameActivity.boardBounds(this, content.width, content.height, appliedSystemBars(findViewById(R.id.root)))
        val overlay = GrowOverlay(this)
        overlay.board.showGame(game, app.settings.checkMistakes)
        content.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        growing = overlay
        preview.visibility = View.INVISIBLE
        overlay.grow(findViewById(R.id.continue_card), preview, target) {
            val origin = IntArray(2).also(content::getLocationOnScreen)
            target.offset(origin[0], origin[1])
            startActivity(
                GameActivity.resume(this, GameStore.Slot.MAIN, boardOnScreen = target),
                ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle(),
            )
        }
    }

    private fun endGrowing() {
        val overlay = growing ?: return
        growing = null
        overlay.stop()
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        findViewById<View>(R.id.continue_board).visibility = View.VISIBLE
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

    companion object {
        const val TAB_PLAY = 0
        const val TAB_LEARN = 1
        const val TAB_RECORDS = 2
        const val TAB_SETTINGS = 3
        private const val STATE_TAB = "tab"
    }
}
