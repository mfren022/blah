package com.ultimatesudoku.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import com.ultimatesudoku.app.data.GameStore
import com.ultimatesudoku.app.ui.BoardView
import com.ultimatesudoku.app.ui.Dialogs
import com.ultimatesudoku.app.ui.NumberPadView
import com.ultimatesudoku.app.ui.Snack
import com.ultimatesudoku.app.ui.ToolButton
import com.ultimatesudoku.app.ui.fitSystemBars
import com.ultimatesudoku.app.ui.tick
import com.ultimatesudoku.core.Difficulty
import com.ultimatesudoku.core.Generator
import com.ultimatesudoku.core.Grid
import com.ultimatesudoku.core.LogicSolver
import com.ultimatesudoku.core.LogicState
import com.ultimatesudoku.core.Rating
import com.ultimatesudoku.core.Solver
import com.ultimatesudoku.core.Step
import com.ultimatesudoku.core.Technique

/**
 * Solves puzzles the player types in: instantly with the exhaustive solver, or one human
 * technique at a time with explanations.
 */
class SolverActivity : BaseActivity() {

    private val clues = IntArray(81)
    private var selected = -1

    /** The full solution, once Solve has run. */
    private var solution: IntArray? = null
    private var analysis: Solver.Analysis? = null
    private var rating: Rating? = null

    /** Step-by-step mode: the logical position and the step on show (not yet applied). */
    private var logicState: LogicState? = null
    private var pendingStep: Step? = null
    private var steps = 0
    private var hardest: Technique? = null

    private var statusTitle = ""
    private var statusText = ""
    private var statusAction: Action? = null
    private var statusIsError = false

