package com.ultimatesudoku.app

import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ultimatesudoku.app.data.Settings
import com.ultimatesudoku.app.ui.BoardView
import com.ultimatesudoku.app.ui.Palette
import com.ultimatesudoku.app.ui.SectionHeader
import com.ultimatesudoku.app.ui.TileView
import com.ultimatesudoku.core.Grid
import com.ultimatesudoku.core.Strategies
import com.ultimatesudoku.core.Technique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class StrategyTest : ScreenTest() {

    private val app get() = RuntimeEnvironment.getApplication() as SudokuApp

    private fun lesson(technique: Technique): ActivityController<StrategyActivity> =
        Robolectric.buildActivity(StrategyActivity::class.java, StrategyActivity.open(app, technique)).setup()

    private fun text(activity: StrategyActivity, id: Int) = activity.view<TextView>(id).text.toString()

    /** Taps the middle of [cell] on the lesson's board. */
    private fun tapCell(activity: StrategyActivity, cell: Int) =
        tap(activity.view<BoardView>(R.id.board), (Grid.col(cell) + 0.5f) / 9f, (Grid.row(cell) + 0.5f) / 9f)

    @Test
    fun homeLeadsToTheStrategies() {
        val home = Robolectric.buildActivity(MainActivity::class.java).setup()
        val tile = home.get().view<TileView>(R.id.tile_strategies)
        assertTrue(tile.contentDescription.toString(), tile.contentDescription.endsWith("23 techniques, from singles to chains"))
        home.get().view<android.view.ViewGroup>(R.id.tabs).getChildAt(MainActivity.TAB_LEARN).performClick()
        capture(home.get(), "42_home_learn")
        // Once some are practised, the tile counts them.
        app.strategies.practise(Technique.FULL_HOUSE)
        app.strategies.practise(Technique.X_WING)
        home.pause().resume()
        assertTrue(tile.contentDescription.endsWith("2 of 23 practised"))
    }

    @Test
    fun theStrategiesAreListedSimplestFirst() {
        app.strategies.practise(Technique.HIDDEN_SINGLE)
        val activity = Robolectric.buildActivity(StrategiesActivity::class.java).setup().get()
        val list = activity.view<LinearLayout>(R.id.strategies)
        val children = (0 until list.childCount).map { list.getChildAt(it) }
        // A header for each tier, then its strategies in order.
        assertEquals(Strategies.TIERS, children.filterIsInstance<SectionHeader>().map { it.text.toString() })
        val rows = children.filter { it !is SectionHeader }
        assertEquals(Technique.entries.map { it.title }, rows.map { it.findViewById<TextView>(R.id.name).text.toString() })
        assertEquals("01", rows[0].findViewById<TextView>(R.id.number).text.toString())
        assertEquals("23", rows[22].findViewById<TextView>(R.id.number).text.toString())
        assertEquals(Strategies.of(Technique.POINTING).summary, rows[3].findViewById<TextView>(R.id.summary).text.toString())
        // A practised strategy has its number on a blue block.
        val palette = Palette(activity)
        assertEquals(palette.accent, (rows[1].findViewById<View>(R.id.number).background as ColorDrawable).color)
        assertFalse(rows[0].findViewById<View>(R.id.number).background is ColorDrawable)
        assertTrue(rows[1].contentDescription.toString().startsWith("2. Hidden Single, practised."))
        capture(activity, "43_strategies")
        rows[9].performClick()
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(StrategyActivity::class.java.name, intent.component?.className)
        assertEquals("X_WING", intent.getStringExtra("technique"))
        val scroll = activity.view<ScrollView>(R.id.scroll)
        scroll.scrollTo(0, rows[14].top)
        capture(activity, "43b_strategies_advanced")
    }

    @Test
    fun aLessonStartsWithAChallenge() {
        val controller = lesson(Technique.HIDDEN_SINGLE)
        val activity = controller.get()
        val strategy = Strategies.of(Technique.HIDDEN_SINGLE)
        val lesson = strategy.examples[0].lesson
        assertEquals("Singles · 02 of 23", text(activity, R.id.kicker))
        assertEquals("Hidden Single", text(activity, R.id.strategy_title))
        assertEquals(strategy.summary, text(activity, R.id.summary))
        assertEquals(strategy.howItWorks, text(activity, R.id.how))
        assertEquals("Example 1 of 3", text(activity, R.id.example_header))
        assertEquals("Your turn", text(activity, R.id.stage))
        assertEquals(lesson.challenge.text, text(activity, R.id.frame_text))
        assertEquals("Show me", text(activity, R.id.step_next))
        assertEquals(View.INVISIBLE, activity.view<View>(R.id.step_back).visibility)
        assertEquals(View.GONE, activity.view<View>(R.id.feedback).visibility)
        capture(activity, "44_strategy_try")

        // A wrong cell gets a nudge, and the challenge stays.
        val answer = lesson.answers.single()
        val wrong = (0 until 81).first { it != answer && strategy.examples[0].values[it] == 0 }
        tapCell(activity, wrong)
        assertEquals(View.VISIBLE, activity.view<View>(R.id.feedback).visibility)
        assertEquals(activity.getString(R.string.strategy_wrong), text(activity, R.id.feedback))
        assertEquals("Your turn", text(activity, R.id.stage))
        assertFalse(Technique.HIDDEN_SINGLE in app.strategies.practised())
        capture(activity, "44b_strategy_wrong")

        // The right one is praised, practised, and explained.
        tapCell(activity, answer)
        assertEquals("Step 1 of 3", text(activity, R.id.stage))
        assertEquals("Well spotted! ${lesson.outcome(answer)}", text(activity, R.id.feedback))
        assertEquals(lesson.frames[0].text, text(activity, R.id.frame_text))
        assertTrue(Technique.HIDDEN_SINGLE in app.strategies.practised())
        activity.view<View>(R.id.step_next).performClick()
        assertEquals("Step 2 of 3", text(activity, R.id.stage))
        capture(activity, "44c_strategy_step")
        activity.view<View>(R.id.step_next).performClick()
        assertEquals("Step 3 of 3", text(activity, R.id.stage))
        assertEquals("Next example", text(activity, R.id.step_next))
        capture(activity, "44d_strategy_result")

        // The board only answers the challenge.
        tapCell(activity, wrong)
        assertEquals("Step 3 of 3", text(activity, R.id.stage))

        // A turn of the phone keeps the place.
        controller.recreate()
        val again = controller.get()
        assertEquals("Step 3 of 3", text(again, R.id.stage))
        assertTrue(text(again, R.id.feedback).startsWith("Well spotted!"))

        // On to the next example, from its challenge.
        again.view<View>(R.id.step_next).performClick()
        assertEquals("Example 2 of 3", text(again, R.id.example_header))
        assertEquals("Your turn", text(again, R.id.stage))
        assertEquals(strategy.examples[1].lesson.challenge.text, text(again, R.id.frame_text))
    }

    @Test
    fun showMeWalksThrough() {
        val activity = lesson(Technique.X_WING).get()
        val lesson = Strategies.of(Technique.X_WING).examples[0].lesson
        activity.view<View>(R.id.step_next).performClick()
        assertEquals("Step 1 of 3", text(activity, R.id.stage))
        // Shown, not found: no praise, and not practised.
        assertEquals(View.GONE, activity.view<View>(R.id.feedback).visibility)
        assertFalse(Technique.X_WING in app.strategies.practised())
        activity.view<View>(R.id.step_back).performClick()
        assertEquals("Your turn", text(activity, R.id.stage))
        repeat(3) { activity.view<View>(R.id.step_next).performClick() }
        assertEquals(lesson.frames.last().text, text(activity, R.id.frame_text))
        capture(activity, "45_strategy_x_wing")
        // Another example from anywhere.
        activity.view<View>(R.id.another).performClick()
        assertEquals("Example 2 of 3", text(activity, R.id.example_header))
        repeat(2) { activity.view<View>(R.id.another).performClick() }
        assertEquals("Example 1 of 3", text(activity, R.id.example_header))
    }

    @Test
    fun everyStrategyHasItsLesson() {
        for (strategy in Strategies.ALL) {
            val activity = lesson(strategy.technique).get()
            assertEquals(strategy.technique.title, text(activity, R.id.strategy_title))
            assertEquals(strategy.howToSpot, text(activity, R.id.spot))
            val board = activity.view<BoardView>(R.id.board)
            assertTrue(board.width > 0 && board.width == board.height)
            // Each example's answer is found by tapping it.
            val answer = strategy.examples[0].lesson.answers.first()
            tapCell(activity, answer)
            assertEquals(strategy.technique.title, "Step 1 of 3", text(activity, R.id.stage))
            // The way on to the neighbours, none past either end.
            val previous = activity.view<TextView>(R.id.previous)
            val following = activity.view<TextView>(R.id.following)
            assertEquals(strategy.number > 1, previous.visibility == View.VISIBLE)
            assertEquals(strategy.number < 23, following.visibility == View.VISIBLE)
            if (strategy.technique == Technique.XY_CHAIN || strategy.technique == Technique.SIMPLE_COLORING) {
                repeat(2) { activity.view<View>(R.id.step_next).performClick() }
                capture(activity, "46_strategy_${strategy.technique.name.lowercase()}")
            }
        }
        assertEquals(Technique.entries.toSet(), app.strategies.practised())
    }

    @Test
    fun neighboursOpenInPlace() {
        val activity = lesson(Technique.POINTING).get()
        assertEquals("‹ Naked Single", text(activity, R.id.previous))
        assertEquals("Claiming ›", text(activity, R.id.following))
        activity.view<View>(R.id.following).performClick()
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals("CLAIMING", intent.getStringExtra("technique"))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun darkLesson() {
        app.settings.theme = Settings.Theme.DARK
        val activity = lesson(Technique.XY_WING).get()
        activity.view<View>(R.id.step_next).performClick()
        activity.view<View>(R.id.step_next).performClick()
        capture(activity, "47_strategy_dark")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscapeLesson() {
        val activity = lesson(Technique.NAKED_PAIR).get()
        val board = activity.view<BoardView>(R.id.board)
        // The walkthrough sits beside the board, which fits the height of the screen.
        val boardAt = IntArray(2).also { board.getLocationInWindow(it) }
        val textAt = IntArray(2).also { activity.view<View>(R.id.frame_text).getLocationInWindow(it) }
        assertTrue(textAt[0] >= boardAt[0] + board.width)
        assertTrue(board.height < activity.window.decorView.height * 0.8)
        activity.view<View>(R.id.step_next).performClick()
        activity.view<ScrollView>(R.id.scroll).scrollTo(0, activity.view<View>(R.id.example_row).top)
        capture(activity, "48_strategy_landscape")
    }

}
