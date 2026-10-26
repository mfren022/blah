package com.ultimatesudoku.app

import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.ultimatesudoku.app.data.GameStore
import com.ultimatesudoku.app.data.Settings
import com.ultimatesudoku.app.ui.BoardView
import com.ultimatesudoku.app.ui.NumberPadView
import com.ultimatesudoku.app.ui.SegmentedView
import com.ultimatesudoku.core.Difficulty
import com.ultimatesudoku.core.Game
import com.ultimatesudoku.core.GameMode
import com.ultimatesudoku.core.Generator
import com.ultimatesudoku.core.Grid
import com.ultimatesudoku.core.Puzzle
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppScreensTest : ScreenTest() {

    private val app get() = RuntimeEnvironment.getApplication() as SudokuApp

    private val givens = Grid.parse(PUZZLE)!!
    private val solution = Grid.parse(SOLUTION)!!

    private fun customGame() = Robolectric.buildActivity(
        GameActivity::class.java,
        GameActivity.newCustom(RuntimeEnvironment.getApplication(), givens, solution, Difficulty.MEDIUM),
    ).setup()

    /** Taps the centre of a cell on [board]. */
    private fun tapCell(board: BoardView, cell: Int) =
        tap(board, (Grid.col(cell) + 0.5f) / 9f, (Grid.row(cell) + 0.5f) / 9f)

    /** Taps a key on a one-row number pad. */
    private fun tapDigit(pad: NumberPadView, digit: Int) = tap(pad, (digit - 0.5f) / 9f, 0.5f)

    @Test
    fun home() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(View.GONE, activity.view<View>(R.id.continue_card).visibility)
        capture(activity, "01_home")
    }

    @Test
    fun homeWithGameInProgress() {
        val game = Game(GameMode.RANDOM, Difficulty.EXPERT, givens, solution)
        for (c in 0 until 30) if (givens[c] == 0) game.enter(c, solution[c], true, true)
        game.elapsedMs = 754_000
        app.store.save(GameStore.Slot.MAIN, game)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(View.VISIBLE, activity.view<View>(R.id.continue_card).visibility)
        capture(activity, "02_home_continue")
    }

    @Test
    fun randomDifficulties() {
        app.stats.won(Difficulty.EASY, 192_000, perfect = true)
        val activity = Robolectric.buildActivity(RandomActivity::class.java).setup().get()
        capture(activity, "03_random")
    }

    @Test
    fun playingAGame() {
        val activity = customGame().get()
        val board = activity.view<BoardView>(R.id.board)
        val pad = activity.view<NumberPadView>(R.id.numpad)
        val empties = (0 until 81).filter { givens[it] == 0 }

        // Correct entries.
        for (cell in empties.take(6)) {
            tapCell(board, cell)
            tapDigit(pad, solution[cell])
        }
        // A wrong entry counts as a mistake.
        val wrongCell = empties[8]
        tapCell(board, wrongCell)
        tapDigit(pad, (1..9).first { it != solution[wrongCell] && it != givens[wrongCell] })
        val game = app.store.game(GameStore.Slot.MAIN)!!
        assertEquals(1, game.mistakes)

        // Notes in another cell.
        tap(activity.view(R.id.tool_notes))
        val noteCell = empties[12]
        tapCell(board, noteCell)
        for (d in Grid.digits(Grid.candidates(game.values)[noteCell])) tapDigit(pad, d)
        assertTrue(game.notes[noteCell] != 0)
        tap(activity.view(R.id.tool_notes))

        // Select a filled cell to show matching digits.
        tapCell(board, empties[0])
        capture(activity, "04_game")

        // Undo removes the last change.
        tap(activity.view(R.id.tool_undo))

        // Pausing shows the puzzle's menu over the hidden board, with the selected cell to reveal.
        // There's room for every option without scrolling.
        tap(activity.view(R.id.pause))
        assertNotNull(menuRow(activity, R.string.menu_reveal_cell))
        assertFalse(activity.view<View>(R.id.pause_menu_scroll).canScrollVertically(1))
        capture(activity, "04b_menu")
    }

    /** A row of the menu shown over the paused board, or null. */
    private fun menuRow(activity: GameActivity, label: Int): View? {
        val rows = activity.view<ViewGroup>(R.id.pause_menu_items)
        return (0 until rows.childCount).map(rows::getChildAt).firstOrNull { (it as TextView).text == activity.getString(label) }
    }

    @Test
    fun hint() {
        val activity = customGame().get()
        tap(activity.view(R.id.tool_hint))
        assertEquals(View.VISIBLE, activity.view<View>(R.id.hint_panel).visibility)
        assertTrue(activity.view<TextView>(R.id.hint_text).text.isNotBlank())
        capture(activity, "05_hint")
        tap(activity.view(R.id.hint_apply))
        assertEquals(View.GONE, activity.view<View>(R.id.hint_panel).visibility)
        assertEquals(1, app.store.game(GameStore.Slot.MAIN)!!.hintsUsed)
    }

    @Test
    fun advancedHintWithNotes() {
        // A position that needs more than singles: the hint shows candidates and eliminations.
        val game = Game(GameMode.RANDOM, Difficulty.EXPERT, EXPERT.givens, EXPERT.solution)
        app.store.save(GameStore.Slot.MAIN, game)
        val activity = Robolectric.buildActivity(GameActivity::class.java, GameActivity.resume(app, GameStore.Slot.MAIN)).setup().get()
        // Apply hints until one is more than a single.
        repeat(80) {
            tap(activity.view(R.id.tool_hint))
            val title = activity.view<TextView>(R.id.hint_title).text.toString()
            if (title !in setOf("Full House", "Hidden Single", "Naked Single")) {
                capture(activity, "06_hint_advanced")
                return
            }
            tap(activity.view(R.id.hint_apply))
        }
    }

    @Test
    fun pausedGame() {
        val activity = customGame().get()
        tap(activity.view(R.id.pause))
        val menu = activity.view<View>(R.id.pause_menu)
        assertEquals(View.VISIBLE, menu.visibility)
        // No cell is selected, so there is nothing to reveal, and the menu fills the board's square.
        assertEquals(null, menuRow(activity, R.string.menu_reveal_cell))
        assertEquals(bounds(activity.view(R.id.board)), bounds(menu))
        // A large title at the top, the options in the middle, and two buttons side by side at the bottom.
        val title = activity.view<TextView>(R.id.pause_title)
        assertTrue(title.isAllCaps)
        assertEquals(34f, title.textSize / activity.resources.displayMetrics.scaledDensity, 0.01f)
        val options = bounds(activity.view(R.id.pause_menu_items))
        val restart = activity.view<View>(R.id.pause_restart)
        val abandon = activity.view<View>(R.id.pause_abandon)
        assertTrue(bounds(title).bottom <= options.top)
        assertTrue(options.bottom <= bounds(restart).top)
        assertEquals(bounds(restart).top, bounds(abandon).top)
        assertTrue(bounds(restart).right < bounds(abandon).left)
        assertEquals(bounds(menu).bottom - bounds(restart).bottom, bounds(restart).left - bounds(menu).left)
        // Restart is white and abandon red, each with an icon.
        assertEquals(Color.WHITE, fill(restart))
        assertEquals(activity.getColor(R.color.button_danger), fill(abandon))
        for (button in listOf(restart, abandon)) assertTrue((button as ViewGroup).getChildAt(0) is android.widget.ImageView)
        capture(activity, "07_paused")
    }

    private fun bounds(view: View): Rect {
        val at = IntArray(2).also(view::getLocationInWindow)
        return Rect(at[0], at[1], at[0] + view.width, at[1] + view.height)
    }

    /** The colour a button is filled with, from just inside its left edge. */
    private fun fill(button: View): Int {
        val bitmap = Bitmap.createBitmap(button.width, button.height, Bitmap.Config.ARGB_8888)
        button.draw(Canvas(bitmap))
        return bitmap.getPixel(Math.round(6 * button.resources.displayMetrics.density), button.height / 2)
    }

    @Test
    fun pauseIsTheRoundButtonInTheMiddle() {
        val activity = customGame().get()
        val pause = activity.view<View>(R.id.pause)
        val controls = activity.view<View>(R.id.controls)
        // In the action bar at the bottom, centred, and large.
        assertTrue(generateSequence(pause.parent as View) { it.parent as? View }.any { it === controls })
        val at = IntArray(2).also(pause::getLocationInWindow)
        val screenWidth = activity.window.decorView.width
        assertTrue(Math.abs(at[0] + pause.width / 2 - screenWidth / 2) <= 2)
        assertEquals(Math.round(56 * activity.resources.displayMetrics.density), pause.width)
        assertEquals(pause.width, pause.height)
        // The top bar ends with the time; there is no menu button any more.
        val timer = activity.view<TextView>(R.id.timer)
        val bar = timer.parent as ViewGroup
        assertEquals(timer, bar.getChildAt(bar.childCount - 1))
        assertEquals(20f, timer.textSize / activity.resources.displayMetrics.scaledDensity, 0.01f)
        assertEquals(null, activity.findViewById<View>(R.id.more))
    }

    @Test
    fun theMenuOverThePausedBoard() {
        val activity = customGame().get()
        val game = app.store.game(GameStore.Slot.MAIN)!!
        val pause = activity.view<View>(R.id.pause)
        val menu = activity.view<View>(R.id.pause_menu)
        tap(pause)
        assertEquals(View.VISIBLE, menu.visibility)
        assertEquals(activity.getString(R.string.resume), pause.contentDescription)

        // Copying leaves the game paused.
        tap(menuRow(activity, R.string.menu_copy)!!)
        val clipboard = activity.getSystemService(ClipboardManager::class.java)
        assertEquals(Grid.format(givens), clipboard.primaryClip!!.getItemAt(0).text.toString())
        assertEquals(View.VISIBLE, menu.visibility)

        // Changing the board resumes, so the change shows.
        tap(menuRow(activity, R.string.menu_fill_notes)!!)
        assertEquals(View.GONE, menu.visibility)
        assertTrue((0 until 81).any { game.notes[it] != 0 })
        assertEquals(activity.getString(R.string.pause), pause.contentDescription)

        // Restarting, the white button, asks first; cancelling stays paused, confirming starts over, unpaused.
        tap(pause)
        tap(activity.view(R.id.pause_restart))
        ShadowDialog.getLatestDialog().dismiss()
        assertEquals(View.VISIBLE, menu.visibility)
        tap(activity.view(R.id.pause_restart))
        val dialog = ShadowDialog.getLatestDialog()
        clickText(dialog.window!!.decorView, activity.getString(R.string.restart_confirm))
        assertEquals(View.GONE, menu.visibility)
        assertTrue((0 until 81).all { game.notes[it] == 0 })

        // Abandoning asks first too; a puzzle of your own doesn't count as a loss, so it doesn't say so.
        tap(pause)
        tap(activity.view(R.id.pause_abandon))
        assertNotNull(findText(ShadowDialog.getLatestDialog().window!!.decorView, activity.getString(R.string.abandon_message_custom)))
        ShadowDialog.getLatestDialog().dismiss()
        assertEquals(View.VISIBLE, menu.visibility)
        assertTrue(game.isPlaying)

        // Showing the solution ends the game with the board on view, and pause greys out.
        tap(menuRow(activity, R.string.menu_show_solution)!!)
        clickText(ShadowDialog.getLatestDialog().window!!.decorView, activity.getString(R.string.solution_confirm))
        assertEquals(Game.Status.REVEALED, game.status)
        assertEquals(View.GONE, menu.visibility)
        assertFalse(activity.view<BoardView>(R.id.board).paused)
        assertFalse(pause.isEnabled)
    }

    @Test
    fun abandoningAPuzzle() {
        // A random game, two wins into a streak.
        repeat(2) { app.stats.won(Difficulty.HARD, 300_000, perfect = false) }
        val game = Game(GameMode.RANDOM, Difficulty.HARD, givens, solution)
        app.store.start(GameStore.Slot.MAIN, game)
        val activity = Robolectric.buildActivity(GameActivity::class.java, GameActivity.resume(app, GameStore.Slot.MAIN)).setup().get()
        tap(activity.view(R.id.pause))

        // The red button asks first, with a red button to confirm; cancelling stays paused.
        tap(activity.view(R.id.pause_abandon))
        val panel = ShadowDialog.getLatestDialog().window!!.decorView
        assertNotNull(findText(panel, activity.getString(R.string.abandon_message)))
        assertEquals(activity.getColor(R.color.button_danger), fill(findText(panel, activity.getString(R.string.abandon_confirm))!!))
        captureDialog(activity, "07b_abandon")
        ShadowDialog.getLatestDialog().dismiss()
        assertEquals(View.VISIBLE, activity.view<View>(R.id.pause_menu).visibility)
        assertTrue(game.isPlaying)
        assertFalse(activity.isFinishing)

        // Confirming gives the game up as a loss, which ends the streak, and leaves it.
        tap(activity.view(R.id.pause_abandon))
        clickText(ShadowDialog.getLatestDialog().window!!.decorView, activity.getString(R.string.abandon_confirm))
        assertEquals(Game.Status.LOST, app.store.game(GameStore.Slot.MAIN)!!.status)
        assertEquals(0, app.stats.record(Difficulty.HARD).streak)
        assertTrue(activity.isFinishing)
    }

    private fun findText(root: View, text: String): TextView? {
        if (root is TextView && root.text.toString() == text) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) findText(root.getChildAt(i), text)?.let { return it }
        return null
    }

    private fun clickText(root: View, text: String) {
        checkNotNull(findText(root, text)) { "No \"$text\"" }.performClick()
        idle()
    }

    @Test
    fun winningAGame() {
        val activity = customGame().get()
        val board = activity.view<BoardView>(R.id.board)
        val pad = activity.view<NumberPadView>(R.id.numpad)
        for (cell in 0 until 81) {
            if (givens[cell] != 0) continue
            tapCell(board, cell)
            tapDigit(pad, solution[cell])
        }
        idle(1500)
        assertEquals(View.VISIBLE, activity.view<View>(R.id.win_overlay).visibility)
        assertEquals(Game.Status.WON, app.store.game(GameStore.Slot.MAIN)!!.status)
        capture(activity, "08_win")
    }

    @Test
    fun mistakeLimitEndsTheGame() {
        val activity = customGame().get()
        val board = activity.view<BoardView>(R.id.board)
        val pad = activity.view<NumberPadView>(R.id.numpad)
        val cell = (0 until 81).first { givens[it] == 0 }
        val wrong = (1..9).filter { it != solution[cell] }
        tapCell(board, cell)
        for (d in wrong.take(Settings.MISTAKE_LIMIT)) tapDigit(pad, d)
        assertEquals(Game.Status.LOST, app.store.game(GameStore.Slot.MAIN)!!.status)
        captureDialog(activity, "09_game_over")
    }

    @Test
    fun randomGameGenerates() {
        val controller = Robolectric.buildActivity(
            GameActivity::class.java,
            GameActivity.newRandom(app, Difficulty.EXPERT),
        ).setup()
        waitFor { !app.store.isGenerating }
        val game = app.store.game(GameStore.Slot.MAIN)
        assertNotNull(game)
        assertEquals(Difficulty.EXPERT, game!!.difficulty)
        assertEquals(1, app.stats.record(Difficulty.EXPERT).started)
        capture(controller.get(), "10_game_expert")
    }

    @Test
    fun dailyChallenge() {
        val controller = Robolectric.buildActivity(GameActivity::class.java, GameActivity.newDaily(app)).setup()
        waitFor { !app.store.isGenerating }
        assertEquals(GameMode.DAILY, app.store.game(GameStore.Slot.DAILY)!!.mode)
        capture(controller.get(), "11_daily")
    }

    @Test
    fun solver() {
        val activity = Robolectric.buildActivity(SolverActivity::class.java).setup().get()
        capture(activity, "12_solver_empty")
        val board = activity.view<BoardView>(R.id.board)
        val pad = activity.view<NumberPadView>(R.id.numpad)
        for (cell in 0 until 81) {
            if (givens[cell] == 0) continue
            tapCell(board, cell)
            tapDigit(pad, givens[cell])
        }
        tap(activity.view(R.id.tool_solve))
        waitFor { activity.view<TextView>(R.id.status_title).text == activity.getString(R.string.solver_unique_title) }
        capture(activity, "13_solver_solved")
    }

    @Test
    fun solverStepByStep() {
        val activity = Robolectric.buildActivity(SolverActivity::class.java).setup().get()
        val board = activity.view<BoardView>(R.id.board)
        val pad = activity.view<NumberPadView>(R.id.numpad)
        val hard = EXPERT.givens
        for (cell in 0 until 81) {
            if (hard[cell] == 0) continue
            tapCell(board, cell)
            tapDigit(pad, hard[cell])
        }
        tap(activity.view(R.id.tool_step))
        waitFor { activity.view<View>(R.id.status_action).visibility == View.VISIBLE }
        repeat(40) { tap(activity.view(R.id.tool_step)) }
        capture(activity, "14_solver_step")
    }

    @Test
    fun story() {
        val activity = Robolectric.buildActivity(StoryActivity::class.java).setup().get()
        capture(activity, "15_story")
    }

    @Test
    fun statistics() {
        app.stats.started(Difficulty.HARD)
        app.stats.started(Difficulty.HARD)
        app.stats.won(Difficulty.HARD, 421_000, perfect = false)
        app.stats.completeDaily("2026-10-21", 300_000)
        val activity = Robolectric.buildActivity(StatsActivity::class.java).setup().get()
        tap(activity.view<SegmentedView>(R.id.tabs), 2.5f / 5, 0.5f)
        capture(activity, "16_stats")
    }

    @Test
    fun settings() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        capture(activity, "17_settings")
    }

    @Test
    fun darkTheme() {
        app.settings.theme = Settings.Theme.DARK
        capture(Robolectric.buildActivity(MainActivity::class.java).setup().get(), "18_home_dark")
        val activity = customGame().get()
        val board = activity.view<BoardView>(R.id.board)
        val pad = activity.view<NumberPadView>(R.id.numpad)
        val empties = (0 until 81).filter { givens[it] == 0 }
        for (cell in empties.take(10)) {
            tapCell(board, cell)
            tapDigit(pad, solution[cell])
        }
        tap(activity.view(R.id.tool_hint))
        capture(activity, "19_game_dark")
        tap(activity.view(R.id.pause))
        capture(activity, "19b_paused_dark")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscapeGame() {
        val activity = customGame().get()
        tapCell(activity.view(R.id.board), 40)
        capture(activity, "20_game_landscape")
        tap(activity.view(R.id.pause))
        capture(activity, "20b_paused_landscape")
    }

    @Test
    fun smallPhone() {
        // A compact 360x640 dp screen still fits the board and controls.
        RuntimeEnvironment.setQualifiers("w360dp-h640dp-xhdpi")
        val activity = customGame().get()
        capture(activity, "21_game_small")
        val pad = activity.view<View>(R.id.numpad)
        assertTrue(pad.height > 0)
        // The menu over the paused board is as wide as the board and, with a cell selected to reveal,
        // taller, as far as there's room; the options scroll for the rest and the buttons stay in view.
        tapCell(activity.view(R.id.board), 2)
        tap(activity.view(R.id.pause))
        val menu = activity.view<View>(R.id.pause_menu)
        val board = activity.view<View>(R.id.board)
        assertEquals(board.width, menu.width)
        assertTrue(menu.height >= board.height)
        assertTrue(menu.height <= activity.view<View>(R.id.board_area).height)
        assertTrue(bounds(menu).contains(bounds(activity.view(R.id.pause_abandon))))
        capture(activity, "21b_paused_small")
    }

    @Test
    fun homeTilesOpenScreens() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val shadow = shadowOf(activity)
        activity.view<View>(R.id.tile_story).performClick()
        assertEquals(StoryActivity::class.java.name, shadow.nextStartedActivity.component?.className)
        activity.view<View>(R.id.tile_random).performClick()
        assertEquals(RandomActivity::class.java.name, shadow.nextStartedActivity.component?.className)
        activity.view<View>(R.id.tile_solver).performClick()
        assertEquals(SolverActivity::class.java.name, shadow.nextStartedActivity.component?.className)
        activity.view<View>(R.id.tile_strategies).performClick()
        assertEquals(StrategiesActivity::class.java.name, shadow.nextStartedActivity.component?.className)
        activity.view<View>(R.id.tile_daily).performClick()
        val daily: Intent = shadow.nextStartedActivity
        assertEquals(GameActivity::class.java.name, daily.component?.className)
    }

    companion object {
        const val PUZZLE = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
        const val SOLUTION = "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

        /** A fixed puzzle that needs advanced techniques. */
        val EXPERT: Puzzle by lazy { Generator(Random(2026)).generate(Difficulty.EXPERT) }
    }
}
