package com.draftpad.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Receives the segments of a stroke's smoothed path (see [Geometry.traceStroke]). */
interface PathSink {
    fun moveTo(x: Float, y: Float)
    fun lineTo(x: Float, y: Float)
    fun quadTo(cx: Float, cy: Float, x: Float, y: Float)
}

/** An arrow's filled head (tip, left and right corners) and the point its shaft stops at. */
class ArrowHead(
    val tipX: Float, val tipY: Float,
    val leftX: Float, val leftY: Float,
    val rightX: Float, val rightY: Float,
    val shaftX: Float, val shaftY: Float,
)

object Geometry {
    /** Half the angle of an arrow head, about 26 degrees. */
    private const val HEAD_SPREAD = 0.45f

    /**
     * Walks a smooth curve through a stroke's samples, the same way on screen and in the SVG: it starts
     * at the first sample, bends through the midpoints between samples (each sample is the control
     * point between two midpoints) and ends at the last sample. One sample gives a zero-length line.
     * [size] is how many floats of [points] to use, for a stroke still being drawn into a buffer.
     */
    fun traceStroke(points: FloatArray, sink: PathSink, size: Int = points.size) {
        val n = size / 2
        sink.moveTo(points[0], points[1])
        if (n <= 2) {
            sink.lineTo(points[2 * n - 2], points[2 * n - 1])
            return
        }
        for (i in 1 until n - 1) {
            val cx = points[2 * i]
            val cy = points[2 * i + 1]
            sink.quadTo(cx, cy, (cx + points[2 * i + 2]) / 2, (cy + points[2 * i + 3]) / 2)
        }
        sink.lineTo(points[2 * n - 2], points[2 * n - 1])
    }

    /** The head of an arrow pointing from (x1, y1) to (x2, y2), sized to its line width and length. */
    fun arrowHead(s: Shape): ArrowHead {
        val dx = s.x2 - s.x1
        val dy = s.y2 - s.y1
        val length = hypot(dx, dy)
        val angle = if (length < 0.001f) 0f else atan2(dy, dx)
        val head = min(max(10f, s.width * 3.5f), max(length * 0.6f, s.width * 2f))
        // The shaft stops inside the head, so its round cap doesn't poke out past the tip.
        val back = min(head * cos(HEAD_SPREAD) * 0.9f, length)
        return ArrowHead(
            s.x2, s.y2,
            s.x2 - head * cos(angle - HEAD_SPREAD), s.y2 - head * sin(angle - HEAD_SPREAD),
            s.x2 - head * cos(angle + HEAD_SPREAD), s.y2 - head * sin(angle + HEAD_SPREAD),
            s.x2 - back * cos(angle), s.y2 - back * sin(angle),
        )
    }

    fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val length2 = dx * dx + dy * dy
        val t = if (length2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / length2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /**
     * Whether (x, y) is within [tolerance] of what [e] draws. Rectangles and ellipses count only near
     * their outline, so whatever is drawn inside a frame can still be picked out.
     */
    fun hits(e: Element, x: Float, y: Float, tolerance: Float, metrics: TextMetrics = TextMetrics.Estimate): Boolean {
        if (!e.bounds(metrics).outset(tolerance).contains(x, y)) return false
        return when (e) {
            is Stroke -> nearPolyline(e.points, x, y, e.width / 2 + tolerance)
            is Shape -> hitsShape(e, x, y, e.width / 2 + tolerance)
            is Label -> true
        }
    }

    /** Index of the topmost element at (x, y), or -1. */
    fun topmostAt(
        elements: List<Element>,
        x: Float,
        y: Float,
        tolerance: Float,
        metrics: TextMetrics = TextMetrics.Estimate,
    ): Int {
        for (i in elements.indices.reversed()) if (hits(elements[i], x, y, tolerance, metrics)) return i
        return -1
    }

    /** Indices of the elements an eraser of [radius] touches as it moves from (x0, y0) to (x1, y1). */
    fun touchedAlong(
        elements: List<Element>,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float,
        metrics: TextMetrics = TextMetrics.Estimate,
    ): Set<Int> {
        val steps = max(1, ceil(hypot(x1 - x0, y1 - y0) / (radius * 0.5f)).toInt())
        val touched = LinkedHashSet<Int>()
        for (step in 0..steps) {
            val t = step.toFloat() / steps
            val x = x0 + (x1 - x0) * t
            val y = y0 + (y1 - y0) * t
            for (i in elements.indices) if (i !in touched && hits(elements[i], x, y, radius, metrics)) touched += i
        }
        return touched
    }

    private fun nearPolyline(p: FloatArray, x: Float, y: Float, reach: Float): Boolean {
        if (p.size == 2) return hypot(x - p[0], y - p[1]) <= reach
        for (i in 0 until p.size - 2 step 2) {
            if (distanceToSegment(x, y, p[i], p[i + 1], p[i + 2], p[i + 3]) <= reach) return true
        }
        return false
    }

    private fun hitsShape(s: Shape, x: Float, y: Float, reach: Float): Boolean = when (s.kind) {
        ShapeKind.LINE -> distanceToSegment(x, y, s.x1, s.y1, s.x2, s.y2) <= reach
        ShapeKind.ARROW -> distanceToSegment(x, y, s.x1, s.y1, s.x2, s.y2) <= reach || inHead(arrowHead(s), x, y)
        ShapeKind.RECT -> {
            val b = Box.around(s.x1, s.y1, s.x2, s.y2)
            distanceToSegment(x, y, b.left, b.top, b.right, b.top) <= reach ||
                distanceToSegment(x, y, b.right, b.top, b.right, b.bottom) <= reach ||
                distanceToSegment(x, y, b.right, b.bottom, b.left, b.bottom) <= reach ||
                distanceToSegment(x, y, b.left, b.bottom, b.left, b.top) <= reach
        }
        ShapeKind.ELLIPSE -> nearEllipse(s, x, y, reach)
    }

    private fun nearEllipse(s: Shape, x: Float, y: Float, reach: Float): Boolean {
        val cx = (s.x1 + s.x2) / 2
        val cy = (s.y1 + s.y2) / 2
        val rx = abs(s.x2 - s.x1) / 2
        val ry = abs(s.y2 - s.y1) / 2
        // A flat ellipse is drawn as a line.
        if (rx < 1f || ry < 1f) return distanceToSegment(x, y, cx - rx, cy - ry, cx + rx, cy + ry) <= reach
        // First-order distance to the outline: |f| / |grad f| for f = (dx/rx)^2 + (dy/ry)^2 - 1.
        val dx = x - cx
        val dy = y - cy
        val f = dx * dx / (rx * rx) + dy * dy / (ry * ry) - 1f
        val gradient = hypot(2f * dx / (rx * rx), 2f * dy / (ry * ry))
        return gradient > 1e-6f && abs(f) / gradient <= reach
    }

    private fun inHead(h: ArrowHead, x: Float, y: Float): Boolean {
        fun side(ax: Float, ay: Float, bx: Float, by: Float) = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
        val a = side(h.tipX, h.tipY, h.leftX, h.leftY)
        val b = side(h.leftX, h.leftY, h.rightX, h.rightY)
        val c = side(h.rightX, h.rightY, h.tipX, h.tipY)
        return (a >= 0 && b >= 0 && c >= 0) || (a <= 0 && b <= 0 && c <= 0)
    }
}
