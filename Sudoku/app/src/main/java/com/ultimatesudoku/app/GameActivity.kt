package com.ultimatesudoku.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.ultimatesudoku.app.data.GameStore
import com.ultimatesudoku.app.data.Settings
import com.ultimatesudoku.app.ui.BoardView
import com.ultimatesudoku.app.ui.ConfettiView
import com.ultimatesudoku.app.ui.Dialogs
import com.ultimatesudoku.app.ui.Fonts
import com.ultimatesudoku.app.ui.NumberPadView
import com.ultimatesudoku.app.ui.Snack
import com.ultimatesudoku.app.ui.ToolButton
import com.ultimatesudoku.app.ui.boundsWithin
import com.ultimatesudoku.app.ui.buzz
import com.ultimatesudoku.app.ui.dp
import com.ultimatesudoku.app.ui.fitSystemBars
import com.ultimatesudoku.app.ui.formatTime
import com.ultimatesudoku.app.ui.modeName
import com.ultimatesudoku.app.ui.tick
import com.ultimatesudoku.core.Daily
import com.ultimatesudoku.core.Difficulty
import com.ultimatesudoku.core.Game
import com.ultimatesudoku.core.GameMode
import com.ultimatesudoku.core.Generator
import com.ultimatesudoku.core.Grid
import com.ultimatesudoku.core.LogicSolver
import com.ultimatesudoku.core.SolvedBoard
import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random

/** Plays one puzzle: entry, notes, undo, hints, timer, mistakes and the end of the game. */
class GameActivity : BaseActivity() {

    private lateinit var slot: GameStore.Slot
    private var game: Game? = null
    private val logic = LogicSolver()
    private val handler = Handler(Looper.getMainLooper())
    private val settings: Settings get() = app.settings

    private var selected = -1
    private var notesMode = false
    private var userPaused = false
    private var hint: Game.Hint? = null
    private var foreground = false
    private var timing = false
    private var lastTick = 0L

    private lateinit var board: BoardView
    private lateinit var numpad: NumberPadView
    private lateinit var title: TextView
    private lateinit var timer: TextView
    private lateinit var modeLabel: TextView
    private lateinit var mistakes: TextView
    private lateinit var hints: TextView
    private lateinit var pause: ImageButton
    private lateinit var resume: View
    private lateinit var controls: View
    private lateinit var hintPanel: View
    private lateinit var toolUndo: ToolButton
    private lateinit var toolErase: ToolButton
    private lateinit var toolNotes: ToolButton
    private lateinit var toolHint: ToolButton
    private lateinit var toolMore: ToolButton
    private lateinit var winOverlay: View

