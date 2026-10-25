package com.draftpad.core

import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadmeTest {
    private val ink = Colors.parse("#1F1F1F")

    @Test
    fun summarisesTheSketch() {
        val draft = Draft(
            id = "x",
            title = "Main menu",
            notes = "Big play button.\nStore and settings below it.",
            modified = 1761401102000,
            elements = listOf(
                Stroke(floatArrayOf(0f, 0f, 5f, 5f), ink, 4f),
                Stroke(floatArrayOf(0f, 0f, 5f, 5f), ink, 4f),
                Shape(ShapeKind.RECT, 20f, 100f, 340f, 160f, ink, 4f),
                Label("Settings", 200f, 300f, 20f, ink),
                Label("Play", 140f, 140f, 28f, ink),
                Label("Store", 40f, 300f, 20f, ink),
            ),
        )
        val md = Readme.write(draft, Box(0f, 0f, 360f, 640f), 900, 1600, listOf(Readme.IMAGE, Readme.VECTOR, Readme.SOURCE))
        assertTrue(md, md.startsWith("# Main menu\n\nBig play button.\nStore and settings below it.\n\n![Main menu](sketch.png)\n"))
        assertTrue(md, md.contains("- **Contents:** 2 pen strokes, 1 rectangle and 3 text labels\n"))
        // Reading order: top to bottom, then left to right.
        val play = md.indexOf("\"Play\" at (140, 113)")
        val store = md.indexOf("\"Store\" at (40, 281)")
        val settings = md.indexOf("\"Settings\" at (200, 281)")
        assertTrue(md, play in 0 until store && store < settings)
        assertTrue(md, md.contains("- **Size:** 360 × 640 canvas units (1 unit is 1dp on the phone); `sketch.png` is 900 × 1600 px\n"))
        assertTrue(md, md.contains("- **Updated:** 2025-10-25 14:05 UTC\n"))
        assertTrue(md, md.contains("- `sketch.svg`: the same drawing as vectors"))
    }

    @Test
    fun describesEmptyAndImageOnlySketches() {
        assertEquals("nothing drawn yet", Readme.contents(Draft(id = "x")))
        val overImage = Draft(id = "x", background = Background("background.jpg", 0f, 0f, 1f, 1f),
            elements = listOf(Shape(ShapeKind.ARROW, 0f, 0f, 1f, 1f, ink, 2f)))
        assertEquals("1 arrow, over an image", Readme.contents(overImage))
        val md = Readme.write(overImage, Box(0f, 0f, 1f, 1f), 1, 1, listOf(Readme.IMAGE, "background.jpg"))
        assertTrue(md, md.contains("- `background.jpg`: the image the sketch is drawn over\n"))
        assertTrue(md, md.startsWith("# Untitled sketch\n\n![Untitled sketch](sketch.png)"))
    }

    @Test
    fun escapesMarkdownInTitlesAndLabels() {
        val draft = Draft(id = "x", title = "my_new *idea*", elements = listOf(Label("[OK] #1", 0f, 20f, 20f, ink)))
        val md = Readme.write(draft, Box(0f, 0f, 100f, 100f), 1, 1, emptyList())
        assertTrue(md, md.startsWith("# my\\_new \\*idea\\*\n"))
        assertTrue(md, md.contains("\"\\[OK\\] \\#1\""))
    }

    @Test
    fun namesAreFileSafe() {
        assertEquals("cafe-menu-v2", Names.slug("Café menu, v2!"))
        assertEquals("sketch", Names.slug("✏️ !!"))
        assertEquals("a".repeat(48), Names.slug("a".repeat(60)))
        assertEquals("sketches/ideas", Names.cleanFolder(" /sketches//ideas/ "))
        assertEquals("drafts", Names.cleanFolder("../"))
        val d = Draft(id = "x", title = "Castle keep", created = 1761400992000)
        assertEquals("drafts/2025-10-25-castle-keep", Names.remoteFolder("drafts", d, ZoneOffset.UTC))
        val taken = setOf("20251025-140312", "20251025-140312-2")
        assertEquals("20251025-140312-3", Names.newId(1761400992000, ZoneId.of("UTC")) { it in taken })
    }
}
