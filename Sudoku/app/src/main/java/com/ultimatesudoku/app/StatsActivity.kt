package com.ultimatesudoku.app

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.ultimatesudoku.app.ui.Dialogs
import com.ultimatesudoku.app.ui.SegmentedView
import com.ultimatesudoku.app.ui.dp
import com.ultimatesudoku.app.ui.formatTime
import com.ultimatesudoku.core.Difficulty
import java.time.LocalDate

class StatsActivity : BaseActivity() {

    private var tab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreen(R.layout.activity_stats, getString(R.string.stats_title))
        tab = savedInstanceState?.getInt(STATE_TAB) ?: 0
        findViewById<SegmentedView>(R.id.tabs).apply {
            options = Difficulty.entries.map { it.title }
            selectedIndex = tab
            onSelect = {
                tab = it
                render()
            }
        }
        findViewById<View>(R.id.reset).setOnClickListener {
            Dialogs.show(
                this,
                getString(R.string.stats_reset_title),
                getString(R.string.stats_reset_message),
                listOf(
                    Dialogs.Action(getString(R.string.stats_reset_confirm), primary = true) {
                        app.stats.reset()
                        render()
                    },
                    Dialogs.Action(getString(R.string.cancel)),
                ),
            )
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, tab)
    }

    private fun render() {
        val r = app.stats.record(Difficulty.entries[tab])
        fun time(ms: Long) = if (ms > 0) formatTime(ms) else "—"
        fill(
            findViewById(R.id.grid),
            listOf(
                r.started.toString() to R.string.stats_started,
                r.won.toString() to R.string.stats_won,
                "${r.winRate}%" to R.string.stats_win_rate,
                r.perfect.toString() to R.string.stats_perfect,
                time(r.bestTimeMs) to R.string.stats_best,
                time(r.averageMs) to R.string.stats_average,
                r.streak.toString() to R.string.stats_streak,
                r.bestStreak.toString() to R.string.stats_best_streak,
            ),
        )
        val stats = app.stats
        fill(
            findViewById(R.id.daily_grid),
            listOf(
                stats.dailyCount.toString() to R.string.stats_daily_count,
                stats.dailyStreak(LocalDate.now()).toString() to R.string.stats_streak,
                stats.bestDailyStreak().toString() to R.string.stats_best_streak,
                "" to 0,
            ),
        )
    }

    /** Lays out stat tiles two per row; a label of 0 leaves an empty slot. */
    private fun fill(grid: LinearLayout, tiles: List<Pair<String, Int>>) {
        grid.removeAllViews()
        for (pair in tiles.chunked(2)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, (value, label) ->
                val tile = layoutInflater.inflate(R.layout.item_stat, row, false)
                if (label == 0) {
                    tile.visibility = View.INVISIBLE
                } else {
                    tile.findViewById<TextView>(R.id.value).text = value
                    tile.findViewById<TextView>(R.id.label).text = getString(label)
                }
                (tile.layoutParams as LinearLayout.LayoutParams).marginStart = if (i == 0) 0 else dp(10)
                row.addView(tile)
            }
            grid.addView(
                row,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(10) },
            )
        }
    }

    private companion object {
        const val STATE_TAB = "tab"
    }
}
