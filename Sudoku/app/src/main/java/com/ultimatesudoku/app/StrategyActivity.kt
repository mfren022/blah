package com.ultimatesudoku.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ultimatesudoku.app.ui.BoardView
import com.ultimatesudoku.app.ui.dp
import com.ultimatesudoku.core.Lesson
import com.ultimatesudoku.core.Strategies
import com.ultimatesudoku.core.Strategy
import com.ultimatesudoku.core.Technique

/**
 * One strategy: what it does, a worked example and then how it works and how to spot it. Each example
 * starts as a challenge, where to look and what to find, answered by tapping a cell (or skipped with Show
 * me); then the walkthrough builds the step up on the board, a frame at a time, to its result. Finding
 * the answer marks the strategy as practised.
 */
class StrategyActivity : BaseActivity() {

    private lateinit var strategy: Strategy
    private lateinit var board: BoardView
    private lateinit var stage: TextView
    private lateinit var feedback: TextView
    private lateinit var frameText: TextView
    private lateinit var stepBack: TextView
    private lateinit var stepNext: TextView

    private var exampleIndex = 0

    /** The frame on show: -1 for the challenge, then the walkthrough's frames from 0. */
    private var frame = -1

    /** The cell the player found, or -1 if they haven't (yet, or asked to be shown). */
    private var found = -1

    private val lesson: Lesson get() = strategy.examples[exampleIndex].lesson

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra(EXTRA_TECHNIQUE)
        strategy = Strategies.of(Technique.entries.firstOrNull { it.name == name } ?: Technique.FULL_HOUSE)
        setScreen(R.layout.activity_strategy, getString(R.string.strategies_title))
        if (savedInstanceState != null) {
            exampleIndex = savedInstanceState.getInt(STATE_EXAMPLE).coerceIn(strategy.examples.indices)
            frame = savedInstanceState.getInt(STATE_FRAME, -1)
            found = savedInstanceState.getInt(STATE_FOUND, -1)
        }
        board = findViewById(R.id.board)
        stage = findViewById(R.id.stage)
        feedback = findViewById(R.id.feedback)
        frameText = findViewById(R.id.frame_text)
        stepBack = findViewById(R.id.step_back)
        stepNext = findViewById(R.id.step_next)

