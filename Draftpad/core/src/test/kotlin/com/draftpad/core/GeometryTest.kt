package com.draftpad.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {
    private val ink = Colors.parse("#1F1F1F")

    @Test
    fun strokeIsHitNearItsLineOnly() {
        val s = Stroke(floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f), ink, 4f)
        assertTrue(Geometry.hits(s, 50f, 3f, 2f))
        assertTrue(Geometry.hits(s, 103f, 50f, 2f))
        assertFalse(Geometry.hits(s, 50f, 50f, 2f))
        assertFalse(Geometry.hits(s, 50f, 9f, 2f))
    }

    @Test
    fun dotIsHitAroundItsPoint() {
        val dot = Stroke(floatArrayOf(10f, 10f), ink, 6f)
        assertTrue(Geometry.hits(dot, 13f, 12f, 1f))
        assertFalse(Geometry.hits(dot, 20f, 10f, 1f))
        assertEquals(Box(7f, 7f, 13f, 13f), dot.bounds())
    }

    @Test
    fun rectanglesAndEllipsesAreHitOnTheirOutline() {
        val rect = Shape(ShapeKind.RECT, 0f, 0f, 200f, 100f, ink, 4f)
        assertTrue(Geometry.hits(rect, 100f, 1f, 4f))
        assertTrue(Geometry.hits(rect, 199f, 50f, 4f))
        assertFalse(Geometry.hits(rect, 100f, 50f, 4f))

        val ellipse = Shape(ShapeKind.ELLIPSE, 0f, 0f, 200f, 100f, ink, 4f)
        assertTrue(Geometry.hits(ellipse, 100f, 1f, 4f))
        assertTrue(Geometry.hits(ellipse, 199f, 50f, 4f))
        assertTrue(Geometry.hits(ellipse, 100f + 100f * 0.7071f, 50f + 50f * 0.7071f, 4f))
        assertFalse(Geometry.hits(ellipse, 100f, 50f, 4f))
        assertFalse(Geometry.hits(ellipse, 2f, 2f, 4f))
    }

    @Test
    fun arrowHeadPointsAlongTheArrow() {
        val arrow = Shape(ShapeKind.ARROW, 0f, 0f, 100f, 0f, ink, 4f)
        val head = Geometry.arrowHead(arrow)
        assertEquals(100f, head.tipX, 0.01f)
        assertTrue(head.leftX < 100f && head.rightX < 100f)
        assertEquals(-head.leftY, head.rightY, 0.01f)
        assertTrue(head.shaftX in 80f..100f)
        // The head widens the arrow's bounds and can be grabbed.
        assertTrue(arrow.bounds().top < -2f)
        assertTrue(Geometry.hits(arrow, 95f, 0f, 1f))
    }

    @Test
    fun labelsAreHitInsideTheirText() {
        val label = Label("Play\nStore", 10f, 50f, 20f, ink)
        val box = label.bounds()
        assertEquals(50f - 0.95f * 20f, box.top, 0.01f)
        assertEquals(50f + 1.25f * 20f + 0.25f * 20f, box.bottom, 0.01f)
        assertTrue(Geometry.hits(label, 20f, 70f, 0f))
        assertFalse(Geometry.hits(label, 200f, 70f, 0f))
    }

    @Test
    fun topmostElementWins() {
        val under = Shape(ShapeKind.LINE, 0f, 0f, 100f, 0f, ink, 4f)
        val over = Shape(ShapeKind.LINE, 0f, 0f, 100f, 0f, ink, 8f)
        assertEquals(1, Geometry.topmostAt(listOf(under, over), 50f, 0f, 2f))
        assertEquals(-1, Geometry.topmostAt(listOf(under, over), 50f, 40f, 2f))
    }

    @Test
    fun eraserCatchesElementsBetweenSamples() {
        val a = Shape(ShapeKind.LINE, 50f, -20f, 50f, 20f, ink, 2f)
        val b = Shape(ShapeKind.LINE, 150f, -20f, 150f, 20f, ink, 2f)
        val c = Shape(ShapeKind.LINE, 50f, 100f, 150f, 100f, ink, 2f)
        // One quick swipe from x=0 to x=200 along y=0 crosses a and b.
        assertEquals(setOf(0, 1), Geometry.touchedAlong(listOf(a, b, c), 0f, 0f, 200f, 0f, 6f))
    }

    @Test
    fun tracedStrokesStartAndEndOnTheirSamples() {
        val ops = mutableListOf<String>()
        val sink = object : PathSink {
            override fun moveTo(x: Float, y: Float) { ops += "M$x,$y" }
            override fun lineTo(x: Float, y: Float) { ops += "L$x,$y" }
            override fun quadTo(cx: Float, cy: Float, x: Float, y: Float) { ops += "Q$cx,$cy,$x,$y" }
        }
        Geometry.traceStroke(floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 20f, 10f), sink)
        assertEquals(listOf("M0.0,0.0", "Q10.0,0.0,10.0,5.0", "Q10.0,10.0,15.0,10.0", "L20.0,10.0"), ops)

        ops.clear()
        Geometry.traceStroke(floatArrayOf(5f, 5f), sink)
        assertEquals(listOf("M5.0,5.0", "L5.0,5.0"), ops)
    }

    @Test
    fun movingKeepsShapeAndStyle() {
        val s = Stroke(floatArrayOf(0f, 0f, 10f, 10f), ink, 4f).moved(5f, -5f) as Stroke
        assertEquals(listOf(5f, -5f, 15f, 5f), s.points.toList())
        assertEquals(Label("a", 3f, 4f, 20f, ink), Label("a", 1f, 1f, 20f, ink).moved(2f, 3f))
    }

    @Test
    fun historyUndoesAndRedoes() {
        val one = listOf<Element>(Label("1", 0f, 0f, 20f, ink))
        val two = one + Label("2", 0f, 0f, 20f, ink)
        val h = History()
        assertFalse(h.commit(emptyList()))
        assertTrue(h.commit(one))
        assertTrue(h.commit(two))
        assertTrue(h.undo())
        assertEquals(one, h.current)
        assertTrue(h.canRedo)
        assertTrue(h.redo())
        assertEquals(two, h.current)
        h.undo()
        h.commit(listOf(Label("3", 0f, 0f, 20f, ink)))
        assertFalse("A new change drops the redo stack", h.canRedo)
        h.reset(one)
        assertFalse(h.canUndo)
    }
}
