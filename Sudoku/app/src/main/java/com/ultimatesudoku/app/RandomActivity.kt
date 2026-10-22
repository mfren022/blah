package com.ultimatesudoku.app

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.ultimatesudoku.app.ui.LevelView
import com.ultimatesudoku.app.ui.formatTime
import com.ultimatesudoku.core.Difficulty

/** Random mode: pick a difficulty and get a freshly generated puzzle. */
class RandomActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreen(R.layout.activity_random, getString(R.string.random_title))
    }

    override fun onResume() {
        super.onResume()
        val rows = findViewById<LinearLayout>(R.id.rows)
        rows.removeAllViews()
        for (difficulty in Difficulty.entries) {
            val row = layoutInflater.inflate(R.layout.item_difficulty, rows, false)
            row.findViewById<LevelView>(R.id.level).level = difficulty.ordinal + 1
            row.findViewById<TextView>(R.id.name).text = difficulty.title
            row.findViewById<TextView>(R.id.blurb).text = difficulty.blurb
            val best = app.stats.record(difficulty).bestTimeMs
            row.findViewById<TextView>(R.id.best).text =
                if (best > 0) formatTime(best) else getString(R.string.random_no_best)
            row.contentDescription = "${difficulty.title}. ${difficulty.blurb}"
            row.setOnClickListener { startMainGame(GameActivity.newRandom(this, difficulty)) }
            rows.addView(row)
        }
    }
}
