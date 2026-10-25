package com.draftpad.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.draftpad.core.Background
import com.draftpad.core.Box
import com.draftpad.core.Draft
import com.draftpad.core.Element
import com.draftpad.core.Geometry
import com.draftpad.core.Label
import com.draftpad.core.PathSink
import com.draftpad.core.Shape
import com.draftpad.core.ShapeKind
import com.draftpad.core.Stroke
import com.draftpad.core.TextMetrics
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws a sketch onto an Android canvas in canvas units, the way [com.draftpad.core.Svg] writes it.
 * Each stroke's smoothed path is kept, so redrawing a big sketch stays cheap. Not thread-safe: use one
 * renderer per thread.
 */
class Renderer {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { typeface = Typeface.SANS_SERIF }
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paths = IdentityHashMap<Stroke, Path>()
    private val scratch = Path()
    private val rect = RectF()

    /** Text widths as this renderer draws them, for hit-testing and framing labels. */
    val metrics = TextMetrics { s, size ->
        text.textSize = size
        text.measureText(s)
    }

    fun draw(canvas: Canvas, elements: List<Element>, background: Bitmap?, placement: Background?) {
        if (background != null && placement != null) {
            rect.set(placement.x, placement.y, placement.x + placement.width, placement.y + placement.height)
            canvas.drawBitmap(background, null, rect, image)
        }
        for (e in elements) draw(canvas, e)
    }

    fun draw(canvas: Canvas, e: Element) {
        when (e) {
            is Stroke -> if (e.pointCount == 1) {
                fill.color = e.color
                canvas.drawCircle(e.points[0], e.points[1], e.width / 2, fill)
            } else {
                line.color = e.color
                line.strokeWidth = e.width
                canvas.drawPath(paths.getOrPut(e) { trace(Path(), e.points, e.points.size) }, line)
            }
            is Shape -> drawShape(canvas, e)
            is Label -> {
                text.color = e.color
                text.textSize = e.size
                e.lines.forEachIndexed { i, s -> canvas.drawText(s, e.x, e.baseline(i), text) }
            }
        }
    }

    /** Draws a stroke that is still being drawn: the first [size] floats of [points]. */
    fun drawLive(canvas: Canvas, points: FloatArray, size: Int, color: Int, width: Float) {
        if (size == 2) {
            fill.color = color
            canvas.drawCircle(points[0], points[1], width / 2, fill)
            return
        }
        line.color = color
        line.strokeWidth = width
        scratch.reset()
        canvas.drawPath(trace(scratch, points, size), line)
    }

    /** Forgets the paths of strokes that are no longer in [elements]. */
    fun keepOnly(elements: List<Element>) {
        if (paths.isEmpty()) return
        val keep = Collections.newSetFromMap(IdentityHashMap<Stroke, Boolean>())
        for (e in elements) if (e is Stroke) keep.add(e)
        paths.keys.retainAll(keep)
    }

    private fun drawShape(canvas: Canvas, s: Shape) {
        line.color = s.color
        line.strokeWidth = s.width
        when (s.kind) {
            ShapeKind.LINE -> canvas.drawLine(s.x1, s.y1, s.x2, s.y2, line)
            ShapeKind.ARROW -> {
                val head = Geometry.arrowHead(s)
                canvas.drawLine(s.x1, s.y1, head.shaftX, head.shaftY, line)
                scratch.reset()
                scratch.moveTo(head.tipX, head.tipY)
                scratch.lineTo(head.leftX, head.leftY)
                scratch.lineTo(head.rightX, head.rightY)
                scratch.close()
                fill.color = s.color
                canvas.drawPath(scratch, fill)
            }
            ShapeKind.RECT -> canvas.drawRect(min(s.x1, s.x2), min(s.y1, s.y2), max(s.x1, s.x2), max(s.y1, s.y2), line)
            ShapeKind.ELLIPSE -> {
                rect.set(min(s.x1, s.x2), min(s.y1, s.y2), max(s.x1, s.x2), max(s.y1, s.y2))
                canvas.drawOval(rect, line)
            }
        }
    }

    private fun trace(path: Path, points: FloatArray, size: Int): Path {
        Geometry.traceStroke(points, object : PathSink {
            override fun moveTo(x: Float, y: Float) = path.moveTo(x, y)
            override fun lineTo(x: Float, y: Float) = path.lineTo(x, y)
            override fun quadTo(cx: Float, cy: Float, x: Float, y: Float) = path.quadTo(cx, cy, x, y)
        }, size)
        return path
    }

    /** A sketch drawn on white: [bitmap] shows [frame] (canvas units) at [scale] pixels per unit. */
    class Picture(val bitmap: Bitmap, val frame: Box, val scale: Float)

    companion object {
        /** Long side of shared and pushed pictures, in pixels: about what Claude looks at closely. */
        const val EXPORT_SIDE = 1600
        const val THUMBNAIL_SIDE = 360

        /** Margin around the drawing in pictures, in canvas units. */
        const val PADDING = 16f

        /** What pictures of [draft] show: everything drawn, plus a margin. */
        fun frame(draft: Draft, metrics: TextMetrics): Box =
            (draft.contentBounds(metrics) ?: Box(0f, 0f, 360f, 240f)).outset(PADDING)

        /** [draft] on white with its long side about [longSide] pixels. */
        fun picture(draft: Draft, background: Bitmap?, longSide: Int): Picture {
            val renderer = Renderer()
            val frame = frame(draft, renderer.metrics)
            val scale = (longSide / max(frame.width, frame.height)).coerceIn(0.1f, 4f)
            val bitmap = Bitmap.createBitmap(
                max(1, (frame.width * scale).roundToInt()),
                max(1, (frame.height * scale).roundToInt()),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            canvas.scale(scale, scale)
            canvas.translate(-frame.left, -frame.top)
            renderer.draw(canvas, draft.elements, background, draft.background)
            return Picture(bitmap, frame, scale)
        }
    }
}
