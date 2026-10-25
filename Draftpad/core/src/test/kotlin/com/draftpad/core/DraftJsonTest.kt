package com.draftpad.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftJsonTest {
    private val ink = Colors.parse("#1F1F1F")
    private val red = Colors.parse("#E53935")

    private val draft = Draft(
        id = "20261025-140312",
        title = "Castle \"keep\" idea",
        notes = "Taller tower\nflags on both sides — ünïcode ✓ \u0001",
        created = 1761400992000,
        modified = 1761401102000,
        elements = listOf(
            Stroke(floatArrayOf(10f, 20f, 14.54f, 22.06f, -3f, 0f), ink, 4f),
            Shape(ShapeKind.LINE, 0f, 0f, 10f, 10f, red, 2f),
            Shape(ShapeKind.ARROW, 0f, 0f, 10f, 10f, red, 2f),
            Shape(ShapeKind.RECT, 0f, 0f, 100f, 50f, red, 8f),
            Shape(ShapeKind.ELLIPSE, 5f, 5f, 50f, 25f, ink, 4f),
            Label("Play\nnow", 12f, 40f, 20f, ink),
        ),
        background = Background("background.jpg", 0f, 0f, 360f, 780f),
        remotePath = "drafts/2026-10-25-castle-keep-idea",
    )

    @Test
    fun roundTrips() {
        val decoded = DraftJson.decode(DraftJson.encode(draft))
        // Coordinates are kept to a tenth of a unit.
        val expected = draft.copy(
            elements = listOf(Stroke(floatArrayOf(10f, 20f, 14.5f, 22.1f, -3f, 0f), ink, 4f)) + draft.elements.drop(1),
        )
        assertEquals(expected, decoded)
    }

    @Test
    fun writesOneElementPerLine() {
        val text = DraftJson.encode(draft)
        val lines = text.lines()
        assertTrue(lines.any { it.trim() == "{\"type\":\"stroke\",\"color\":\"#1F1F1F\",\"width\":4,\"points\":[10,20,14.5,22.1,-3,0]}," })
        assertTrue(lines.any { it.trim().startsWith("{\"type\":\"text\",\"color\":\"#1F1F1F\",\"size\":20,\"x\":12,\"y\":40,\"text\":\"Play\\nnow\"}") })
        assertTrue(text.contains("\"notes\": \"Taller tower\\nflags on both sides — ünïcode ✓ \\u0001\""))
    }

    @Test
    fun emptyDraftAndMissingFields() {
        val empty = Draft(id = "a", created = 1, modified = 2)
        assertEquals(empty, DraftJson.decode(DraftJson.encode(empty)))
        val minimal = DraftJson.decode("{\"format\":\"draftpad\",\"id\":\"x\"}")
        assertEquals("x", minimal.id)
        assertTrue(minimal.elements.isEmpty())
        assertNull(minimal.background)
    }

    @Test
    fun skipsElementsItDoesNotKnow() {
        val text = """
            {"format": "draftpad", "version": 2, "id": "x", "elements": [
              {"type": "hologram", "color": "#000000"},
              {"type": "stroke", "points": []},
              {"type": "rect", "color": "#80FF0000", "width": 2, "x1": 1, "y1": 2, "x2": 3, "y2": 4}
            ]}
        """.trimIndent()
        val d = DraftJson.decode(text)
        assertEquals(listOf<Element>(Shape(ShapeKind.RECT, 1f, 2f, 3f, 4f, 0x80FF0000.toInt(), 2f)), d.elements)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherJson() {
        DraftJson.decode("{\"format\": \"something else\", \"id\": \"x\"}")
    }

    @Test
    fun numbersAreLocaleFree() {
        assertEquals("12.3", Json.num(12.34f))
        assertEquals("-0.5", Json.num(-0.46f))
        assertEquals("0", Json.num(-0.04f))
        assertEquals("100", Json.num(99.99f))
        assertEquals("0", Json.num(Float.NaN))
        assertEquals("#80FF0000", Colors.hex(0x80FF0000.toInt()))
        assertEquals("#00FF00", Colors.hex(Colors.parse("00ff00")))
    }
}