        showStrategy()
        arrangeExample()
        board.tapsOnRelease = true
        board.onCellTap = BoardView.OnCellTapListener(::answer)
        stepBack.setOnClickListener { show(frame - 1) }
        stepNext.setOnClickListener { if (frame == lesson.frames.lastIndex) nextExample() else show(frame + 1) }
        findViewById<View>(R.id.another).setOnClickListener { nextExample() }
        showExample()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_EXAMPLE, exampleIndex)
        outState.putInt(STATE_FRAME, frame)
        outState.putInt(STATE_FOUND, found)
    }

    /** The words around the example, and the way on to the strategies either side. */
    private fun showStrategy() {
        val technique = strategy.technique
        val tier = Strategies.TIERS[technique.tier - 1]
        findViewById<TextView>(R.id.kicker).text = getString(R.string.strategy_kicker, tier, strategy.number, Strategies.ALL.size)
        findViewById<TextView>(R.id.strategy_title).text = technique.title
        findViewById<TextView>(R.id.summary).text = strategy.summary
        findViewById<TextView>(R.id.how).text = strategy.howItWorks
        findViewById<TextView>(R.id.spot).text = strategy.howToSpot
        neighbour(R.id.previous, Strategies.ALL.getOrNull(technique.ordinal - 1), R.string.strategy_previous_label, R.string.strategy_previous)
        neighbour(R.id.following, Strategies.ALL.getOrNull(technique.ordinal + 1), R.string.strategy_following_label, R.string.strategy_following)
    }

    private fun neighbour(id: Int, other: Strategy?, label: Int, description: Int) {
        val button = findViewById<TextView>(id)
        if (other == null) {
            button.visibility = View.INVISIBLE
            return
        }
        val title = other.technique.title
        button.text = getString(label, title)
        button.contentDescription = getString(description, title)
        button.setOnClickListener {
            startActivity(open(this, other.technique))
            finish()
        }
    }

    /**
     * Sizes the board and places the walkthrough: beneath a board as wide as the page, or on a wide
     * screen beside a board as tall as the screen allows, so that both are in view together.
     */
    private fun arrangeExample() {
        val metrics = resources.displayMetrics
        val page = metrics.widthPixels - dp(40)
        val wide = resources.configuration.screenWidthDp >= 600
        val size = if (wide) {
            minOf(metrics.heightPixels - dp(110), page / 2, dp(480))
        } else {
            minOf(page, metrics.heightPixels - dp(160), dp(480))
        }.coerceAtLeast(dp(220))
        board.layoutParams = board.layoutParams.apply {
            width = size
            height = size
        }
        if (wide) {
            findViewById<LinearLayout>(R.id.example_body).orientation = LinearLayout.HORIZONTAL
            findViewById<View>(R.id.walkthrough).layoutParams =
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(24) }
            (stage.layoutParams as ViewGroup.MarginLayoutParams).topMargin = 0
        }
    }

    /** Puts the current example on the board, at the frame on show. */
    private fun showExample() {
        val example = strategy.examples[exampleIndex]
        val notes = if (strategy.showsCandidates) example.candidates else IntArray(81)
        board.setContent(example.givens, example.values, notes, BooleanArray(81))
        findViewById<TextView>(R.id.example_header).text =
            getString(R.string.strategy_example, exampleIndex + 1, strategy.examples.size)
        show(frame)
    }

    private fun show(index: Int) {
        frame = index.coerceIn(-1, lesson.frames.lastIndex)
        val current = if (frame < 0) lesson.challenge else lesson.frames[frame]
        board.showStep(current.overlay, current.candidates)
        board.contentDescription = getString(R.string.strategy_board, current.text)
        stage.text = if (frame < 0) {
            getString(R.string.strategy_try)
        } else {
            getString(R.string.strategy_step, frame + 1, lesson.frames.size)
        }
        frameText.text = current.text
        if (frame >= 0 && found >= 0) {
            say(getString(R.string.strategy_right, lesson.outcome(found)), palette.userDigit)
        } else {
            feedback.visibility = View.GONE
        }
        stepBack.visibility = if (frame < 0) View.INVISIBLE else View.VISIBLE
        stepNext.setText(
            when (frame) {
                -1 -> R.string.strategy_show_me
                lesson.frames.lastIndex -> R.string.strategy_try_another
                else -> R.string.strategy_next
            },
        )
    }

    /** A tap on the board answers the challenge. */
    private fun answer(cell: Int) {
        if (frame >= 0) return
        if (cell in lesson.answers) {
            found = cell
            app.strategies.practise(strategy.technique)
            show(0)
        } else {
            board.markWrong(cell)
            say(getString(R.string.strategy_wrong), palette.error)
        }
    }

    private fun say(text: String, color: Int) {
        feedback.text = text
        feedback.setTextColor(color)
        feedback.visibility = View.VISIBLE
    }

    /** On to the next example, from its challenge, with the board back in view. */
    private fun nextExample() {
        exampleIndex = (exampleIndex + 1) % strategy.examples.size
        frame = -1
        found = -1
        showExample()
        val scroll = findViewById<ScrollView>(R.id.scroll)
        val top = findViewById<View>(R.id.example_row).top
        if (scroll.scrollY > top) scroll.smoothScrollTo(0, top)
    }

    companion object {
        private const val EXTRA_TECHNIQUE = "technique"
        private const val STATE_EXAMPLE = "example"
        private const val STATE_FRAME = "frame"
        private const val STATE_FOUND = "found"

        fun open(context: Context, technique: Technique): Intent =
            Intent(context, StrategyActivity::class.java).putExtra(EXTRA_TECHNIQUE, technique.name)
    }
}
