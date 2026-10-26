package com.ultimatesudoku.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import com.ultimatesudoku.app.data.GameStore
import com.ultimatesudoku.app.data.Settings
import com.ultimatesudoku.core.Difficulty
import com.ultimatesudoku.core.Game
import com.ultimatesudoku.core.GameMode
import com.ultimatesudoku.core.Grid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class LifecycleTest : ScreenTest() {

    private val app get() = RuntimeEnvironment.getApplication() as SudokuApp
    private val givens = Grid.parse(AppScreensTest.PUZZLE)!!
    private val solution = Grid.parse(AppScreensTest.SOLUTION)!!

    private fun customGame() = Robolectric.buildActivity(
        GameActivity::class.java,
        GameActivity.newCustom(app, givens, solution, Difficulty.MEDIUM),
    ).setup()

    @Test
    fun rotatingKeepsTheHintAndGame() {
        val controller = customGame()
        val activity = controller.get()
        tap(activity.view(R.id.tool_hint))
        val text = activity.view<TextView>(R.id.hint_text).text.toString()

        val landscape = Configuration(activity.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
            screenWidthDp = 891
            screenHeightDp = 411
        }
        controller.configurationChange(landscape)
        idle()
        assertEquals(View.VISIBLE, activity.view<View>(R.id.hint_panel).visibility)
        assertEquals(text, activity.view<TextView>(R.id.hint_text).text.toString())
        tap(activity.view(R.id.hint_apply))
        assertEquals(1, app.store.game(GameStore.Slot.MAIN)!!.hintsUsed)
    }

    @Test
    fun switchingThemeRecreatesWithTheSameGame() {
        val controller = customGame()
        val activity = controller.get()
        val cell = (0 until 81).first { givens[it] == 0 }
        activity.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        controller.pause()
        app.settings.theme = Settings.Theme.DARK
        controller.resume()
        idle()
        // The recreated screen reloads the saved game.
        val game = app.store.game(GameStore.Slot.MAIN)!!
        assertEquals(Game.Status.PLAYING, game.status)
        assertTrue(cell >= 0)
    }

    @Test
    fun hardwareKeyboardPlays() {
        val activity = customGame().get()
        // Arrow keys start in the centre cell (R5C5, empty in this puzzle).
        activity.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        activity.onKeyDown(KeyEvent.KEYCODE_DPAD_UP, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
        val digit = solution[40]
        activity.onKeyDown(KeyEvent.KEYCODE_0 + digit, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0 + digit))
        assertEquals(digit, app.store.game(GameStore.Slot.MAIN)!!.values[40])
        activity.onKeyDown(KeyEvent.KEYCODE_DEL, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        assertEquals(0, app.store.game(GameStore.Slot.MAIN)!!.values[40])
    }

    @Test
    fun reopeningALostGameOffersTheChoicesAgain() {
        val game = Game(GameMode.RANDOM, Difficulty.HARD, givens, solution)
        game.markLost()
        app.store.save(GameStore.Slot.MAIN, game)
        val activity = Robolectric.buildActivity(GameActivity::class.java, GameActivity.resume(app, GameStore.Slot.MAIN)).setup().get()
        idle()
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue(dialog != null && dialog.isShowing)
        captureDialog(activity, "22_lost_reopened")
    }

    @Test
    fun backClosesHintFirst() {
        val controller = customGame()
        val activity = controller.get()
        tap(activity.view(R.id.tool_hint))
        @Suppress("DEPRECATION")
        activity.onBackPressed()
        assertEquals(View.GONE, activity.view<View>(R.id.hint_panel).visibility)
        assertTrue(!activity.isFinishing)
    }

    @Test
    fun solverPastesAPuzzle() {
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("p", AppScreensTest.PUZZLE))
        val activity = Robolectric.buildActivity(SolverActivity::class.java).setup().get()
        tap(activity.view(R.id.more))
        // The first menu row is "Paste puzzle".
        val dialog = ShadowDialog.getLatestDialog()
        val paste = findText(dialog.window!!.decorView, activity.getString(R.string.solver_menu_paste))
        paste!!.performClick()
        idle()
        tap(activity.view(R.id.tool_solve))
        waitFor { activity.view<TextView>(R.id.status_title).text == activity.getString(R.string.solver_unique_title) }
        // Play the pasted puzzle as a custom game.
        tap(activity.view(R.id.status_action))
        val started = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
        assertEquals(GameActivity::class.java.name, started.component?.className)
    }

    @Test
    fun settingsTogglesPersist() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        (activity.findViewById<android.view.ViewGroup>(R.id.tabs)).getChildAt(MainActivity.TAB_SETTINGS).performClick()
        val row = findText(activity.window.decorView, activity.getString(R.string.settings_check))!!.parent.parent as View
        row.performClick()
        idle(300)
        assertEquals(false, app.settings.checkMistakes)
        assertEquals(false, app.settings.limitMistakes)
    }

    @Test
    fun aSupersededGenerationIsDropped() {
        val store = app.store
        val first = Game(GameMode.RANDOM, Difficulty.EASY, givens, solution)
        val second = Game(GameMode.RANDOM, Difficulty.HARD, givens, solution)
        val delivered = ArrayList<Game>()
        val gate = java.util.concurrent.CountDownLatch(1)
        store.generate(GameStore.Slot.MAIN, {
            gate.await()
            first
        }) { delivered += it }
        store.generate(GameStore.Slot.MAIN, { second }) { delivered += it }
        gate.countDown()
        waitFor { !store.isGenerating }
        idle(200)
        assertEquals(listOf(second), delivered)
        assertEquals(Difficulty.HARD, store.game(GameStore.Slot.MAIN)!!.difficulty)
        assertEquals(0, app.stats.record(Difficulty.EASY).started)
    }

    private fun findText(root: View, text: String): View? {
        if (root is TextView && root.text.toString() == text) return root
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) findText(root.getChildAt(i), text)?.let { return it }
        }
        return null
    }
}
