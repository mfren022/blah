package com.ultimatesudoku.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Insets
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import com.ultimatesudoku.app.data.GameStore
import com.ultimatesudoku.app.data.Settings
import com.ultimatesudoku.app.ui.BoardView
import com.ultimatesudoku.app.ui.LevelMapView
import com.ultimatesudoku.app.ui.NumberPadView
import com.ultimatesudoku.app.ui.Palette
import com.ultimatesudoku.app.ui.SquareGlowView
import com.ultimatesudoku.core.Difficulty
import com.ultimatesudoku.core.Game
import com.ultimatesudoku.core.GameMode
import com.ultimatesudoku.core.Grid
import com.ultimatesudoku.core.LevelMap
import kotlin.math.max
import kotlin.math.min
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
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class StoryTest : ScreenTest() {

    private val app get() = RuntimeEnvironment.getApplication() as SudokuApp

    private fun chapter(index: Int) =
        Robolectric.buildActivity(ChapterActivity::class.java, ChapterActivity.open(app, index)).setup().get()

    /** Every TextView under [root], in layout order. */
    private fun texts(root: View): List<TextView> = when (root) {
        is TextView -> listOf(root)
        is ViewGroup -> (0 until root.childCount).flatMap { texts(root.getChildAt(it)) }
        else -> emptyList()
    }

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
    fun theChapterListCountsFinishedLevels() {
        // Three levels into the first chapter, and the second finished.
        for (level in 1..3) app.story.finish(0, level)
        for (level in LevelMap.forChapter(1).levels) app.story.finish(1, level.number)
        val story = Robolectric.buildActivity(StoryActivity::class.java).setup().get()
        val rows = story.view<ViewGroup>(R.id.chapters)
        val palette = Palette(story)
        StoryActivity.CHAPTERS.forEachIndexed { i, chapter ->
            val row = rows.getChildAt(i)
            val total = LevelMap.forChapter(i).levels.size
            val finished = when (i) {
                0 -> 3
                1 -> total
                else -> 0
            }
            // Just its number, its name and the count, on a darker block, or blue once complete.
            assertEquals(listOf("%02d".format(i + 1), chapter.title, "$finished/$total"), texts(row).map { it.text.toString() })
            val badge = row.findViewById<TextView>(R.id.progress)
            val complete = finished == total
            assertEquals(if (complete) palette.accent else palette.surfacePressed, (badge.background as ColorDrawable).color)
            assertEquals(if (complete) palette.onAccent else palette.inkMuted, badge.currentTextColor)
            // Then the chevron on the right, every chapter being open.
            val icon = row.findViewById<ImageView>(R.id.state)
            assertEquals(R.drawable.ic_chevron, shadowOf(icon.drawable).createdFromResId)
            assertEquals(255, icon.imageAlpha)
        }
        capture(story, "15b_story_progress")
        // No chapter is locked, but if one were, its lock would be a faint red.
        val icon = rows.getChildAt(2).findViewById<ImageView>(R.id.state)
        story.showState(icon, open = false)
        assertEquals(R.drawable.ic_lock, shadowOf(icon.drawable).createdFromResId)
        assertEquals(128, icon.imageAlpha)
        capture(story, "15c_story_lock")
        // A chapter's own screen counts the same.
        val levels = chapter(0).view<TextView>(R.id.levels).text.toString()
        assertEquals("Levels (3/${LevelMap.forChapter(0).levels.size})", levels)
    }

    @Test
    fun theFirstChapter() {
        val activity = chapter(0)
        assertEquals("Chapter 01", activity.view<TextView>(R.id.kicker).text.toString())
        assertEquals("The Pond", activity.view<TextView>(R.id.chapter_title).text.toString())
        assertTrue(activity.view<TextView>(R.id.story).text.startsWith("Anna lives in a hut on stilts"))
        val map = LevelMap.forChapter(0)
        val view = activity.view<LevelMapView>(R.id.map)
        assertEquals(map.levels, view.map!!.levels)
        assertEquals(setOf(1), view.open)
        // Under the story, only the levels header, counting those finished out of all.
        assertEquals("Levels (0/${map.levels.size})", activity.view<TextView>(R.id.levels).text.toString())
        assertEquals(listOf(R.id.kicker, R.id.chapter_title, R.id.story, R.id.levels), texts(activity.view(R.id.header)).map { it.id })
        // Its levels can be played, so no word of story mode still being built.
        assertEquals(View.GONE, activity.view<View>(R.id.footnote).visibility)
        assertTrue(view.height > 0)
        captureRisen(activity, "33_chapter")
    }

    @Test
    fun theFirstChapterHasItsArt() {
        val activity = chapter(0)
        val glow = activity.view<SquareGlowView>(R.id.glow)
        val art = checkNotNull(glow.art)
        // A painting twenty times the grid's size each way, which the squares look onto.
        assertEquals(64 * 20, art.width)
        assertEquals(72 * 20, art.height)
        assertTrue(glow.contentDescription.isNotEmpty())
        val page = activity.getColor(R.color.story_bg)
        val drawn = Bitmap.createBitmap(glow.width, glow.height, Bitmap.Config.ARGB_8888)
        drawn.eraseColor(page)
        glow.draw(Canvas(drawn))
        // The grid: 5.5dp squares 1dp apart, as many as fit across, centred, the art anchored top-right.
        val density = glow.resources.displayMetrics.density
        val gap = Math.round(density)
        val columns = (glow.width + gap) / (Math.round(5.5f * density) + gap)
        val size = (glow.width - gap * (columns - 1)) / columns
        val pitch = size + gap
        val left = (glow.width - columns * pitch + gap) / 2
        val screenHeight = activity.window.decorView.height
        val cx = glow.width * 0.82f
        val cy = screenHeight * 0.03f
        val radius = Math.hypot((cx - glow.width / 2f).toDouble(), (cy - screenHeight / 2f).toDouble()).toFloat()
        // The painting lies under the grid, a square's worth to each step from square to square, its right
        // edge under the last column: each square shows the part of it underneath.
        val perPixel = art.width / 64f / pitch
        val under = left + (columns - 64) * pitch
        var checked = 0
        for ((column, row) in listOf(columns - 1 to 0, columns - 12 to 3, columns - 10 to 22, columns - 30 to 30, 5 to 60)) {
            val x = left + column * pitch
            val y = left + row * pitch
            val light = 1f - Math.hypot((x + size / 2f - cx).toDouble(), (y + size / 2f - cy).toDouble()).toFloat() / radius
            val pixel = art.getPixel(((x + size / 2 - under) * perPixel).toInt(), ((y + size / 2 - left) * perPixel).toInt())
            val expected = if (light <= 0f) page else blend(page, pixel, light)
            val actual = drawn.getPixel(x + size / 2, y + size / 2)
            for (channel in listOf(Color::red, Color::green, Color::blue)) {
                assertTrue("square ($column, $row)", Math.abs(channel(expected) - channel(actual)) <= 2)
            }
            // And the gap beside it shows the page.
            assertEquals(page, drawn.getPixel(x + size, y + size / 2))
            checked++
        }
        assertEquals(5, checked)
        // Unlike pixel art, a square holds more than one colour, wherever the painting has detail.
        var detailed = 0
        for (row in 0 until 72) {
            for (column in 0 until columns) {
                val x = left + column * pitch
                val y = left + row * pitch
                if (y + size > drawn.height) continue
                val colours = HashSet<Int>()
                for (dy in 0 until size step 4) for (dx in 0 until size step 4) colours += drawn.getPixel(x + dx, y + dy)
                if (colours.size > 4) detailed++
            }
        }
        assertTrue("$detailed squares with detail", detailed > 200)
    }

    @Test
    fun everyChapterHasItsStoryAndPicture() {
        val pictures = HashSet<Int>()
        StoryActivity.CHAPTERS.forEachIndexed { index, chapter ->
            val activity = chapter(index)
            assertEquals(chapter.title, activity.view<TextView>(R.id.chapter_title).text.toString())
            assertEquals(activity.getString(chapter.story), activity.view<TextView>(R.id.story).text.toString())
            val glow = activity.view<SquareGlowView>(R.id.glow)
            val art = checkNotNull(glow.art)
            // Pixel art, one pixel to a square of the 64 by 72 grid, or a painting a whole number of
            // times larger.
            assertEquals(0, art.width % 64)
            assertEquals(art.width / 64 * 72, art.height)
            assertEquals(activity.getString(chapter.artDescription), glow.contentDescription)
            pictures += IntArray(art.width * art.height).also { art.getPixels(it, 0, art.width, 0, 0, art.width, art.height) }.contentHashCode()
            captureRisen(activity, "39_chapter_%d".format(index + 1))
        }
        // Nine different pictures.
        assertEquals(StoryActivity.CHAPTERS.size, pictures.size)
    }

    private fun blend(from: Int, to: Int, t: Float) = Color.rgb(
        Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t),
        Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t),
        Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t),
    )

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
        assertEquals(0, activity.view<View>(R.id.glow).top)
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun theArtInLandscape() {
        captureRisen(chapter(0), "38_chapter_art_landscape")
    }

    @Test
    fun everyChapterIsOpen() {
        for (index in StoryActivity.CHAPTERS.indices) {
            val activity = chapter(index)
            assertEquals("Chapter %02d".format(index + 1), activity.view<TextView>(R.id.kicker).text.toString())
            // Its first level ready to play, once levels can be played.
            val map = activity.view<LevelMapView>(R.id.map)
            assertEquals(setOf(map.map!!.entrance.number), map.open)
            if (index == 5) captureRisen(activity, "34_chapter_6")
        }
    }

    /** The centre of level [number]'s stop on [view], where the map draws it. */
    /** Taps level [number] on [view], where the map draws it. */
    private fun tapLevel(view: LevelMapView, number: Int) {
        val centre = checkNotNull(view.center(number))
        tap(view, centre.x / view.width, centre.y / view.height)
    }

    /** What [view] looks like, drawn on the chapter's page. */
    /** Scrolls a chapter's screen straight to the bottom. */
    private fun bottomOf(activity: Activity) {
        val scroll = activity.view<ScrollView>(R.id.scroll)
        scroll.scrollTo(0, scroll.getChildAt(0).height)
    }

    /** Captures [activity] once any scrolling has settled and the land in view on its map has risen. */
    private fun captureRisen(activity: Activity, name: String) {
        // A frame of the whole screen first, which takes a smooth scroll where it's going, and brings
        // into view the land there.
        idle(500)
        val screen = activity.window.decorView
        screen.draw(Canvas(Bitmap.createBitmap(screen.width, screen.height, Bitmap.Config.ARGB_8888)))
        idle(1500)
        capture(activity, name)
    }

    private fun drawn(view: LevelMapView): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Palette(view.context).bg)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    /** The colour level [number] is filled with on [view], clear of its number. */
    private fun stopColour(view: LevelMapView, number: Int): Int {
        val point = checkNotNull(view.fillPoint(number))
        return drawn(view).getPixel(Math.round(point.x), Math.round(point.y))
    }

    @Test
    fun everyChaptersMapLiesOverItsOwnLandscape() {
        // Told apart by a hash of their pixels: a whole map's pixels are too many to keep nine of.
        val landscapes = HashSet<Int>()
        for (index in StoryActivity.CHAPTERS.indices) {
            val activity = chapter(index)
            val view = activity.view<LevelMapView>(R.id.map)
            assertEquals(index, view.seed)
            // All the land seen, the top and then the rest, and risen.
            drawn(view)
            bottomOf(activity)
            drawn(view)
            idle(2000)
            val bitmap = drawn(view)
            write(bitmap, "42_map_%d".format(index + 1))
            assertLandscape(view, bitmap, "Chapter ${index + 1}")
            landscapes += IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }.contentHashCode()
        }
        assertEquals(StoryActivity.CHAPTERS.size, landscapes.size)
    }

    /**
     * The map in [view], drawn as [bitmap] once its land has risen: levels on the three lanes, each row
     * level and the rows only ever further apart than the plain spacing, much further where the river
     * crosses; a landscape of land, its two sides and water, with land beside every level; and the page
     * above and below.
     */
    private fun assertLandscape(view: LevelMapView, bitmap: Bitmap, chapter: String) {
        val map = view.map!!
        val density = view.resources.displayMetrics.density
        val page = Palette(view.context).bg
        fun darker(by: Float) = Color.rgb(
            Math.round(Color.red(page) * (1 - by)),
            Math.round(Color.green(page) * (1 - by)),
            Math.round(Color.blue(page) * (1 - by)),
        )
        val land = blend(page, Color.WHITE, 0.10f)
        val left = darker(0.25f)
        val right = darker(0.42f)
        val water = darker(0.70f)
        fun luminance(c: Int) = Color.red(c) * 0.3 + Color.green(c) * 0.59 + Color.blue(c) * 0.11
        fun centre(number: Int) = checkNotNull(view.center(number))

        assertEquals(chapter, 3, map.levels.map { centre(it.number).x }.toSet().size)
        assertEquals(chapter, map.rows, map.levels.map { centre(it.number).y }.toSet().size)
        val lines = (0 until map.rows).map { row -> centre(map.levels.first { it.row == row }.number).y }
        val gaps = lines.zipWithNext { a, b -> b - a }
        assertTrue(chapter, gaps.all { it >= 76 * density })
        assertTrue(chapter, gaps.any { it > 110 * density })

        // Land lighter than the page, standing in blocks whose sides are in shade where they fall to the
        // water, much darker than the page.
        val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        for ((name, shade) in listOf("land" to land, "left side" to left, "right side" to right, "water" to water)) {
            assertTrue("$chapter, $name", pixels.count { it == shade } > 500)
        }

        // Every level stands on land: just beside its stop there's the land's top, and in the hairlines
        // between tiles the shaded sides of those behind, and nothing darker, as water would be.
        for (level in map.levels) {
            val at = centre(level.number)
            val half = (if (level == map.final) 30 else 22) * density
            // Up to 5dp out: the ring around a level ready to play is 6dp out and more.
            val beside = (2..5).flatMap { dx ->
                (-10..10).flatMap { dy ->
                    val y = Math.round(at.y + dy * half / 20)
                    listOf(bitmap.getPixel(Math.round(at.x - half - dx * density), y), bitmap.getPixel(Math.round(at.x + half + dx * density), y))
                }
            }
            assertEquals("$chapter, ${level.number}", land, beside.maxBy { luminance(it) })
            // (Halfway down to water, to allow the soft edges between shades.)
            assertTrue("$chapter, ${level.number}", beside.all { luminance(it) > (luminance(right) + luminance(water)) / 2 })
        }
        // Faded into the page above the levels and below them.
        assertEquals(chapter, page, bitmap.getPixel(view.width / 2, 0))
        assertEquals(chapter, page, bitmap.getPixel(view.width / 2, view.height - 1))
        assertTrue(chapter, centre(1).y > 64 * density)
    }

    @Test
    fun theLandRisesOutOfTheWaterAsItComesIntoView() {
        val activity = chapter(0)
        val view = activity.view<LevelMapView>(R.id.map)
        val density = view.resources.displayMetrics.density
        val side = blend(Palette(activity).bg, Color.BLACK, 0.25f)
        fun rows(bitmap: Bitmap, top: Int, bottom: Int) =
            IntArray(bitmap.width * (bottom - top)).also { bitmap.getPixels(it, 0, bitmap.width, 0, top, bitmap.width, bottom - top) }
        fun sides(bitmap: Bitmap, top: Int, bottom: Int) = rows(bitmap, top, bottom).count { it == side }
        val shown = Rect().also { view.getLocalVisibleRect(it) }
        val below = shown.bottom + Math.round(8 * density)
        assertTrue(below < view.height)
        // Just come into view, the land lies flat in the water; a moment later it has risen, its sides
        // in shade showing, where it's in view, and nothing below has moved.
        val flat = drawn(view)
        // (Frames of it rising, the part of the map in view, to look at.)
        for (frame in 1..12) {
            idle(70)
            val bitmap = drawn(view)
            write(Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, shown.bottom), "43_rise_%02d".format(frame))
        }
        idle(2000)
        val risen = drawn(view)
        assertTrue("${sides(flat, 0, shown.bottom)} flat, ${sides(risen, 0, shown.bottom)} risen in ${shown.toShortString()}", sides(risen, 0, shown.bottom) > sides(flat, 0, shown.bottom) + 1000)
        val a = rows(risen, below, view.height)
        val b = rows(flat, below, view.height)
        val firstDiff = a.indices.firstOrNull { a[it] != b[it] }
        assertTrue("first change at row ${firstDiff?.let { below + it / view.width }} of ${view.height}, shown ${shown.toShortString()} below $below", firstDiff == null)
        // Scrolled to, the rest rises too.
        bottomOf(activity)
        drawn(view)
        idle(2000)
        assertTrue(sides(drawn(view), below, view.height) > sides(risen, below, view.height) + 1000)
    }

    @Test
    fun thePondsRowsAreFurtherApartWhereTheRiverCrosses() {
        val pond = chapter(0).view<LevelMapView>(R.id.map)
        val density = pond.resources.displayMetrics.density
        // The river runs down the middle past rows 1 and 2, and 4 and 5, and bends round 1, 6 and the
        // final level: the rows either side of each of those are far apart, the others not so far.
        val lines = listOf(1, 2, 3, 6, 7, 8, 11).map { checkNotNull(pond.center(it)).y }
        val gaps = lines.zipWithNext { a, b -> (b - a) / density }
        for (row in listOf(0, 2, 3, 5)) assertTrue("$row", gaps[row] > 118)
        for (row in listOf(1, 4)) assertTrue("$row", gaps[row] < 88)
    }

    @Test
    fun theOtherChaptersAreStillComing() {
        val activity = chapter(1)
        assertEquals(View.VISIBLE, activity.view<View>(R.id.footnote).visibility)
        tapLevel(activity.view(R.id.map), 1)
        assertNotNull(findText(activity.window.decorView, activity.getString(R.string.story_locked)))
        assertEquals(null, shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun playingThePond() {
        val controller = Robolectric.buildActivity(ChapterActivity::class.java, ChapterActivity.open(app, 0)).setup()
        val activity = controller.get()
        val view = activity.view<LevelMapView>(R.id.map)
        // To begin with, only the entrance.
        assertEquals(setOf(1), view.open)
        assertEquals("Levels (0/11)", activity.view<TextView>(R.id.levels).text.toString())
        // A level further on stays shut until one leading to it is finished.
        tapLevel(view, 2)
        assertNotNull(findText(activity.window.decorView, activity.getString(R.string.level_locked)))
        assertEquals(null, shadowOf(activity).nextStartedActivity)
        // The entrance starts its fixed puzzle.
        tapLevel(view, 1)
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(GameActivity::class.java.name, intent.component?.className)
        assertEquals(listOf("story", 0, 1), listOf(intent.getStringExtra("new"), intent.getIntExtra("chapter", -1), intent.getIntExtra("level", -1)))
        // Finishing it opens both routes on; finishing one of them through opens level 6.
        app.story.finish(0, 1)
        controller.pause().resume()
        assertEquals(setOf(1), view.finished)
        assertEquals(setOf(2, 4), view.open)
        assertEquals("Levels (1/11)", activity.view<TextView>(R.id.levels).text.toString())
        for (level in listOf(2, 3)) app.story.finish(0, level)
        controller.pause().resume()
        assertEquals(setOf(4, 6), view.open)
        // Finished levels are white, those ready to play blue, and the rest dim.
        val ready = activity.getColor(R.color.story_level_ready)
        assertEquals(Palette(activity).accent, stopColour(view, 3))
        assertEquals(ready, stopColour(view, 4))
        assertEquals(ready, stopColour(view, 6))
        assertTrue(stopColour(view, 5) !in setOf(Palette(activity).accent, ready))
        idle(3000) // until the snack from the shut level has gone
        captureRisen(activity, "40_pond_progress")
        // On down the right-hand route of the second half, to the final level.
        for (level in listOf(6, 9, 10)) app.story.finish(0, level)
        controller.pause().resume()
        assertEquals(setOf(4, 7, 11), view.open)
        assertEquals(activity.getColor(R.color.story_level_ready), stopColour(view, 11))
        assertEquals("Levels (6/11)", activity.view<TextView>(R.id.levels).text.toString())
        activity.view<android.widget.ScrollView>(R.id.scroll).fullScroll(View.FOCUS_DOWN)
        captureRisen(activity, "41_pond_final_open")
    }

    @Test
    fun winningAStoryLevel() {
        val activity = Robolectric.buildActivity(GameActivity::class.java, GameActivity.newStory(app, 0, 1)).setup().get()
        val game = checkNotNull(app.store.game(GameStore.Slot.STORY))
        assertEquals(GameMode.STORY, game.mode)
        assertEquals(Difficulty.EASY, game.difficulty)
        assertEquals("The Pond · Level 1", activity.view<TextView>(R.id.mode_label).text.toString())
        val board = activity.view<BoardView>(R.id.board)
        val pad = activity.view<NumberPadView>(R.id.numpad)
        for (cell in 0 until 81) {
            if (game.givens[cell] != 0) continue
            tap(board, (Grid.col(cell) + 0.5f) / 9f, (Grid.row(cell) + 0.5f) / 9f)
            tap(pad, (game.solution[cell] - 0.5f) / 9f, 0.5f)
        }
        idle(1500)
        assertEquals(Game.Status.WON, game.status)
        // It counts as finished, and the win screen leads back to the chapter.
        assertEquals(setOf(1), app.story.finished(0))
        val back = activity.view<TextView>(R.id.win_primary)
        assertEquals(activity.getString(R.string.story_back), back.text.toString())
        back.performClick()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun theFinalLevelIsMedium() {
        val activity = Robolectric.buildActivity(GameActivity::class.java, GameActivity.newStory(app, 0, 11)).setup().get()
        assertEquals(Difficulty.MEDIUM, app.store.game(GameStore.Slot.STORY)!!.difficulty)
        assertEquals("The Pond · Final level", activity.view<TextView>(R.id.mode_label).text.toString())
    }

    @Test
    fun switchingLevelsAsksFirst() {
        app.story.finish(0, 1)
        // Level 4 under way.
        Robolectric.buildActivity(GameActivity::class.java, GameActivity.newStory(app, 0, 4)).setup()
        val saved = checkNotNull(app.store.game(GameStore.Slot.STORY))
        val cell = (0 until 81).first { saved.givens[it] == 0 }
        saved.enter(cell, saved.solution[cell], true, true)
        app.store.save(GameStore.Slot.STORY, saved)
        val activity = chapter(0)
        val view = activity.view<LevelMapView>(R.id.map)
        // Going back to level 4 carries on with it...
        tapLevel(view, 4)
        val resume = shadowOf(activity).nextStartedActivity
        assertEquals(null, resume.getStringExtra("new"))
        assertEquals("STORY", resume.getStringExtra("slot"))
        // ...and starting level 2 instead asks first.
        tapLevel(view, 2)
        assertEquals(null, shadowOf(activity).nextStartedActivity)
        assertNotNull(findText(ShadowDialog.getLatestDialog().window!!.decorView, activity.getString(R.string.level_replace_title)))
    }

    @Test
    fun theGlowIsBrightestNearTheTopRight() {
        // The grid as it is without a picture: plain white squares under the light.
        val activity = chapter(1)
        val glow = activity.view<SquareGlowView>(R.id.glow)
        glow.art = null
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
        captureRisen(activity, "35_chapter_dark")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscape() {
        captureRisen(chapter(8), "36_chapter_landscape")
    }

    @Test
    fun theLastChapterScrollsToItsFinalLevel() {
        val activity = chapter(8)
        val scroll = activity.view<android.widget.ScrollView>(R.id.scroll)
        scroll.fullScroll(View.FOCUS_DOWN)
        idle()
        captureRisen(activity, "37_chapter_scrolled")
    }
}