    private val ticker = object : Runnable {
        override fun run() {
            accumulateTime()
            updateTimerText()
            scheduleTick()
        }
    }
    private val saver = Runnable { save() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        slot = GameStore.Slot.valueOf(intent.getStringExtra(EXTRA_SLOT) ?: GameStore.Slot.MAIN.name)
        if (savedInstanceState != null) {
            selected = savedInstanceState.getInt(STATE_SELECTED, -1)
            notesMode = savedInstanceState.getBoolean(STATE_NOTES)
            userPaused = savedInstanceState.getBoolean(STATE_PAUSED)
        }
        inflate()
        if (savedInstanceState == null) {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Rect>(EXTRA_BOARD_ON_SCREEN)?.let(::arriveAt)
        }

        val kind = intent.getStringExtra(EXTRA_NEW)
        val store = app.store
        when {
            savedInstanceState == null && kind != null -> startNew(kind)
            store.isGenerating && store.generatingSlot == slot -> {
                showGenerating()
                store.listen(::onGameReady)
            }
            else -> {
                val saved = store.game(slot)
                if (saved == null) {
                    finish()
                    return
                }
                onGameReady(saved)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SELECTED, selected)
        outState.putBoolean(STATE_NOTES, notesMode)
        outState.putBoolean(STATE_PAUSED, userPaused)
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        if (settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        updateTimer()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        foreground = false
        updateTimer()
        save()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (game == null) app.store.stopListening()
    }

    @Deprecated("Still the back hook for apps not opted in to predictive back")
    override fun onBackPressed() {
        if (hint != null) {
            closeHint()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation is handled here rather than by recreating, so a puzzle being generated keeps
        // its screen. Re-inflate to pick up the portrait or landscape layout.
        inflate()
        val g = game
        if (g == null) {
            showGenerating()
            return
        }
        refresh()
        hint?.let { displayHint(g, it) }
        if (g.status == Game.Status.WON) showWin(g, newBest = false, celebrate = false)
    }

    // ---- Setup --------------------------------------------------------------------------------

    private fun inflate() {
        setContentView(R.layout.activity_game)
        fitSystemBars(findViewById(R.id.root))
        fitSystemBars(findViewById(R.id.win_content))
        board = findViewById(R.id.board)
        numpad = findViewById(R.id.numpad)
        title = findViewById(R.id.title)
        timer = findViewById(R.id.timer)
        modeLabel = findViewById(R.id.mode_label)
        mistakes = findViewById(R.id.mistakes)
        hints = findViewById(R.id.hints)
        pause = findViewById(R.id.pause)
        resume = findViewById(R.id.resume)
        controls = findViewById(R.id.controls)
        hintPanel = findViewById(R.id.hint_panel)
        toolUndo = findViewById(R.id.tool_undo)
        toolErase = findViewById(R.id.tool_erase)
        toolNotes = findViewById(R.id.tool_notes)
        toolHint = findViewById(R.id.tool_hint)
        toolMore = findViewById(R.id.tool_more)
        winOverlay = findViewById(R.id.win_overlay)

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        board.onCellTap = BoardView.OnCellTapListener(::select)
        numpad.onDigit = NumberPadView.OnDigitListener(::enterDigit)
        toolUndo.setOnClickListener { undo() }
        toolErase.setOnClickListener { erase() }
        toolNotes.setOnClickListener { toggleNotes() }
        toolHint.setOnClickListener { showHint() }
        toolMore.setOnClickListener { showMenu() }
        pause.setOnClickListener { togglePause() }
        resume.setOnClickListener { togglePause() }
        findViewById<View>(R.id.hint_close).setOnClickListener { closeHint() }
        findViewById<View>(R.id.hint_apply).setOnClickListener { applyHint() }
    }

    /**
     * Arriving from the home screen's preview, which has just grown to [boardOnScreen]: the board
     * starts there (normally exactly where it is laid out) and the rest of the screen fades in around it.
     */
    private fun arriveAt(boardOnScreen: Rect) {
        val root = findViewById<ViewGroup>(R.id.root)
        val boardArea = findViewById<View>(R.id.board_area)
        val around = (0 until root.childCount).map(root::getChildAt).filter { it !== boardArea }
        for (view in around) view.alpha = 0f
        board.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                board.viewTreeObserver.removeOnPreDrawListener(this)
                val at = IntArray(2).also(board::getLocationOnScreen)
                val off = abs(at[0] - boardOnScreen.left) > 1 || abs(at[1] - boardOnScreen.top) > 1 ||
                    abs(board.width - boardOnScreen.width()) > 1
                if (off && board.width > 0) {
                    // It landed somewhere else (the window moved, say): carry it on from there.
                    board.pivotX = 0f
                    board.pivotY = 0f
                    board.scaleX = boardOnScreen.width() / board.width.toFloat()
                    board.scaleY = board.scaleX
                    board.translationX = (boardOnScreen.left - at[0]).toFloat()
                    board.translationY = (boardOnScreen.top - at[1]).toFloat()
                    board.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
                        .setDuration(220).setInterpolator(DecelerateInterpolator()).start()
                }
                for (view in around) {
                    view.translationY = if (view.bottom <= boardArea.top) -dp(8f) else dp(16f)
                    view.animate().alpha(1f).translationY(0f)
                        .setDuration(260).setInterpolator(DecelerateInterpolator()).start()
                }
                return true
            }
        })
    }

