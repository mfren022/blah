package com.ultimatesudoku.app

import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.ListView
import android.widget.TextView
import com.ultimatesudoku.app.ui.BoardView
import com.ultimatesudoku.app.ui.NumberPadView
import com.ultimatesudoku.app.ui.SegmentedView
import com.ultimatesudoku.core.Difficulty
import com.ultimatesudoku.core.Game
import com.ultimatesudoku.core.GameMode
import com.ultimatesudoku.core.Generator
import com.ultimatesudoku.core.Grid
import com.ultimatesudoku.core.SolvedBoard
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
class HistoryTest : ScreenTest() {

    private val app get() = RuntimeEnvironment.getApplication() as SudokuApp
    private val givens = Grid.parse(AppScreensTest.PUZZLE)!!
    private val solution = Grid.parse(AppScreensTest.SOLUTION)!!

    /** Plays [intent]'s game to the end by tapping the board and keypad. */
    private fun solveOnScreen(intent: android.content.Intent, start: IntArray, answer: IntArray) {
        val activity = Robolectric.buildActivity(GameActivity::class.java, intent).setup().get()
        waitFor { activity.findViewById<BoardView>(R.id.board).isEnabled }
        val board = activity.view<BoardView>(R.id.board)
        val pad = activity.view<NumberPadView>(R.id.numpad)
        for (cell in 0 until 81) {
            if (start[cell] != 0) continue
            tap(board, (Grid.col(cell) + 0.5f) / 9f, (Grid.row(cell) + 0.5f) / 9f)
            tap(pad, (answer[cell] - 0.5f) / 9f, 0.5f)
        }
        idle(500)
    }

    /** Stores a finished board directly, [minutesAgo] in the past. */
    private fun record(mode: GameMode, difficulty: Difficulty, seed: Long, timeMs: Long, minutesAgo: Long, mistakes: Int = 0, hints: Int = 0) {
        val puzzle = Generator(Random(seed)).generate(difficulty)
        app.history.add(
            SolvedBoard(
                completedAt = System.currentTimeMillis() - minutesAgo * 60_000,
                mode = mode,
                difficulty = difficulty,
                givens = puzzle.givens,
                solution = puzzle.solution,
                timeMs = timeMs,
                mistakes = mistakes,
                hints = hints,
                tag = if (mode == GameMode.DAILY) "2026-10-21" else "",
            ),
        )
    }

    @Test
    fun winningRecordsTheStartingBoardAndTime() {
        solveOnScreen(GameActivity.newCustom(app, givens, solution, Difficulty.MEDIUM), givens, solution)
        waitFor { app.history.count() == 1 }
        val board = app.history.all().single()
        assertArrayEquals(givens, board.givens)
        assertArrayEquals(solution, board.solution)
        assertEquals(GameMode.CUSTOM, board.mode)
        assertEquals(app.store.game(com.ultimatesudoku.app.data.GameStore.Slot.MAIN)!!.elapsedMs, board.timeMs)
    }

    @Test
    fun everyModeIsRecorded() {
        // A random game: generated on one screen, then resumed and played to the end.
        val random = Robolectric.buildActivity(GameActivity::class.java, GameActivity.newRandom(app, Difficulty.EASY)).setup()
        waitFor { !app.store.isGenerating }
        val game = app.store.game(com.ultimatesudoku.app.data.GameStore.Slot.MAIN)!!
        val start = game.givens.copyOf()
        random.pause().stop().destroy()
        solveOnScreen(GameActivity.resume(app, com.ultimatesudoku.app.data.GameStore.Slot.MAIN), start, game.solution)

        // Today's daily challenge.
        val dailyScreen = Robolectric.buildActivity(GameActivity::class.java, GameActivity.newDaily(app)).setup()
        waitFor { !app.store.isGenerating }
        val daily = app.store.game(com.ultimatesudoku.app.data.GameStore.Slot.DAILY)!!
        dailyScreen.pause().stop().destroy()
        solveOnScreen(GameActivity.resume(app, com.ultimatesudoku.app.data.GameStore.Slot.DAILY), daily.givens.copyOf(), daily.solution)

        waitFor { app.history.count() == 2 }
        assertEquals(setOf(GameMode.RANDOM, GameMode.DAILY), app.history.all().map { it.mode }.toSet())
        assertArrayEquals(start, app.history.all(GameMode.RANDOM).single().givens)
        assertEquals(daily.tag, app.history.all(GameMode.DAILY).single().tag)
    }

