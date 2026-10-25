package com.draftpad.core

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * A sketch. Coordinates are canvas units: one unit is 1dp on the phone at 100% zoom, so a sketch
 * drawn over a phone screen is about 360-420 units across, the screen's width in dp.
 */
data class Draft(
    val id: String,
    val title: String = "",
    val notes: String = "",
    val created: Long = 0,
    val modified: Long = 0,
    val elements: List<Element> = emptyList(),
    val background: Background? = null,
    /** Folder in the GitHub repository the draft was pushed to, such as "drafts/2026-10-25-castle". */
    val remotePath: String? = null,
) {
    val isEmpty: Boolean get() = elements.isEmpty() && background == null

    /** Bounds of everything drawn, background image included, or null for an empty sketch. */
    fun contentBounds(metrics: TextMetrics = TextMetrics.Estimate): Box? {
        var box = background?.bounds
        for (e in elements) {
            val b = e.bounds(metrics)
            box = box?.union(b) ?: b
        }
        return box
    }
}

/** An image the sketch is drawn over, stored next to the draft as [file]. */
data class Background(val file: String, val x: Float, val y: Float, val width: Float, val height: Float) {
    val bounds: Box get() = Box(x, y, x + width, y + height)
}

data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun union(o: Box) = Box(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))

    fun outset(d: Float) = Box(left - d, top - d, right + d, bottom + d)

    fun contains(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom

    fun contains(o: Box) = o.left >= left && o.top >= top && o.right <= right && o.bottom <= bottom

    companion object {
        fun around(x1: Float, y1: Float, x2: Float, y2: Float) = Box(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
    }
}

/** Measures a line of text in canvas units. The app passes the metrics of the font it draws with. */
fun interface TextMetrics {
    fun width(line: String, size: Float): Float

    companion object {
        /** A sans-serif face's average advance, on the generous side so frames don't clip labels. */
        val Estimate = TextMetrics { line, size -> line.length * size * 0.6f }
    }
}

sealed class Element {
    /** ARGB. */
    abstract val color: Int

    abstract fun bounds(metrics: TextMetrics = TextMetrics.Estimate): Box

    abstract fun moved(dx: Float, dy: Float): Element
}

/** A freehand line through [points] (x0, y0, x1, y1, ...). A single point draws a dot. */
class Stroke(val points: FloatArray, override val color: Int, val width: Float) : Element() {
    init {
        require(points.size >= 2 && points.size % 2 == 0) { "A stroke needs whole points" }
    }

    val pointCount: Int get() = points.size / 2

    override fun bounds(metrics: TextMetrics): Box {
        var l = points[0]
        var t = points[1]
        var r = l
        var b = t
        for (i in 2 until points.size step 2) {
            l = min(l, points[i]); r = max(r, points[i])
            t = min(t, points[i + 1]); b = max(b, points[i + 1])
        }
        return Box(l, t, r, b).outset(width / 2)
    }

    override fun moved(dx: Float, dy: Float) =
        Stroke(FloatArray(points.size) { points[it] + if (it % 2 == 0) dx else dy }, color, width)

    override fun equals(other: Any?) = other is Stroke && other.color == color && other.width == width &&
        other.points.contentEquals(points)

    override fun hashCode() = (points.contentHashCode() * 31 + color) * 31 + width.hashCode()

    override fun toString() = "Stroke($pointCount points, width=$width, color=${Colors.hex(color)})"
}

enum class ShapeKind(val id: String) {
    LINE("line"),
    ARROW("arrow"),
    RECT("rect"),
    ELLIPSE("ellipse"),
}

/** A line or arrow from (x1, y1) to (x2, y2), or a rectangle or ellipse filling the box between them. */
data class Shape(
    val kind: ShapeKind,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    override val color: Int,
    val width: Float,
) : Element() {
    override fun bounds(metrics: TextMetrics): Box {
        val box = Box.around(x1, y1, x2, y2).outset(width / 2)
        if (kind != ShapeKind.ARROW) return box
        val head = Geometry.arrowHead(this)
        return box.union(Box.around(head.leftX, head.leftY, head.rightX, head.rightY))
    }

    override fun moved(dx: Float, dy: Float) = copy(x1 = x1 + dx, y1 = y1 + dy, x2 = x2 + dx, y2 = y2 + dy)
}

/** Text whose first line has its baseline at (x, y); further lines are [LINE_HEIGHT] × size apart. */
data class Label(val text: String, val x: Float, val y: Float, val size: Float, override val color: Int) : Element() {
    val lines: List<String> get() = text.split('\n')

    /** Baseline of line [index]. */
    fun baseline(index: Int) = y + index * LINE_HEIGHT * size

    override fun bounds(metrics: TextMetrics): Box {
        val lines = lines
        val w = lines.maxOf { metrics.width(it, size) }
        return Box(x, y - ASCENT * size, x + w, baseline(lines.size - 1) + DESCENT * size)
    }

    override fun moved(dx: Float, dy: Float) = copy(x = x + dx, y = y + dy)

    companion object {
        const val LINE_HEIGHT = 1.25f
        const val ASCENT = 0.95f
        const val DESCENT = 0.25f
    }
}

object Colors {
    /** "#RRGGBB" for opaque colours, "#AARRGGBB" otherwise. */
    fun hex(color: Int): String {
        val alpha = color ushr 24
        return if (alpha == 0xFF) String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)
        else String.format(Locale.ROOT, "#%08X", color)
    }

    /** Parses "#RRGGBB" or "#AARRGGBB". */
    fun parse(text: String): Int {
        val digits = text.removePrefix("#")
        require(digits.length == 6 || digits.length == 8) { "Bad colour '$text'" }
        val value = digits.toLong(16).toInt()
        return if (digits.length == 6) value or (0xFF shl 24) else value
    }
}