    private fun startNew(kind: String) {
        val store = app.store
        when (kind) {
            KIND_CUSTOM -> {
                val g = Game(
                    GameMode.CUSTOM,
                    Difficulty.valueOf(intent.getStringExtra(EXTRA_DIFFICULTY)!!),
                    Grid.parse(intent.getStringExtra(EXTRA_GIVENS)!!)!!,
                    Grid.parse(intent.getStringExtra(EXTRA_SOLUTION)!!)!!,
                )
                store.start(slot, g)
                onGameReady(g)
            }
            KIND_DAILY -> {
                showGenerating()
                val today = LocalDate.now()
                store.generate(slot, {
                    val p = Daily.puzzle(today.year, today.monthValue, today.dayOfMonth, today.dayOfWeek.value)
                    Game(GameMode.DAILY, p.difficulty, p.givens, p.solution, today.toString())
                }, ::onGameReady)
            }
            else -> {
                showGenerating()
                val difficulty = Difficulty.valueOf(intent.getStringExtra(EXTRA_DIFFICULTY)!!)
                store.generate(slot, {
                    val p = Generator(Random(System.nanoTime())).generate(difficulty)
                    Game(GameMode.RANDOM, p.difficulty, p.givens, p.solution)
                }, ::onGameReady)
            }
        }
    }

    private fun showGenerating() {
        board.message = getString(R.string.generating).uppercase()
        title.text = intent.getStringExtra(EXTRA_DIFFICULTY)?.let { Difficulty.valueOf(it).title }
            ?: Daily.difficulty(LocalDate.now().dayOfWeek.value).title
        setControlsEnabled(false)
    }

    private fun onGameReady(g: Game) {
        if (isFinishing || isDestroyed) return
        game = g
        board.message = null
        if (selected !in 0 until 81) selected = -1
        setControlsEnabled(true)
        refresh()
        updateTimer()
        when (g.status) {
            Game.Status.WON -> showWin(g, newBest = false, celebrate = false)
            Game.Status.LOST -> showGameOver(g)
            else -> Unit
        }
    }

    // ---- Input --------------------------------------------------------------------------------

    private fun canPlay(): Boolean = game?.isPlaying == true && !userPaused

    private fun select(cell: Int) {
        if (!canPlay()) return
        if (hint != null) closeHint()
        selected = cell
        board.selected = cell
    }

    private fun enterDigit(digit: Int) {
        val g = game ?: return
        if (!canPlay()) return
        if (hint != null) closeHint()
        if (selected < 0) {
            Snack.show(this, getString(R.string.select_cell_first))
            return
        }
        if (notesMode) {
            if (g.toggleNote(selected, digit)) board.tick(settings.haptics)
            refresh()
            scheduleSave()
            return
        }
        when (g.enter(selected, digit, settings.autoRemoveNotes, settings.checkMistakes)) {
            Game.Entry.IGNORED -> return
            Game.Entry.WRONG -> {
                board.buzz(settings.haptics)
                if (settings.limitMistakes && !g.limitWaived && g.mistakes >= Settings.MISTAKE_LIMIT) {
                    refresh()
                    gameOver()
                    return
                }
            }
            Game.Entry.PLACED -> {
                board.tick(settings.haptics)
                flashCompletedUnits(g, selected)
                if (g.isFull) Snack.show(this, getString(R.string.board_incorrect))
            }
            Game.Entry.COMPLETED -> {
                refresh()
                win()
                return
            }
        }
        refresh()
        scheduleSave()
    }

    private fun erase() {
        val g = game ?: return
        if (!canPlay() || selected < 0) return
        if (hint != null) closeHint()
        if (g.erase(selected)) {
            board.tick(settings.haptics)
            refresh()
            scheduleSave()
        }
    }

    private fun undo() {
        val g = game ?: return
        if (!canPlay()) return
        if (hint != null) closeHint()
        val cell = g.undo()
        if (cell >= 0) {
            selected = cell
            board.tick(settings.haptics)
            refresh()
            scheduleSave()
        }
    }

    private fun toggleNotes() {
        if (!canPlay()) return
        notesMode = !notesMode
        refresh()
    }

    private fun togglePause() {
        val g = game ?: return
        if (!g.isPlaying) return
        if (hint != null) closeHint()
        userPaused = !userPaused
        updateTimer()
        refresh()
    }

