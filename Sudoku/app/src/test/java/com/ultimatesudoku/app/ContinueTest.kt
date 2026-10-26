package com.ultimatesudoku.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Insets
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.TextView
import com.ultimatesudoku.app.data.GameStore
import com.ultimatesudoku.app.data.Settings
import com.ultimatesudoku.app.ui.BoardView
import com.ultimatesudoku.app.ui.GrowOverlay
import com.ultimatesudoku.app.ui.SectionHeader
import com.ultimatesudoku.app.ui.appliedSystemBars
import com.ultimatesudoku.app.ui.boundsWithin
import com.ultimatesudoku.core.Difficulty
import com.ultimatesudoku.core.Game
import com.ultimatesudoku.core.GameMode
import com.ultimatesudoku.core.Grid
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.CountDownLatch

/** The home screen's continue card: its preview of the game, and growing into the game when tapped. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ContinueTest : ScreenTest() {

    private val app get() = RuntimeEnvironment.getApplication() as SudokuApp

    /**
     * Frames run only as the clock moves, 16 ms apart, so the animation can be stopped partway.
     * Otherwise Robolectric runs every pending frame on each idle and animations finish at once.
     */
    @Before
    fun frameByFrame() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(FRAME))
    }

    /** Saves an expert game partway through: digits, notes and one wrong digit. */
    private fun gameInProgress(): Game {
        val puzzle = AppScreensTest.EXPERT
        val game = Game(GameMode.RANDOM, Difficulty.EXPERT, puzzle.givens, puzzle.solution)
        val empties = (0 until 81).filter { puzzle.givens[it] == 0 }
        for (cell in empties.take(22)) game.enter(cell, puzzle.solution[cell], true, true)
        val candidates = Grid.candidates(game.values)
        for (cell in empties.drop(22).take(8)) for (d in Grid.digits(candidates[cell])) game.toggleNote(cell, d)
        val wrong = empties.last()
        game.enter(wrong, (1..9).first { it != puzzle.solution[wrong] }, true, true)
        game.elapsedMs = 754_000
        app.store.save(GameStore.Slot.MAIN, game)
        return game
    }

    /** Runs [ms] of frames one at a time, as a device would. A single long idle runs just one frame. */
    private fun play(ms: Long) = repeat((ms / FRAME).toInt()) { idle(FRAME) }

    /**
     * Waits for background work, running frames meanwhile. With frames paused, a pending layout pass
     * holds back every message queued behind it, so [waitFor]'s plain idling could wait forever.
     */
    private fun playUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            Thread.sleep(10)
            idle(FRAME)
        }
    }

    private fun home() = Robolectric.buildActivity(MainActivity::class.java).setup().also { idle(FRAME) }

    /** Opens the game with [intent] and lets it draw its first frame. */
    private fun game(intent: Intent): GameActivity =
        Robolectric.buildActivity(GameActivity::class.java, intent).setup().get().also { idle(FRAME) }

    private fun overlayOf(activity: Activity): GrowOverlay? {
        val content = activity.view<ViewGroup>(android.R.id.content)
        return (0 until content.childCount).map(content::getChildAt).filterIsInstance<GrowOverlay>().firstOrNull()
    }

    private fun screenBounds(view: View): Rect {
        val at = IntArray(2).also(view::getLocationOnScreen)
        return Rect(at[0], at[1], at[0] + view.width, at[1] + view.height)
    }

    private fun draw(activity: Activity): Bitmap {
        val view = activity.window.decorView
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    @Test
    fun cardPreviewsTheGameInProgress() {
        val game = gameInProgress()
        val activity = home().get()
        assertEquals(View.VISIBLE, activity.view<View>(R.id.continue_header).visibility)
        assertEquals(View.VISIBLE, activity.view<View>(R.id.continue_card).visibility)
        assertEquals("Expert", activity.view<TextView>(R.id.continue_title).text.toString())
        assertEquals("Random", activity.view<TextView>(R.id.continue_mode).text.toString())
        assertEquals("12:34", activity.view<TextView>(R.id.continue_time).text.toString())
        assertEquals("${(game.progress * 100).toInt()}%", activity.view<TextView>(R.id.continue_percent).text.toString())
        val description = activity.view<View>(R.id.continue_card).contentDescription.toString()
        assertTrue(description, description.startsWith("Continue Expert, Random: 12:34 played"))
        val preview = activity.view<BoardView>(R.id.continue_board)
        assertFalse(preview.isEnabled)
        assertEquals(preview.width, preview.height)
        capture(activity, "29_home_continue_notes")
    }

    @Test
    fun noGameInProgressHidesTheSection() {
        val game = gameInProgress()
        game.revealAll()
        val activity = home().get()
        assertEquals(View.GONE, activity.view<View>(R.id.continue_header).visibility)
        assertEquals(View.GONE, activity.view<View>(R.id.continue_card).visibility)
    }

    @Test
    fun sectionHeadersAreHeadings() {
        val activity = home().get()
        val headers = ArrayList<SectionHeader>()
        fun collect(view: View) {
            if (view is SectionHeader) headers += view
            if (view is ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i))
        }
        collect(activity.window.decorView)
        assertEquals(listOf("Continue", "Play", "Learn", "Records"), headers.map { it.text.toString() })
        for (header in headers) {
            assertTrue(header.isAccessibilityHeading)
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, header.layoutParams.width)
        }
    }

    @Test
    fun tappingTheCardGrowsItIntoTheGame() {
        gameInProgress()
        val controller = home()
        val activity = controller.get()
        val preview = activity.view<BoardView>(R.id.continue_board)
        val previewBounds = screenBounds(preview)

        activity.view<View>(R.id.continue_card).performClick()
        idle(FRAME)
        val overlay = checkNotNull(overlayOf(activity)) { "No overlay" }
        assertTrue(overlay.isGrowing)
        // The board in flight starts exactly over the preview, which it stands in for.
        assertEquals(View.INVISIBLE, preview.visibility)
        assertEquals(previewBounds, screenBounds(overlay.board))
        capture(activity, "30_grow_1_start")
        play(96)
        capture(activity, "30_grow_2")
        play(96)
        capture(activity, "30_grow_3")
        play(96)
        capture(activity, "30_grow_4")
        assertTrue(overlay.isGrowing)
        assertNull("The game opens once grown", shadowOf(activity).nextStartedActivity)
        play(200)
        assertFalse(overlay.isGrowing)
        capture(activity, "30_grow_5_end")
        val landed = screenBounds(overlay.board)
        val lastFrame = draw(activity)

        val intent: Intent = shadowOf(activity).nextStartedActivity
        assertEquals(GameActivity::class.java.name, intent.component?.className)
        val game = game(intent)
        val board = game.view<BoardView>(R.id.board)
        // The game lays its board out where the preview landed, so nothing jumps.
        assertEquals(landed, screenBounds(board))
        assertEquals(1f, board.scaleX, 0f)
        assertEquals(0f, board.translationY, 0f)
        // Its first frame is the overlay's last: same background, same board, controls not in yet.
        assertTrue("The game's first frame differs from the grown card", lastFrame.sameAs(draw(game)))
        play(96)
        capture(game, "30_grow_6_controls_coming_in")
        play(400)
        val root = game.view<ViewGroup>(R.id.root)
        for (i in 0 until root.childCount) {
            assertEquals(1f, root.getChildAt(i).alpha, 0f)
            assertEquals(0f, root.getChildAt(i).translationY, 0f)
        }
        capture(game, "30_grow_7_game")

        // Once the game covers it, the home screen is put back as it was.
        controller.pause().stop()
        assertNull(overlayOf(activity))
        assertEquals(View.VISIBLE, preview.visibility)
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun growsIntoTheLandscapeGame() {
        gameInProgress()
        val activity = home().get()
        activity.view<View>(R.id.continue_card).performClick()
        play(208)
        capture(activity, "31_grow_landscape_middle")
        play(400)
        val landed = screenBounds(overlayOf(activity)!!.board)
        val lastFrame = draw(activity)
        val game = game(shadowOf(activity).nextStartedActivity)
        assertEquals(landed, screenBounds(game.view(R.id.board)))
        assertTrue(lastFrame.sameAs(draw(game)))
    }

    @Test
    fun darkTheme() {
        app.settings.theme = Settings.Theme.DARK
        gameInProgress()
        val activity = home().get()
        capture(activity, "32_home_continue_dark")
        activity.view<View>(R.id.continue_card).performClick()
        play(176)
        capture(activity, "32_grow_dark_middle")
        play(400)
        val lastFrame = draw(activity)
        val game = game(shadowOf(activity).nextStartedActivity)
        assertTrue(lastFrame.sameAs(draw(game)))
    }

    @Test
    fun backMidwayShrinksTheCardBack() {
        gameInProgress()
        val activity = home().get()
        activity.view<View>(R.id.continue_card).performClick()
        play(176)
        @Suppress("DEPRECATION")
        activity.onBackPressed()
        assertFalse(activity.isFinishing)
        play(64)
        assertNotNull("Still shrinking", overlayOf(activity))
        play(400)
        assertNull(overlayOf(activity))
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(View.VISIBLE, activity.view<View>(R.id.continue_board).visibility)

        // And it can grow again.
        activity.view<View>(R.id.continue_card).performClick()
        play(600)
        assertEquals(GameActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    @Test
    fun leavingMidwayDoesNotOpenTheGame() {
        gameInProgress()
        val controller = home()
        val activity = controller.get()
        activity.view<View>(R.id.continue_card).performClick()
        play(112)
        controller.pause().stop()
        play(1000)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertNull(overlayOf(activity))
        controller.restart().start().resume()
        idle(FRAME)
        assertEquals(View.VISIBLE, activity.view<View>(R.id.continue_board).visibility)
    }

    @Test
    fun whileANewPuzzleIsGeneratingItOpensWithoutGrowing() {
        val game = gameInProgress()
        val activity = home().get()
        // A new game replacing this one is still being generated.
        val release = CountDownLatch(1)
        app.store.generate(GameStore.Slot.MAIN, { release.await(); game }, {})
        activity.view<View>(R.id.continue_card).performClick()
        assertNull(overlayOf(activity))
        assertEquals(GameActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        release.countDown()
        playUntil { !app.store.isGenerating }
    }

    @Test
    fun theCardKeepsUpWithTheGame() {
        val game = gameInProgress()
        val controller = home()
        val activity = controller.get()
        controller.pause().stop()
        // Played on: one more digit and another minute.
        val cell = (0 until 81).first { game.values[it] == 0 }
        game.enter(cell, game.solution[cell], true, true)
        game.elapsedMs += 60_000
        controller.restart().start().resume()
        assertEquals("13:34", activity.view<TextView>(R.id.continue_time).text.toString())
        assertEquals("${(game.progress * 100).toInt()}%", activity.view<TextView>(R.id.continue_percent).text.toString())
    }

    @Test
    fun boardBoundsFollowTheSystemBars() {
        // As on Android 15, where the app draws under a status bar and a navigation bar.
        gameInProgress()
        val game = game(GameActivity.resume(app, GameStore.Slot.MAIN))
        val bars = WindowInsets.Builder()
            .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 72, 0, 0))
            .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 63))
            .build()
        val content = game.view<ViewGroup>(android.R.id.content)
        content.getChildAt(0).dispatchApplyWindowInsets(bars)
        content.requestLayout()
        idle(FRAME)
        val root = game.view<View>(R.id.root)
        assertArrayEquals(intArrayOf(0, 72, 0, 63), appliedSystemBars(root))
        val expected = GameActivity.boardBounds(game, content.width, content.height, appliedSystemBars(root))
        assertEquals(expected, boundsWithin(content, game.view(R.id.board)))
    }

    private companion object {
        const val FRAME = 16L
    }
}
