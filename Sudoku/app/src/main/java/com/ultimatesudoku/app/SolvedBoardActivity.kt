package com.ultimatesudoku.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.ultimatesudoku.app.ui.BoardView
import com.ultimatesudoku.app.ui.Dialogs
import com.ultimatesudoku.app.ui.SegmentedView
import com.ultimatesudoku.app.ui.Snack
import com.ultimatesudoku.app.ui.formatTime
import com.ultimatesudoku.app.ui.modeName
import com.ultimatesudoku.core.Grid
import com.ultimatesudoku.core.SolvedBoard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** One solved board: its starting combination (or solution), time and results, and ways to revisit it. */
class SolvedBoardActivity : BaseActivity() {

    private var board: SolvedBoard? = null
    private var showSolution = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreen(R.layout.activity_solved_board, getString(R.string.board_title))
        showSolution = savedInstanceState?.getBoolean(STATE_SOLUTION) ?: false
        val id = intent.getLongExtra(EXTRA_ID, -1)
        app.io.execute {
            val loaded = app.history.get(id)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (loaded == null) finish() else bind(loaded)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SOLUTION, showSolution)
    }

    private fun bind(b: SolvedBoard) {
        board = b
        findViewById<TextView>(R.id.kicker).text = "${b.difficulty.title} · ${modeName(b.mode, b.tag)}"
        findViewById<TextView>(R.id.date).text = Instant.ofEpochMilli(b.completedAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT))

        tile(R.id.tile_time, formatTime(b.timeMs), R.string.board_time)
        tile(R.id.tile_mistakes, b.mistakes.toString(), R.string.board_mistakes)
        tile(R.id.tile_hints, b.hints.toString(), R.string.board_hints)
        tile(R.id.tile_clues, b.clues.toString(), R.string.board_clues)

        findViewById<SegmentedView>(R.id.view_toggle).apply {
            options = listOf(getString(R.string.board_start), getString(R.string.board_solution))
            selectedIndex = if (showSolution) 1 else 0
            onSelect = {
                showSolution = it == 1
                renderBoard()
            }
        }
        renderBoard()

        findViewById<View>(R.id.play_again).setOnClickListener {
            startMainGame(GameActivity.newCustom(this, b.givens, b.solution, b.difficulty))
        }
        findViewById<View>(R.id.open_solver).setOnClickListener {
            startActivity(SolverActivity.open(this, b.givens))
        }
        findViewById<View>(R.id.more).apply {
            visibility = View.VISIBLE
            setOnClickListener { showMenu(b) }
        }
    }

    private fun renderBoard() {
        val b = board ?: return
        findViewById<BoardView>(R.id.board).apply {
            isEnabled = false
            highlightPeers = false
            highlightSame = false
            setContent(b.givens, if (showSolution) b.solution else b.givens, IntArray(81), BooleanArray(81))
        }
    }

    private fun tile(id: Int, value: String, label: Int) {
        val tile = findViewById<View>(id)
        tile.findViewById<TextView>(R.id.value).text = value
        tile.findViewById<TextView>(R.id.label).setText(label)
    }

    private fun showMenu(b: SolvedBoard) {
        Dialogs.menu(
            this,
            getString(R.string.board_title),
            listOf(
                getString(R.string.board_menu_copy) to {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Sudoku", Grid.format(b.givens)))
                    Snack.show(this, getString(R.string.copied))
                },
                getString(R.string.board_menu_delete) to {
                    Dialogs.show(
                        this,
                        getString(R.string.board_delete_title),
                        getString(R.string.board_delete_message),
                        listOf(
                            Dialogs.Action(getString(R.string.board_delete_confirm), primary = true) {
                                app.io.execute {
                                    app.history.delete(b.id)
                                    runOnUiThread { finish() }
                                }
                            },
                            Dialogs.Action(getString(R.string.cancel)),
                        ),
                    )
                },
            ),
        )
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val STATE_SOLUTION = "solution"

        fun open(context: Context, id: Long): Intent =
            Intent(context, SolvedBoardActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