    private fun flashCompletedUnits(g: Game, cell: Int) {
        val done = Grid.UNITS_OF[cell]
            .filter { unit -> Grid.UNITS[unit].all { g.values[it] == g.solution[it] } }
            .flatMap { Grid.UNITS[it].toList() }
        board.flash(done.toSet())
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (game == null) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> enterDigit(keyCode - KeyEvent.KEYCODE_0)
            in KeyEvent.KEYCODE_NUMPAD_1..KeyEvent.KEYCODE_NUMPAD_9 -> enterDigit(keyCode - KeyEvent.KEYCODE_NUMPAD_0)
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> erase()
            KeyEvent.KEYCODE_DPAD_UP -> moveSelection(-1, 0)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveSelection(1, 0)
            KeyEvent.KEYCODE_DPAD_LEFT -> moveSelection(0, -1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveSelection(0, 1)
            KeyEvent.KEYCODE_N -> toggleNotes()
            KeyEvent.KEYCODE_H -> showHint()
            KeyEvent.KEYCODE_SPACE -> togglePause()
            KeyEvent.KEYCODE_Z -> if (event.isCtrlPressed) undo() else return super.onKeyDown(keyCode, event)
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun moveSelection(dRow: Int, dCol: Int) {
        val start = if (selected < 0) 40 else selected
        val row = (Grid.row(start) + dRow + 9) % 9
        val col = (Grid.col(start) + dCol + 9) % 9
        select(Grid.cell(row, col))
    }

    // ---- Hints and menu -----------------------------------------------------------------------

    private fun showHint() {
        val g = game ?: return
        if (!canPlay()) return
        val h = g.findHint(logic, selected) ?: return
        g.countHint()
        hint = h
        displayHint(g, h)
        refresh()
        scheduleSave()
    }

    private fun displayHint(g: Game, h: Game.Hint) {
        val (heading, text) = when (h) {
            is Game.Hint.Mistake -> {
                board.showFocus(h.cell, 0, mistake = true)
                getString(R.string.hint_mistake_title) to
                    getString(R.string.hint_mistake_text, g.values[h.cell], Grid.cellName(h.cell))
            }
            is Game.Hint.Logic -> {
                board.showStep(h.step, g.hintCandidates())
                h.step.technique.title to h.step.description
            }
            is Game.Hint.Reveal -> {
                board.showFocus(h.cell, h.digit, mistake = false)
                getString(R.string.hint_reveal_title) to
                    getString(R.string.hint_reveal_text, Grid.cellName(h.cell), h.digit)
            }
        }
        findViewById<TextView>(R.id.hint_title).text = heading
        findViewById<TextView>(R.id.hint_text).text = text
        controls.visibility = View.GONE
        hintPanel.visibility = View.VISIBLE
    }

    private fun closeHint() {
        hint = null
        board.clearHint()
        hintPanel.visibility = View.GONE
        controls.visibility = View.VISIBLE
    }

    private fun applyHint() {
        val g = game ?: return
        val h = hint ?: return
        closeHint()
        g.applyHint(h, settings.autoRemoveNotes)
        selected = when (h) {
            is Game.Hint.Mistake -> h.cell
            is Game.Hint.Reveal -> h.cell
            is Game.Hint.Logic -> h.step.placements.firstOrNull()?.cell ?: selected
        }
        if (g.status == Game.Status.WON) {
            refresh()
            win()
            return
        }
        if (selected >= 0 && g.values[selected] != 0) flashCompletedUnits(g, selected)
        refresh()
        scheduleSave()
    }

    private fun showMenu() {
        val g = game ?: return
        if (!g.isPlaying) return
        val items = ArrayList<Pair<String, () -> Unit>>()
        items += getString(R.string.menu_fill_notes) to {
            g.fillNotes()
            refresh()
            scheduleSave()
        }
        items += getString(R.string.menu_clear_notes) to {
            g.clearAllNotes()
            refresh()
            scheduleSave()
        }
        val cell = selected
        if (cell >= 0 && !g.isGiven(cell) && g.values[cell] != g.solution[cell]) {
            items += getString(R.string.menu_reveal_cell) to {
                g.reveal(cell, settings.autoRemoveNotes)
                if (g.status == Game.Status.WON) {
                    refresh()
                    win()
                } else {
                    flashCompletedUnits(g, cell)
                    refresh()
                    scheduleSave()
                }
            }
        }
        items += getString(R.string.menu_restart) to {
            Dialogs.show(
                this,
                getString(R.string.restart_title),
                getString(R.string.restart_message),
                listOf(
                    Dialogs.Action(getString(R.string.restart_confirm), primary = true) { restart() },
                    Dialogs.Action(getString(R.string.cancel)),
                ),
            )
        }
        items += getString(R.string.menu_show_solution) to {
            Dialogs.show(
                this,
                getString(R.string.solution_title),
                getString(R.string.solution_message),
                listOf(
                    Dialogs.Action(getString(R.string.solution_confirm), primary = true) { revealSolution() },
                    Dialogs.Action(getString(R.string.cancel)),
                ),
            )
        }
        items += getString(R.string.menu_copy) to {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Sudoku", Grid.format(g.givens)))
            Snack.show(this, getString(R.string.copied))
        }
        Dialogs.menu(this, getString(R.string.menu_title), items)
    }

    // ---- End of game --------------------------------------------------------------------------

    private fun restart() {
        val g = game ?: return
        accumulateTime()
        g.restart()
        selected = -1
        notesMode = false
        userPaused = false
        closeHint()
        winOverlay.visibility = View.GONE
        refresh()
        updateTimer()
        save()
    }

    private fun revealSolution() {
        val g = game ?: return
        accumulateTime()
        g.revealAll()
        if (g.mode != GameMode.CUSTOM) app.stats.lost(g.difficulty)
        closeHint()
        selected = -1
        refresh()
        updateTimer()
        save()
        Snack.show(this, getString(R.string.solution_shown))
    }

    private fun gameOver() {
        val g = game ?: return
        accumulateTime()
        g.markLost()
        if (g.mode != GameMode.CUSTOM) app.stats.lost(g.difficulty)
        updateTimer()
        save()
        showGameOver(g)
    }

    private fun showGameOver(g: Game) {
        Dialogs.show(
            this,
            getString(R.string.game_over_title),
            getString(R.string.game_over_message, g.mistakes),
            listOf(
                Dialogs.Action(getString(R.string.keep_playing), primary = true) {
                    g.keepPlaying()
                    updateTimer()
                    refresh()
                    save()
                },
                Dialogs.Action(getString(R.string.restart_confirm)) { restart() },
                Dialogs.Action(getString(if (g.mode == GameMode.RANDOM) R.string.new_game else R.string.home)) {
                    if (g.mode == GameMode.RANDOM) nextGame(g) else goHome()
                },
            ),
            cancelable = false,
        )
    }

    private fun win() {
        val g = game ?: return
        accumulateTime()
        updateTimer()
        closeHint()
        selected = -1
        var newBest = false
        if (g.mode != GameMode.CUSTOM) newBest = app.stats.won(g.difficulty, g.elapsedMs, g.isPerfect)
        if (g.mode == GameMode.DAILY) app.stats.completeDaily(g.tag, g.elapsedMs)
        // Every completed board goes into the history, whatever the mode.
        val board = SolvedBoard.of(g, System.currentTimeMillis())
        app.io.execute { app.history.add(board) }
        save()
        refresh()
        showWin(g, newBest, celebrate = true)
    }

    private fun showWin(g: Game, newBest: Boolean, celebrate: Boolean) {
        val fonts = Fonts.get(this)
        winOverlay.visibility = View.VISIBLE
        findViewById<TextView>(R.id.win_kicker).text = "${g.difficulty.title} · ${modeName(g)}"
        findViewById<TextView>(R.id.win_title).text =
            getString(if (g.mode == GameMode.DAILY) R.string.win_daily_title else R.string.win_title)
        findViewById<TextView>(R.id.win_time).text = formatTime(g.elapsedMs)
        findViewById<TextView>(R.id.win_mistakes).text = g.mistakes.toString()
        findViewById<TextView>(R.id.win_hints).text = g.hintsUsed.toString()

        val badges = findViewById<LinearLayout>(R.id.win_badges)
        badges.removeAllViews()
        val labels = buildList {
            if (newBest) add(getString(R.string.win_new_best))
            if (g.isPerfect) add(getString(R.string.win_perfect))
        }
        for (label in labels) {
            badges.addView(
                TextView(this).apply {
                    text = label.uppercase()
                    typeface = fonts.bold
                    textSize = 11f
                    letterSpacing = 0.12f
                    setTextColor(palette.accent)
                    setBackgroundColor(palette.onAccent)
                    setPadding(dp(10), dp(5), dp(10), dp(5))
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { marginEnd = dp(8) },
            )
        }

        val best = app.stats.record(g.difficulty).bestTimeMs
        findViewById<TextView>(R.id.win_best).apply {
            visibility = if (g.mode == GameMode.CUSTOM || best == 0L) View.GONE else View.VISIBLE
            text = "${getString(R.string.win_best)} ${formatTime(best)}"
        }

        val primary = findViewById<TextView>(R.id.win_primary)
        val secondary = findViewById<TextView>(R.id.win_secondary)
        when (g.mode) {
            GameMode.RANDOM, GameMode.STORY -> {
                primary.text = getString(R.string.new_game)
                primary.setOnClickListener { nextGame(g) }
                secondary.text = getString(R.string.home)
                secondary.setOnClickListener { goHome() }
            }
            GameMode.DAILY -> {
                primary.text = getString(R.string.home)
                primary.setOnClickListener { goHome() }
                secondary.text = getString(R.string.stats_title)
                secondary.setOnClickListener {
                    goHome()
                    startActivity(Intent(this, StatsActivity::class.java))
                }
            }
            GameMode.CUSTOM -> {
                primary.text = getString(R.string.solver_title)
                primary.setOnClickListener { finish() }
                secondary.text = getString(R.string.home)
                secondary.setOnClickListener { goHome() }
            }
        }

        if (celebrate) {
            findViewById<ConfettiView>(R.id.confetti).burst()
            winOverlay.alpha = 0f
            winOverlay.animate().alpha(1f).setDuration(320).start()
            val content = findViewById<View>(R.id.win_content)
            content.translationY = dp(24f)
            content.animate().translationY(0f).setDuration(420).start()
        }
    }

    private fun nextGame(g: Game) {
        if (g.mode == GameMode.RANDOM) startActivity(newRandom(this, g.difficulty))
        finish()
    }

    private fun goHome() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    // ---- Display ------------------------------------------------------------------------------

    private fun modeName(g: Game): String = modeName(g.mode, g.tag)

    private fun setControlsEnabled(enabled: Boolean) {
        for (view in listOf(toolUndo, toolErase, toolNotes, toolHint, toolMore, pause)) view.isEnabled = enabled
        numpad.isEnabled = enabled
        board.isEnabled = enabled
    }

    private fun refresh() {
        val g = game ?: return
        val check = settings.checkMistakes
        board.highlightPeers = settings.highlightPeers
        board.highlightSame = settings.highlightSame
        board.showGame(g, check)
        board.selected = if (g.isPlaying) selected else -1
        board.paused = userPaused
        board.message = null

        numpad.remaining = IntArray(10) { if (it == 0) 0 else g.remaining(it) }
        numpad.notesMode = notesMode
        numpad.hideFinished = settings.hideFinished

        val playing = g.isPlaying && !userPaused
        toolUndo.isEnabled = playing && g.canUndo
        toolErase.isEnabled = playing
        toolNotes.isEnabled = playing
        toolHint.isEnabled = playing
        toolMore.isEnabled = g.isPlaying && !userPaused
        numpad.isEnabled = playing
        toolNotes.active = notesMode
        toolNotes.badge = getString(if (notesMode) R.string.notes_on else R.string.notes_off).uppercase()

        title.text = g.difficulty.title
        modeLabel.text = modeName(g)
        mistakes.visibility = if (check) View.VISIBLE else View.GONE
        mistakes.text = if (settings.limitMistakes && !g.limitWaived) {
            getString(R.string.mistakes_limited, g.mistakes, Settings.MISTAKE_LIMIT)
        } else {
            getString(R.string.mistakes_count, g.mistakes)
        }
        mistakes.setTextColor(if (g.mistakes > 0) palette.error else palette.inkMuted)
        hints.text = getString(R.string.hints_count, g.hintsUsed)

        pause.visibility = if (g.isPlaying) View.VISIBLE else View.INVISIBLE
        pause.setImageResource(if (userPaused) R.drawable.ic_play else R.drawable.ic_pause)
        pause.contentDescription = getString(if (userPaused) R.string.resume else R.string.pause)
        resume.visibility = if (userPaused) View.VISIBLE else View.GONE
        updateTimerText()
    }

    // ---- Timer and saving ---------------------------------------------------------------------

    private fun updateTimerText() {
        val g = game ?: return
        timer.visibility = if (settings.showTimer) View.VISIBLE else View.GONE
        timer.text = formatTime(g.elapsedMs)
    }

    /** Starts or stops the clock to match whether the game is visible, unpaused and unfinished. */
    private fun updateTimer() {
        val g = game
        val shouldRun = g != null && foreground && !userPaused && g.isPlaying
        if (shouldRun == timing) return
        if (shouldRun) {
            lastTick = SystemClock.elapsedRealtime()
            timing = true
            scheduleTick()
        } else {
            accumulateTime()
            timing = false
            handler.removeCallbacks(ticker)
        }
    }

    private fun accumulateTime() {
        val g = game ?: return
        if (!timing) return
        val now = SystemClock.elapsedRealtime()
        g.elapsedMs += now - lastTick
        lastTick = now
    }

    private fun scheduleTick() {
        val g = game ?: return
        handler.postDelayed(ticker, 1000 - g.elapsedMs % 1000)
    }

    private fun scheduleSave() {
        handler.removeCallbacks(saver)
        handler.postDelayed(saver, 600)
    }

    private fun save() {
        handler.removeCallbacks(saver)
        val g = game ?: return
        accumulateTime()
        app.store.save(slot, g)
    }

    companion object {
        private const val EXTRA_SLOT = "slot"
        private const val EXTRA_NEW = "new"
        private const val EXTRA_DIFFICULTY = "difficulty"
        private const val EXTRA_GIVENS = "givens"
        private const val EXTRA_SOLUTION = "solution"
        private const val EXTRA_BOARD_ON_SCREEN = "board_on_screen"
        private const val KIND_RANDOM = "random"
        private const val KIND_DAILY = "daily"
        private const val KIND_CUSTOM = "custom"
        private const val STATE_SELECTED = "selected"
        private const val STATE_NOTES = "notes"
        private const val STATE_PAUSED = "paused"

        fun newRandom(context: Context, difficulty: Difficulty): Intent =
            Intent(context, GameActivity::class.java)
                .putExtra(EXTRA_SLOT, GameStore.Slot.MAIN.name)
                .putExtra(EXTRA_NEW, KIND_RANDOM)
                .putExtra(EXTRA_DIFFICULTY, difficulty.name)

        fun newDaily(context: Context): Intent =
            Intent(context, GameActivity::class.java)
                .putExtra(EXTRA_SLOT, GameStore.Slot.DAILY.name)
                .putExtra(EXTRA_NEW, KIND_DAILY)

        fun newCustom(context: Context, givens: IntArray, solution: IntArray, difficulty: Difficulty): Intent =
            Intent(context, GameActivity::class.java)
                .putExtra(EXTRA_SLOT, GameStore.Slot.MAIN.name)
                .putExtra(EXTRA_NEW, KIND_CUSTOM)
                .putExtra(EXTRA_DIFFICULTY, difficulty.name)
                .putExtra(EXTRA_GIVENS, Grid.format(givens))
                .putExtra(EXTRA_SOLUTION, Grid.format(solution))

        /**
         * Reopens the game saved in [slot]. Pass [boardOnScreen] when the caller has already grown a
         * preview of the board to that spot on screen: the game then opens around it.
         */
        fun resume(context: Context, slot: GameStore.Slot, boardOnScreen: Rect? = null): Intent =
            Intent(context, GameActivity::class.java)
                .putExtra(EXTRA_SLOT, slot.name)
                .apply { if (boardOnScreen != null) putExtra(EXTRA_BOARD_ON_SCREEN, boardOnScreen) }

        /**
         * Where this screen draws its board, in a content area of [width] by [height] pixels whose
         * system bars take [systemBars] (as [com.ultimatesudoku.app.ui.fitSystemBars] applies them).
         * Lays the real layout out off screen, so it can't drift from what [inflate] builds.
         */
        fun boardBounds(context: Context, width: Int, height: Int, systemBars: IntArray): Rect {
            val screen = LayoutInflater.from(context).inflate(R.layout.activity_game, FrameLayout(context), false)
            val root = screen.findViewById<View>(R.id.root)
            root.setPadding(
                root.paddingLeft + systemBars[0],
                root.paddingTop + systemBars[1],
                root.paddingRight + systemBars[2],
                root.paddingBottom + systemBars[3],
            )
            screen.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            screen.layout(0, 0, width, height)
            return boundsWithin(screen, screen.findViewById(R.id.board))
        }
    }
}
