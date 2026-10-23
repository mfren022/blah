package com.ultimatesudoku.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Insets
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.TextView
import com.ultimatesudoku.app.data.Settings
import com.ultimatesudoku.app.ui.LevelMapView
import com.ultimatesudoku.app.ui.PixelArtView
import com.ultimatesudoku.app.ui.SquareGlowView
import com.ultimatesudoku.core.LevelMap
import org.junit.Assert.assertEquals
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class StoryTest : ScreenTest() {

    private val app get() = RuntimeEnvironment.getApplication() as SudokuApp

    private fun chapter(index: Int) =
        Robolectric.buildActivity(ChapterActivity::class.java, ChapterActivity.open(app, index)).setup().get()

    private fun findText(root: View, text: String): View? {
        if (root is TextView && root.text.toString() == text) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) findText(root.getChildAt(i), text)?.let { return it }
        return null
    }

    @Test
    fun chaptersOpenTheirMaps() {
        val story = Robolectric.buildActivity(StoryActivity::class.java).setup().get()
        val rows = story.view<ViewGroup>(R.id.chapters)
        assertEquals(StoryActivity.CHAPTERS.size, rows.childCount)
        rows.getChildAt(3).performClick()
        val intent = shadowOf(story).nextStartedActivity
        assertEquals(ChapterActivity::class.java.name, intent.component?.className)
        assertEquals(3, intent.getIntExtra("chapter", -1))
    }

    @Test
    fun theFirstChapter() {
        val activity = chapter(0)
        assertEquals("Chapter 01", activity.view<TextView>(R.id.kicker).text.toString())
        assertEquals("The Old Quarter", activity.view<TextView>(R.id.chapter_title).text.toString())
        val map = LevelMap.forChapter(0)
        val view = activity.view<LevelMapView>(R.id.map)
        assertEquals(map.levels, view.map!!.levels)
        assertEquals(setOf(1), view.open)
        val summary = activity.view<TextView>(R.id.summary).text.toString()
        assertTrue(summary, summary.startsWith("${map.levels.size} levels · ${map.routes()} routes\nEach level you finish"))
        assertTrue(view.height > 0)
        capture(activity, "33_chapter")
    }

    @Test
    fun theFirstChapterHasItsArt() {
        val activity = chapter(0)
        assertEquals(View.GONE, activity.view<View>(R.id.glow).visibility)
        val art = activity.view<PixelArtView>(R.id.art)
        assertEquals(View.VISIBLE, art.visibility)
        val source = BitmapFactory.decodeResource(activity.resources, R.drawable.art_old_quarter, BitmapFactory.Options().apply { inScaled = false })
        assertEquals(128, source.width)
        // Whole pixels, filling the width.
        val scale = Math.ceil(art.width / 128.0).toInt()
        assertEquals(source.height * scale, art.height)
        val drawn = Bitmap.createBitmap(art.width, art.height, Bitmap.Config.ARGB_8888)
        art.draw(Canvas(drawn))
        val left = (art.width - source.width * scale) / 2
        for ((x, y) in listOf(64 to 10, 100 to 52, 30 to 40, 90 to 70)) {
            // Every screen pixel of an art pixel has that pixel's colour: sharp, not smoothed.
            for (dx in listOf(0, scale - 1)) for (dy in listOf(0, scale - 1)) {
                val sx = left + x * scale + dx
                val sy = y * scale + dy
                assertEquals("art pixel ($x, $y)", source.getPixel(x, y), drawn.getPixel(sx, sy))
            }
        }
        // The bottom row has faded into the page.
        val page = activity.getColor(R.color.story_bg)
        assertEquals(page, drawn.getPixel(art.width / 2, art.height - 1))
        // The title starts below the art.
        val header = activity.view<View>(R.id.header)
        assertTrue(header.top >= art.bottom)
        assertTrue(activity.view<View>(R.id.art).contentDescription.isNotEmpty())
    }

    @Test
    fun theArtRunsBehindTheStatusBar() {
        // As on Android 15, where the app draws under a status bar and a navigation bar.
        val activity = chapter(0)
        val content = activity.view<ViewGroup>(R.id.content)
        val bar = activity.view<View>(R.id.top_bar)
        val barHeight = bar.height
        val bottom = content.paddingBottom
        content.dispatchApplyWindowInsets(
            WindowInsets.Builder()
                .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 72, 0, 0))
                .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 63))
                .build(),
        )
        idle()
        assertEquals(0, content.paddingTop)
        assertEquals(bottom + 63, content.paddingBottom)
        assertEquals(72, bar.paddingTop)
        assertEquals(barHeight + 72, bar.height)
        assertEquals(0, activity.view<View>(R.id.art).top)
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun theArtFitsALandscapeScreen() {
        val activity = chapter(0)
        val art = activity.view<PixelArtView>(R.id.art)
        // At most about half the screen tall, so it is centred with the page on either side.
        assertTrue(art.height <= activity.resources.displayMetrics.heightPixels * 0.55f)
        assertEquals(0, art.height % 96)
        capture(activity, "38_chapter_art_landscape")
    }

    @Test
    fun aLockedChapter() {
        val activity = chapter(5)
        assertEquals("Chapter 06 · Locked", activity.view<TextView>(R.id.kicker).text.toString())
        assertTrue(activity.view<LevelMapView>(R.id.map).open.isEmpty())
        val summary = activity.view<TextView>(R.id.summary).text.toString()
        assertTrue(summary, summary.endsWith("Finish The Observatory to open this district."))
        capture(activity, "34_chapter_locked")
    }

    @Test
    fun tappingALevelSaysStoryModeIsComing() {
        val activity = chapter(0)
        val view = activity.view<LevelMapView>(R.id.map)
        // The entrance sits at the top, in the middle lane.
        val map = view.map!!
        val top = view.paddingTop + view.resources.displayMetrics.density * (7 + 30)
        tap(view, 0.5f, top / view.height)
        assertNotNull(findText(activity.window.decorView, activity.getString(R.string.story_locked)))
        assertEquals(1, map.entrance.number)
    }

    @Test
    fun theGlowIsBrightestNearTheTopRight() {
        val activity = chapter(1)
        val glow = activity.view<SquareGlowView>(R.id.glow)
        val bitmap = Bitmap.createBitmap(glow.width, glow.height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        glow.draw(Canvas(bitmap))
        val screenHeight = activity.window.decorView.height
        fun brightest(cx: Float, cy: Float): Int {
            // The brightest pixel near a point, so gaps between squares don't matter.
            var best = 0
            val x0 = (cx * glow.width).toInt()
            val y0 = (cy * screenHeight).toInt()
            for (x in x0 - 30..x0 + 30) for (y in y0 - 30..y0 + 30) {
                if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) continue
                best = maxOf(best, Color.red(bitmap.getPixel(x, y)))
            }
            return best
        }
        assertTrue(brightest(0.82f, 0.03f) > 240)
        assertTrue(brightest(0.5f, 0.25f) in 15..120)
        assertEquals(0, brightest(0.5f, 0.52f))
        assertEquals(0, brightest(0.1f, 0.7f))
        // Along a lit row: squares 5.5dp wide, whole pixels, 1dp apart, about 62 across a 411dp screen.
        val density = glow.resources.displayMetrics.density
        val lightX = (0.82f * glow.width).toInt()
        val row = (0 until bitmap.height).first { Color.red(bitmap.getPixel(lightX, it)) > 0 } + 2
        val lit = (0 until bitmap.width).map { Color.red(bitmap.getPixel(it, row)) > 0 }
        val runs = ArrayList<Pair<Boolean, Int>>()
        for (on in lit) if (runs.isNotEmpty() && runs.last().first == on) runs[runs.size - 1] = on to runs.last().second + 1 else runs += on to 1
        val inner = runs.drop(1).dropLast(1)
        assertEquals(setOf(Math.round(density)), inner.filter { !it.first }.map { it.second }.toSet())
        assertTrue(inner.filter { it.first }.all { it.second in 16..18 })
        // The whole top row is lit, however faintly, so its squares can be counted.
        val across = runs.count { it.first }
        assertTrue("$across squares across", across in 60..64)
    }

    @Test
    fun darkTheme() {
        app.settings.theme = Settings.Theme.DARK
        val activity = chapter(0)
        assertEquals(activity.getColor(R.color.story_bg_dark), (activity.view<View>(R.id.scroll).background as android.graphics.drawable.ColorDrawable).color)
        capture(activity, "35_chapter_dark")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscape() {
        capture(chapter(8), "36_chapter_landscape")
    }

    @Test
    fun theLastChapterScrollsToItsFinalLevel() {
        val activity = chapter(8)
        val scroll = activity.view<android.widget.ScrollView>(R.id.scroll)
        scroll.fullScroll(View.FOCUS_DOWN)
        idle()
        capture(activity, "37_chapter_scrolled")
    }
}
