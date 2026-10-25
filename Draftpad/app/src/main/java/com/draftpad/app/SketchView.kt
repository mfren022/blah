package com.draftpad.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.draftpad.core.Background
import com.draftpad.core.Box
import com.draftpad.core.Element
import com.draftpad.core.Geometry
import com.draftpad.core.Label
import com.draftpad.core.Shape
import com.draftpad.core.Stroke
import kotlin.math.hypot
import kotlin.math.min

/**
 * The drawing surface. One finger uses the current tool; two fingers pan and zoom, and cancel
 * whatever the first finger had started. The view never keeps a change itself: each finished gesture
 * goes to [listener], which records it (for undo) and sets [elements] to the result.
 *
 * Canvas units are dp at 100% zoom: screen pixels = canvas units × density × zoom + offset.
 */
class SketchView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    interface Listener {
        /** A gesture changed the drawing to [elements]. */
        fun onSketchChanged(elements: List<Element>)

        /** The text tool was tapped at ([x], [y]) in canvas units, on [label] if it hit one. */
        fun onTextTap(x: Float, y: Float, label: Label?)
    }

    var listener: Listener? = null

    var elements: List<Element> = emptyList()
        set(value) {
            field = value
            if (gesture != Gesture.ZOOM) resetGesture()
            renderer.keepOnly(value)
            invalidate()
        }

    var tool = Tool.PEN
        set(value) {
            field = value
            cancelGesture()
        }

    var color = Pens.COLORS[0]
    var lineWidth = Pens.WIDTHS[1]

    var showGrid = true
        set(value) {
            field = value
            invalidate()
        }

    /** 1 is 100%: one canvas unit per dp. */
    var zoom = 1f
        private set
    private var offsetX = 0f
    private var offsetY = 0f
    private val density = resources.displayMetrics.density
    private val scale get() = density * zoom

    val renderer = Renderer()
    private var background: Bitmap? = null
    private var placement: Background? = null

    private enum class Gesture { NONE, DRAW, SHAPE, TAP, MOVE, PAN, ERASE, ZOOM, DONE }

    private var gesture = Gesture.NONE
    private var base: List<Element> = emptyList()
    private var preview: List<Element>? = null
    private var points = FloatArray(512)
    private var pointCount = 0
    private var shapeX = 0f
    private var shapeY = 0f
    private var liveShape: Shape? = null
    private var moving = -1
    private val erased = HashSet<Int>()
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var pinchA = -1
    private var pinchB = -1
    private var pinchDistance = 0f
    private var pinchX = 0f
    private var pinchY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val canvasColor = context.getColor(R.color.canvas)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.grid)
        strokeWidth = context.dp(2f)
        strokeCap = Paint.Cap.ROUND
    }
    private var gridPoints = FloatArray(0)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = context.getColor(R.color.accent)
        strokeWidth = context.dp(1.5f)
    }

    init {
        isFocusable = false
    }

    fun setBackgroundImage(bitmap: Bitmap?, placement: Background?) {
        background = bitmap
        this.placement = placement
        invalidate()
    }

    fun toCanvasX(x: Float) = (x - offsetX) / scale

    fun toCanvasY(y: Float) = (y - offsetY) / scale

    /** The part of the canvas on screen, in canvas units. */
    fun visibleArea() = Box(toCanvasX(0f), toCanvasY(0f), toCanvasX(width.toFloat()), toCanvasY(height.toFloat()))

    fun resetView() {
        zoom = 1f
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }

    /** Zooms and centres so all of [content] (canvas units) is in view, at most at 200%. */
    fun fitTo(content: Box?) {
        if (content == null || width == 0 || height == 0) {
            resetView()
            return
        }
        val margin = context.dp(24f)
        val fit = min((width - 2 * margin) / (content.width * density), (height - 2 * margin) / (content.height * density))
        zoom = fit.coerceIn(MIN_ZOOM, 2f)
        offsetX = width / 2f - (content.left + content.width / 2) * scale
        offsetY = height / 2f - (content.top + content.height / 2) * scale
        invalidate()
    }

    fun cancelGesture() {
        resetGesture()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(canvasColor)
        if (showGrid) drawGrid(canvas)
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        val shown = preview ?: elements
        renderer.draw(canvas, shown, background, placement)
        when (gesture) {
            Gesture.DRAW -> renderer.drawLive(canvas, points, pointCount, color, lineWidth)
            Gesture.SHAPE -> liveShape?.let { renderer.draw(canvas, it) }
            Gesture.MOVE -> {
                // Outline what is being moved.
                val box = shown[moving].bounds(renderer.metrics).outset(4f / zoom)
                ringPaint.strokeWidth = 1.5f / zoom
                canvas.drawRect(box.left, box.top, box.right, box.bottom, ringPaint)
            }
            else -> {}
        }
        canvas.restore()
        if (gesture == Gesture.ERASE) {
            ringPaint.strokeWidth = context.dp(1.5f)
            canvas.drawCircle(lastX, lastY, context.dp(ERASER_DP), ringPaint)
        }
    }

    private fun drawGrid(canvas: Canvas) {
        var step = GRID * scale
        while (step < context.dp(14f)) step *= 2
        val columns = (width / step).toInt() + 2
        val rows = (height / step).toInt() + 2
        if (gridPoints.size < columns * rows * 2) gridPoints = FloatArray(columns * rows * 2)
        var n = 0
        var y = offsetY.mod(step)
        while (y < height) {
            var x = offsetX.mod(step)
            while (x < width) {
                gridPoints[n++] = x
                gridPoints[n++] = y
                x += step
            }
            y += step
        }
        canvas.drawPoints(gridPoints, 0, n, gridPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(e.x, e.y)
            MotionEvent.ACTION_POINTER_DOWN -> if (gesture != Gesture.ZOOM) {
                // A second finger turns any gesture into pan and zoom, dropping what the first had done.
                resetGesture()
                startPinch(e)
            }
            MotionEvent.ACTION_MOVE -> when (gesture) {
                Gesture.ZOOM -> pinch(e)
                Gesture.NONE, Gesture.DONE -> {}
                else -> move(e)
            }
            MotionEvent.ACTION_POINTER_UP -> if (gesture == Gesture.ZOOM) {
                val id = e.getPointerId(e.actionIndex)
                // The finger left over after a pinch does nothing until it lifts.
                if (id == pinchA || id == pinchB) gesture = Gesture.DONE
            }
            MotionEvent.ACTION_UP -> if (gesture == Gesture.ZOOM || gesture == Gesture.DONE) {
                gesture = Gesture.NONE
            } else {
                finish(e.x, e.y)
            }
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return true
    }

    private fun begin(x: Float, y: Float) {
        downX = x
        downY = y
        lastX = x
        lastY = y
        base = elements
        val cx = toCanvasX(x)
        val cy = toCanvasY(y)
        gesture = when (tool) {
            Tool.PEN -> {
                pointCount = 0
                addPoint(cx, cy)
                Gesture.DRAW
            }
            Tool.LINE, Tool.ARROW, Tool.RECT, Tool.ELLIPSE -> {
                shapeX = cx
                shapeY = cy
                liveShape = null
                Gesture.SHAPE
            }
            Tool.TEXT -> Gesture.TAP
            Tool.MOVE -> {
                moving = Geometry.topmostAt(base, cx, cy, TOUCH_DP / zoom, renderer.metrics)
                if (moving >= 0) Gesture.MOVE else Gesture.PAN
            }
            Tool.ERASE -> {
                erased.clear()
                erase(cx, cy, cx, cy)
                Gesture.ERASE
            }
        }
        invalidate()
    }

    private fun move(e: MotionEvent) {
        val x = e.x
        val y = e.y
        when (gesture) {
            Gesture.DRAW -> {
                for (h in 0 until e.historySize) addPointIfApart(e.getHistoricalX(h), e.getHistoricalY(h))
                addPointIfApart(x, y)
            }
            Gesture.SHAPE -> if (liveShape != null || hypot(x - downX, y - downY) > touchSlop / 2f) {
                liveShape = Shape(tool.shape!!, shapeX, shapeY, toCanvasX(x), toCanvasY(y), color, lineWidth)
            }
            // Dragging with the text tool pans instead.
            Gesture.TAP -> if (hypot(x - downX, y - downY) > touchSlop) {
                gesture = Gesture.PAN
                offsetX += x - downX
                offsetY += y - downY
            }
            Gesture.PAN -> {
                offsetX += x - lastX
                offsetY += y - lastY
            }
            Gesture.MOVE -> {
                val moved = base[moving].moved((x - downX) / scale, (y - downY) / scale)
                preview = base.toMutableList().also { it[moving] = moved }
            }
            Gesture.ERASE -> erase(toCanvasX(lastX), toCanvasY(lastY), toCanvasX(x), toCanvasY(y))
            else -> {}
        }
        lastX = x
        lastY = y
        invalidate()
    }

    private fun finish(x: Float, y: Float) {
        val result: List<Element>? = when (gesture) {
            Gesture.DRAW -> base + Stroke(points.copyOf(pointCount), color, lineWidth)
            Gesture.SHAPE -> liveShape?.let { base + it }
            Gesture.MOVE -> preview
            Gesture.ERASE -> preview?.takeIf { erased.isNotEmpty() }
            Gesture.TAP -> {
                val cx = toCanvasX(x)
                val cy = toCanvasY(y)
                val label = base.lastOrNull { it is Label && Geometry.hits(it, cx, cy, TOUCH_DP / zoom, renderer.metrics) }
                listener?.onTextTap(cx, cy, label as Label?)
                null
            }
            else -> null
        }
        resetGesture()
        invalidate()
        if (result != null) listener?.onSketchChanged(result)
    }

    private fun resetGesture() {
        gesture = Gesture.NONE
        preview = null
        liveShape = null
        pointCount = 0
        moving = -1
        erased.clear()
    }

    private fun addPointIfApart(sx: Float, sy: Float) {
        val cx = toCanvasX(sx)
        val cy = toCanvasY(sy)
        // Samples closer than 1.5dp on screen add nothing but noise.
        if (hypot(cx - points[pointCount - 2], cy - points[pointCount - 1]) * zoom < 1.5f) return
        addPoint(cx, cy)
    }

    private fun addPoint(x: Float, y: Float) {
        if (pointCount + 2 > points.size) points = points.copyOf(points.size * 2)
        points[pointCount++] = x
        points[pointCount++] = y
    }

    private fun erase(x0: Float, y0: Float, x1: Float, y1: Float) {
        val hit = Geometry.touchedAlong(base, x0, y0, x1, y1, ERASER_DP / zoom, renderer.metrics)
        if (erased.addAll(hit)) preview = base.filterIndexed { i, _ -> i !in erased }
    }

    private fun startPinch(e: MotionEvent) {
        pinchA = e.getPointerId(0)
        pinchB = e.getPointerId(1)
        pinchDistance = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0))
        pinchX = (e.getX(0) + e.getX(1)) / 2
        pinchY = (e.getY(0) + e.getY(1)) / 2
        gesture = Gesture.ZOOM
        invalidate()
    }

    private fun pinch(e: MotionEvent) {
        val a = e.findPointerIndex(pinchA)
        val b = e.findPointerIndex(pinchB)
        if (a < 0 || b < 0) return
        val distance = hypot(e.getX(b) - e.getX(a), e.getY(b) - e.getY(a))
        val mx = (e.getX(a) + e.getX(b)) / 2
        val my = (e.getY(a) + e.getY(b)) / 2
        // Keep the canvas point that was under the fingers' midpoint under it.
        val cx = toCanvasX(pinchX)
        val cy = toCanvasY(pinchY)
        if (pinchDistance > context.dp(8f) && distance > context.dp(8f)) {
            zoom = (zoom * distance / pinchDistance).coerceIn(MIN_ZOOM, MAX_ZOOM)
        }
        offsetX = mx - cx * scale
        offsetY = my - cy * scale
        pinchDistance = distance
        pinchX = mx
        pinchY = my
        invalidate()
    }

    companion object {
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 8f

        /** Dot grid spacing, in canvas units. */
        const val GRID = 20f

        /** How close a finger must come to grab or edit something, in dp on screen. */
        const val TOUCH_DP = 12f

        /** Eraser radius, in dp on screen. */
        const val ERASER_DP = 10f
    }
}