    private enum class Action { PLAY, APPLY }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var board: BoardView
    private lateinit var numpad: NumberPadView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(STATE_CLUES)?.let { Grid.parse(it)?.copyInto(clues) }
        selected = savedInstanceState?.getInt(STATE_SELECTED, -1) ?: -1
        inflate()
        showIntro()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CLUES, Grid.format(clues))
        outState.putInt(STATE_SELECTED, selected)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        inflate()
        render()
    }

    private fun inflate() {
        setContentView(R.layout.activity_solver)
        fitSystemBars(findViewById(R.id.root))
        board = findViewById(R.id.board)
        numpad = findViewById(R.id.numpad)
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.more).setOnClickListener { showMenu() }
        board.onCellTap = BoardView.OnCellTapListener { cell ->
            selected = cell
            board.selected = cell
        }
        numpad.onDigit = NumberPadView.OnDigitListener(::enterClue)
        findViewById<ToolButton>(R.id.tool_erase).setOnClickListener { enterClue(0) }
        findViewById<ToolButton>(R.id.tool_clear).setOnClickListener { clearAll() }
        findViewById<ToolButton>(R.id.tool_step).setOnClickListener { step() }
        findViewById<ToolButton>(R.id.tool_solve).setOnClickListener { solve() }
        findViewById<TextView>(R.id.status_action).setOnClickListener { onStatusAction() }
    }

    // ---- Editing ------------------------------------------------------------------------------

    private fun enterClue(digit: Int) {
        if (selected < 0) {
            Snack.show(this, getString(R.string.select_cell_first))
            return
        }
        resetResults()
        clues[selected] = if (digit != 0 && clues[selected] == digit) 0 else digit
        board.tick(app.settings.haptics)
        if (Grid.isConsistent(clues)) showIntro() else showInvalid()
    }

    private fun clearAll() {
        clues.fill(0)
        selected = -1
        resetResults()
        showIntro()
    }

    private fun load(values: IntArray) {
        values.copyInto(clues)
        selected = -1
        resetResults()
        if (Grid.isConsistent(clues)) showIntro() else showInvalid()
    }

    private fun resetResults() {
        solution = null
        analysis = null
        rating = null
        logicState = null
        pendingStep = null
        steps = 0
        hardest = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> enterClue(keyCode - KeyEvent.KEYCODE_0)
            in KeyEvent.KEYCODE_NUMPAD_1..KeyEvent.KEYCODE_NUMPAD_9 -> enterClue(keyCode - KeyEvent.KEYCODE_NUMPAD_0)
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> enterClue(0)
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    // ---- Solving ------------------------------------------------------------------------------

    /** Runs the exhaustive solver (off the main thread) and grades unique puzzles. */
    private fun analyze(then: (Solver.Analysis) -> Unit) {
        analysis?.let {
            then(it)
            return
        }
        val puzzle = clues.copyOf()
        Thread {
            val start = System.nanoTime()
            val result = Solver().analyze(puzzle)
            val micros = (System.nanoTime() - start) / 1000
            val graded = if (result.status == Solver.Analysis.Status.UNIQUE) LogicSolver().rate(puzzle) else null
            handler.post {
                if (isDestroyed || !puzzle.contentEquals(clues)) return@post
                analysis = result
                rating = graded
                solveMicros = micros
                then(result)
            }
        }.start()
    }

    private var solveMicros = 0L

    private fun solve() {
        if (!Grid.isConsistent(clues)) {
            showInvalid()
            return
        }
        logicState = null
        pendingStep = null
        analyze { result ->
            when (result.status) {
                Solver.Analysis.Status.INVALID -> showInvalid()
                Solver.Analysis.Status.NO_SOLUTION ->
                    setStatus(getString(R.string.solver_none_title), getString(R.string.solver_none_text), error = true)
                Solver.Analysis.Status.TOO_HARD ->
                    setStatus(getString(R.string.solver_too_hard_title), getString(R.string.solver_too_hard_text), error = true)
                Solver.Analysis.Status.MULTIPLE -> {
                    solution = result.solution
                    setStatus(getString(R.string.solver_multiple_title), getString(R.string.solver_multiple_text))
                }
                Solver.Analysis.Status.UNIQUE -> {
                    solution = result.solution
                    val time = "%.1f".format(solveMicros / 1000.0)
                    val r = rating
                    val text = if (r == null || !r.solved) {
                        getString(R.string.solver_unique_text_stuck, time)
                    } else {
                        getString(
                            R.string.solver_unique_text,
                            time,
                            Generator.classify(r, clueCount()).title,
                            if (r.tier <= 1) getString(R.string.solver_singles) else r.hardest!!.title,
                        )
                    }
                    setStatus(getString(R.string.solver_unique_title), text, action = Action.PLAY)
                }
            }
        }
    }

    private fun step() {
        if (!Grid.isConsistent(clues)) {
            showInvalid()
            return
        }
        solution = null
        analyze { result ->
            when (result.status) {
                Solver.Analysis.Status.INVALID -> showInvalid()
                Solver.Analysis.Status.NO_SOLUTION ->
                    setStatus(getString(R.string.solver_none_title), getString(R.string.solver_none_text), error = true)
                else -> nextStep(assumeUnique = result.status == Solver.Analysis.Status.UNIQUE)
            }
        }
    }

    private fun nextStep(assumeUnique: Boolean) {
        val state = logicState ?: LogicState(clues).also { logicState = it }
        pendingStep?.let { applyPending(it, state) }
        if (state.isSolved) {
            showLogicDone()
            return
        }
        val next = LogicSolver(assumeUnique).nextStep(state)
        pendingStep = next
        if (next == null) {
            setStatus(getString(R.string.solver_stuck_title), getString(R.string.solver_stuck_text))
            return
        }
        setStatus(next.technique.title, next.description, action = Action.APPLY)
    }

    private fun applyPending(step: Step, state: LogicState) {
        state.apply(step)
        steps++
        if (hardest == null || step.technique.ordinal > hardest!!.ordinal) hardest = step.technique
        pendingStep = null
    }

    private fun showLogicDone() {
        setStatus(
            getString(R.string.solver_done_title),
            getString(R.string.solver_done_text, steps, hardest?.title ?: Technique.NAKED_SINGLE.title),
        )
    }

    private fun onStatusAction() {
        when (statusAction) {
            Action.APPLY -> {
                val state = logicState ?: return
                pendingStep?.let { applyPending(it, state) }
                if (state.isSolved) {
                    showLogicDone()
                } else {
                    setStatus(getString(R.string.solver_applied_title), getString(R.string.solver_applied_text, steps))
                }
            }
            Action.PLAY -> play()
            null -> Unit
        }
    }

    private fun play() {
        val result = analysis ?: return
        val answer = result.solution ?: return
        if (result.status != Solver.Analysis.Status.UNIQUE) {
            Snack.show(this, getString(R.string.solver_play_needs_unique))
            return
        }
        val difficulty = rating?.let { Generator.classify(it, clueCount()) } ?: Difficulty.EXTREME
        val start = { startActivity(GameActivity.newCustom(this, clues, answer, difficulty)) }
        val current = app.store.game(GameStore.Slot.MAIN)
        if (current == null || !current.isPlaying || current.values.contentEquals(current.givens)) {
            start()
        } else {
            Dialogs.show(
                this,
                getString(R.string.replace_title),
                getString(R.string.replace_message, current.difficulty.title),
                listOf(
                    Dialogs.Action(getString(R.string.replace_confirm), primary = true) { start() },
                    Dialogs.Action(getString(R.string.cancel)),
                ),
            )
        }
    }

    private fun clueCount() = clues.count { it != 0 }

    private fun showMenu() {
        Dialogs.menu(
            this,
            getString(R.string.solver_title),
            listOf(
                getString(R.string.solver_menu_paste) to {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)
                    val parsed = text?.let { Grid.parse(it.toString()) }
                    if (parsed == null) Snack.show(this, getString(R.string.solver_paste_failed)) else load(parsed)
                },
                getString(R.string.solver_menu_copy) to {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Sudoku", Grid.format(clues)))
                    Snack.show(this, getString(R.string.copied))
                },
            ),
        )
    }

    // ---- Display ------------------------------------------------------------------------------

    private fun showIntro() {
        setStatus(getString(R.string.solver_intro_title), getString(R.string.solver_intro_text))
    }

    private fun showInvalid() {
        setStatus(getString(R.string.solver_invalid_title), getString(R.string.solver_invalid_text), error = true)
    }

    private fun setStatus(title: String, text: String, action: Action? = null, error: Boolean = false) {
        statusTitle = title
        statusText = text
        statusAction = action
        statusIsError = error
        render()
    }

    private fun render() {
        val state = logicState
        val shown = solution
        val values = when {
            shown != null -> shown
            state != null -> state.values
            else -> clues
        }
        val notes = state?.candidates ?: IntArray(81)
        board.setContent(clues.copyOf(), values.copyOf(), notes.copyOf(), BooleanArray(81))
        board.selected = selected
        val step = pendingStep
        if (step != null && state != null) board.showStep(step, state.candidates.copyOf()) else board.clearHint()

        findViewById<TextView>(R.id.status_title).apply {
            text = statusTitle
            setTextColor(if (statusIsError) palette.error else palette.userDigit)
        }
        findViewById<View>(R.id.status_mark).setBackgroundColor(if (statusIsError) palette.error else palette.accent)
        findViewById<TextView>(R.id.status_text).text = statusText
        findViewById<TextView>(R.id.status_action).apply {
            visibility = if (statusAction == null) View.GONE else View.VISIBLE
            text = getString(if (statusAction == Action.PLAY) R.string.solver_play else R.string.hint_apply)
        }
    }

    private companion object {
        const val STATE_CLUES = "clues"
        const val STATE_SELECTED = "selected"
    }
}
