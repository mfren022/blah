package com.ultimatesudoku.app

import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import com.ultimatesudoku.app.data.Settings
import com.ultimatesudoku.app.ui.TabBar
import com.ultimatesudoku.app.ui.TileView
import com.ultimatesudoku.core.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HomeTabsTest : ScreenTest() {

    private val app get() = RuntimeEnvironment.getApplication() as SudokuApp

    private val pages = listOf(R.id.page_play, R.id.page_learn, R.id.page_records, R.id.page_settings)

    private fun home() = Robolectric.buildActivity(MainActivity::class.java).setup()

    private fun MainActivity.tab(index: Int) = view<TabBar>(R.id.tabs).getChildAt(index)

    /** Which page is showing, by tab. */
    private fun MainActivity.page() = pages.indexOfFirst { view<View>(it).visibility == View.VISIBLE }

    @Test
    fun fourTabsWithIcons() {
        val activity = home().get()
        val tabs = activity.view<TabBar>(R.id.tabs)
        assertEquals(listOf("Play", "Learn", "Records", "Settings"), (0 until tabs.childCount).map { tabs.getChildAt(it).contentDescription })
        for (i in 0 until tabs.childCount) {
            val item = tabs.getChildAt(i) as ViewGroup
            assertTrue(item.getChildAt(0) is android.widget.ImageView)
        }
        // Play first, and only its page showing.
        assertEquals(MainActivity.TAB_PLAY, tabs.selected)
        assertEquals(MainActivity.TAB_PLAY, activity.page())
        assertEquals(1, pages.count { activity.view<View>(it).visibility == View.VISIBLE })
        assertTrue(tabs.getChildAt(0).isSelected)
        assertFalse(tabs.getChildAt(1).isSelected)
    }

    @Test
    fun eachTabShowsItsPage() {
        val activity = home().get()
        activity.tab(MainActivity.TAB_LEARN).performClick()
        assertEquals(MainActivity.TAB_LEARN, activity.page())
        assertEquals(View.VISIBLE, activity.view<View>(R.id.tile_strategies).let { (it.parent as View).visibility })
        // Statistics now sits under Records, beside the solved boards.
        activity.tab(MainActivity.TAB_RECORDS).performClick()
        assertEquals(MainActivity.TAB_RECORDS, activity.page())
        val stats = activity.view<TileView>(R.id.tile_stats)
        assertEquals(R.id.page_records, (stats.parent as View).id)
        assertEquals(R.id.page_records, (activity.view<View>(R.id.tile_history).parent as View).id)
        assertTrue(stats.contentDescription.endsWith("Wins, streaks and best times, by difficulty"))
        capture(activity, "50_home_records")
        activity.tab(MainActivity.TAB_SETTINGS).performClick()
        assertEquals(MainActivity.TAB_SETTINGS, activity.page())
        assertTrue(activity.view<ViewGroup>(R.id.page_settings).childCount > 10)
    }

    @Test
    fun statisticsSummariseTheGamesWon() {
        app.stats.started(Difficulty.EASY)
        app.stats.started(Difficulty.HARD)
        app.stats.won(Difficulty.EASY, 200_000, perfect = true)
        val activity = home().get()
        assertTrue(activity.view<TileView>(R.id.tile_stats).contentDescription.endsWith("Won 1 of 2 games"))
    }

    @Test
    fun aNewTabStartsAtTheTop() {
        val activity = home().get()
        val scroll = activity.view<ScrollView>(R.id.scroll)
        scroll.scrollTo(0, 400)
        activity.tab(MainActivity.TAB_SETTINGS).performClick()
        idle()
        assertEquals(0, scroll.scrollY)
    }

    @Test
    fun backGoesToPlayBeforeLeaving() {
        val activity = home().get()
        activity.tab(MainActivity.TAB_RECORDS).performClick()
        @Suppress("DEPRECATION")
        activity.onBackPressed()
        assertEquals(MainActivity.TAB_PLAY, activity.page())
        assertFalse(activity.isFinishing)
        @Suppress("DEPRECATION")
        activity.onBackPressed()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun aNewThemeKeepsTheSettingsTab() {
        val controller = home()
        controller.get().tab(MainActivity.TAB_SETTINGS).performClick()
        // Choosing Dark recreates the screen to show it.
        app.settings.theme = Settings.Theme.DARK
        controller.recreate()
        val activity = controller.get()
        assertEquals(MainActivity.TAB_SETTINGS, activity.view<TabBar>(R.id.tabs).selected)
        assertEquals(MainActivity.TAB_SETTINGS, activity.page())
        capture(activity, "51_home_settings_dark")
    }
}