    @Test
    fun listShowsBoardsNewestFirst() {
        record(GameMode.RANDOM, Difficulty.EXPERT, 1, 754_000, minutesAgo = 5, hints = 1)
        record(GameMode.DAILY, Difficulty.HARD, 2, 482_000, minutesAgo = 60 * 24)
        record(GameMode.CUSTOM, Difficulty.MEDIUM, 3, 301_000, minutesAgo = 60 * 30, mistakes = 2)
        record(GameMode.RANDOM, Difficulty.EASY, 4, 192_000, minutesAgo = 60 * 50)
        val activity = Robolectric.buildActivity(HistoryActivity::class.java).setup().get()
        val list = activity.view<ListView>(R.id.list)
        waitFor { list.adapter.count == 4 }
        assertEquals(Difficulty.EXPERT, (list.adapter.getItem(0) as SolvedBoard).difficulty)
        capture(activity, "23_history")

        // Filter to daily challenges only.
        tap(activity.view<SegmentedView>(R.id.filter), 2.5f / 4, 0.5f)
        waitFor { list.adapter.count == 1 }
        assertEquals(GameMode.DAILY, (list.adapter.getItem(0) as SolvedBoard).mode)
    }

    @Test
    fun darkTheme() {
        app.settings.theme = com.ultimatesudoku.app.data.Settings.Theme.DARK
        record(GameMode.RANDOM, Difficulty.EXPERT, 1, 754_000, minutesAgo = 5, hints = 1)
        record(GameMode.DAILY, Difficulty.HARD, 2, 482_000, minutesAgo = 60 * 24)
        val activity = Robolectric.buildActivity(HistoryActivity::class.java).setup().get()
        waitFor { activity.view<ListView>(R.id.list).adapter.count == 2 }
        capture(activity, "28_history_dark")
    }

    @Test
    fun anUnreadableRowDoesNotHideOlderBoards() {
        record(GameMode.RANDOM, Difficulty.EASY, 4, 192_000, minutesAgo = 50)
        // A row this version can't read, e.g. written by a newer version with another mode.
        val db = android.database.sqlite.SQLiteDatabase.openDatabase(
            app.getDatabasePath("history.db").path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
        )
        db.execSQL(
            "INSERT INTO solved (completed_at, mode, difficulty, tag, givens, solution, time_ms, mistakes, hints) " +
                "VALUES (?, 'ARCADE', 'EASY', '', ?, ?, 1000, 0, 0)",
            arrayOf<Any>(System.currentTimeMillis() - 20 * 60_000, AppScreensTest.PUZZLE, AppScreensTest.SOLUTION),
        )
        db.close()
        record(GameMode.DAILY, Difficulty.HARD, 2, 482_000, minutesAgo = 5)
        assertEquals(listOf(GameMode.DAILY, GameMode.RANDOM), app.history.all().map { it.mode })
    }

    @Test
    fun emptyHistory() {
        val activity = Robolectric.buildActivity(HistoryActivity::class.java).setup().get()
        waitFor { activity.view<View>(R.id.empty).visibility == View.VISIBLE }
        capture(activity, "24_history_empty")
    }

