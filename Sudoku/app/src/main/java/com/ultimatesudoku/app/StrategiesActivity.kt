package com.ultimatesudoku.app

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ultimatesudoku.app.ui.SectionHeader
import com.ultimatesudoku.app.ui.dp
import com.ultimatesudoku.core.Strategies
import com.ultimatesudoku.core.Strategy

/** Every solving technique, simplest first and grouped by tier; each one opens its lesson. */
class StrategiesActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreen(R.layout.activity_strategies, getString(R.string.strategies_title))
    }

    override fun onResume() {
        super.onResume()
        // Fresh marks, after a strategy has been practised.
        showStrategies()
    }

    private fun showStrategies() {
        val list = findViewById<LinearLayout>(R.id.strategies)
        list.removeAllViews()
        val practised = app.strategies.practised()
        for ((tier, strategies) in Strategies.ALL.groupBy { it.technique.tier }) {
            val header = SectionHeader(this).apply { text = Strategies.TIERS[tier - 1] }
            list.addView(
                header,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(28)
                    bottomMargin = dp(4)
                },
            )
            for (strategy in strategies) list.addView(row(list, strategy, strategy.technique in practised))
        }
    }

    private fun row(list: ViewGroup, strategy: Strategy, practised: Boolean): View {
        val row = layoutInflater.inflate(R.layout.item_strategy, list, false)
        val title = strategy.technique.title
        row.findViewById<TextView>(R.id.number).apply {
            text = "%02d".format(strategy.number)
            if (practised) {
                setBackgroundColor(palette.accent)
                setTextColor(palette.onAccent)
            }
        }
        row.findViewById<TextView>(R.id.name).text = title
        row.findViewById<TextView>(R.id.summary).text = strategy.summary
        row.findViewById<ImageView>(R.id.state).setColorFilter(palette.inkMuted)
        val description = if (practised) R.string.strategy_row_practised else R.string.strategy_row
        row.contentDescription = getString(description, strategy.number, title, strategy.summary)
        row.setOnClickListener { startActivity(StrategyActivity.open(this, strategy.technique)) }
        return row
    }
}