    @Test
    fun boardDetailsAndActions() {
        record(GameMode.RANDOM, Difficulty.EXPERT, 1, 754_000, minutesAgo = 5, hints = 1)
        val stored = app.history.all().single()
        val activity = Robolectric.buildActivity(SolvedBoardActivity::class.java, SolvedBoardActivity.open(app, stored.id)).setup().get()
        waitFor { activity.view<TextView>(R.id.kicker).text.isNotEmpty() }
        capture(activity, "25_board_start")

        tap(activity.view<SegmentedView>(R.id.view_toggle), 0.75f, 0.5f)
        capture(activity, "26_board_solution")

        activity.view<View>(R.id.play_again).performClick()
        val play = shadowOf(activity).nextStartedActivity
        assertEquals(GameActivity::class.java.name, play.component?.className)

        activity.view<View>(R.id.open_solver).performClick()
        val solver = shadowOf(activity).nextStartedActivity
        assertEquals(SolverActivity::class.java.name, solver.component?.className)
        // The solver opens with the starting board filled in.
        val solverActivity = Robolectric.buildActivity(SolverActivity::class.java, solver).setup().get()
        tap(solverActivity.view(R.id.tool_solve))
        waitFor { solverActivity.view<TextView>(R.id.status_title).text == solverActivity.getString(R.string.solver_unique_title) }
    }

    @Test
    fun deletingABoard() {
        record(GameMode.RANDOM, Difficulty.HARD, 7, 400_000, minutesAgo = 1)
        val stored = app.history.all().single()
        val activity = Robolectric.buildActivity(SolvedBoardActivity::class.java, SolvedBoardActivity.open(app, stored.id)).setup().get()
        waitFor { activity.view<TextView>(R.id.kicker).text.isNotEmpty() }
        tap(activity.view(R.id.more))
        clickText(ShadowDialog.getLatestDialog().window!!.decorView, activity.getString(R.string.board_menu_delete))
        clickText(ShadowDialog.getLatestDialog().window!!.decorView, activity.getString(R.string.board_delete_confirm))
        waitFor { app.history.count() == 0 }
        waitFor { activity.isFinishing }
    }

    @Test
    fun copiesCsv() {
        record(GameMode.RANDOM, Difficulty.EXPERT, 1, 754_000, minutesAgo = 5)
        record(GameMode.DAILY, Difficulty.HARD, 2, 482_000, minutesAgo = 60)
        val activity = Robolectric.buildActivity(HistoryActivity::class.java).setup().get()
        waitFor { activity.view<ListView>(R.id.list).adapter.count == 2 }
        tap(activity.view(R.id.more))
        clickText(ShadowDialog.getLatestDialog().window!!.decorView, activity.getString(R.string.history_menu_copy))
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val lines = clipboard.primaryClip!!.getItemAt(0).text.toString().trim().lines()
        assertEquals(SolvedBoard.CSV_HEADER, lines[0])
        assertEquals(3, lines.size)
        // Oldest first.
        assertTrue(lines[1].contains(",DAILY,"))
        assertTrue(lines[2].contains(",RANDOM,"))
    }

    @Test
    fun homeShowsTheCount() {
        record(GameMode.RANDOM, Difficulty.EASY, 4, 192_000, minutesAgo = 50)
        record(GameMode.RANDOM, Difficulty.HARD, 5, 300_000, minutesAgo = 20)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        waitFor { findText(activity.window.decorView, "2 boards · 8:12 total") != null }
        activity.view<View>(R.id.tile_history).performClick()
        assertEquals(HistoryActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    @Test
    fun winScreenSaysTheBoardWasSaved() {
        solveOnScreen(GameActivity.newCustom(app, givens, solution, Difficulty.MEDIUM), givens, solution)
        val activity = Robolectric.buildActivity(GameActivity::class.java, GameActivity.resume(app, com.ultimatesudoku.app.data.GameStore.Slot.MAIN)).setup().get()
        idle(800)
        assertEquals(View.VISIBLE, activity.view<View>(R.id.win_overlay).visibility)
        capture(activity, "27_win_saved")
    }

    private fun findText(root: View, text: String): View? {
        if (root is TextView && root.text.toString() == text) return root
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) findText(root.getChildAt(i), text)?.let { return it }
        }
        return null
    }

    private fun clickText(root: View, text: String) {
        checkNotNull(findText(root, text)) { "No \"$text\" on screen" }.performClick()
        idle()
    }
}
