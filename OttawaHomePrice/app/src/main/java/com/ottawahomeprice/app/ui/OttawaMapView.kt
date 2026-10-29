package com.ottawahomeprice.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.ottawahomeprice.app.R
import com.ottawahomeprice.core.Estimator
import com.ottawahomeprice.core.LatLon
import com.ottawahomeprice.core.LocalProjection
import com.ottawahomeprice.core.MapFeature
import java.util.concurrent.Executor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * An offline map of the City of Ottawa, shaded by how home prices compare with the city average
 * (the same neighbourhood blend the estimates use), with the rivers, highways and place names.
 * Tap to pick a spot; pinch, drag and double-tap to zoom once zoomed in.
 */
class OttawaMapView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** Called with the point the user tapped. */
    var onPointPicked: ((LatLon) -> Unit)? = null

    private var estimator: Estimator? = null
    private var marker: LatLon? = null
    private var heat: Bitmap? = null
    private val night = context.isNight

    // World coordinates are kilometres east and north (LocalProjection); screen y runs down.
    private val projection = LocalProjection.OTTAWA
    private var worldLeft = 0.0
    private var worldRight = 1.0
    private var worldBottom = 0.0
    private var worldTop = 1.0
    private var baseScale = 1.0
    private var zoom = 1.0
    private var centerX = 0.5
    private var centerY = 0.5
    private val scale: Double get() = baseScale * zoom

    private val outsideColor = context.getColor(R.color.map_land_outside)
    private val heatPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = context.getColor(R.color.map_water); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = context.getColor(R.color.map_road); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val roadCasing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = context.getColor(R.color.map_road_casing); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val boundaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(1.5f); color = withAlpha(context.getColor(R.color.map_boundary), 0.75f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.map_label); textSize = sp(11f); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val outsideLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.map_label_outside); textSize = sp(11f); typeface = Typeface.create("sans-serif", Typeface.ITALIC)
    }
    private val smallLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.map_label); textSize = sp(10f)
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(3f); color = context.getColor(R.color.map_label_halo); strokeJoin = Paint.Join.ROUND
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.marker) }
    private val markerInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val markerShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(context.getColor(R.color.surface), 0.92f) }
    private val buttonText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.ink); textSize = sp(12f); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val resetButton = RectF()
    private val placedLabels = ArrayList<RectF>()

    /** Whether the shading has been computed (for tests and first draw). */
    val shaded: Boolean get() = heat != null

    fun setEstimator(estimator: Estimator, background: Executor) {
        this.estimator = estimator
        val frame = estimator.data.boundary.bounds.expandedBy(0.03)
        worldLeft = projection.x(frame.west)
        worldRight = projection.x(frame.east)
        worldBottom = projection.y(frame.south)
        worldTop = projection.y(frame.north)
        centerX = (worldLeft + worldRight) / 2
        centerY = (worldBottom + worldTop) / 2
        updateBaseScale()
        contentDescription = "Map of Ottawa shaded by how home prices compare with the city average. Tap to estimate a spot."
        val cached = heatCache
        if (cached != null && cached.first === estimator && cached.second == night) {
            heat = cached.third
        } else {
            val main = Handler(Looper.getMainLooper())
            background.execute {
                val bitmap = renderHeat(estimator)
                heatCache = Triple(estimator, night, bitmap)
                main.post { heat = bitmap; invalidate() }
            }
        }
        invalidate()
    }

    fun setMarker(point: LatLon?) {
        marker = point
        invalidate()
    }

    /** Colours each pixel of a fixed-resolution grid over the frame by the price factor there. */
    private fun renderHeat(estimator: Estimator): Bitmap {
        val w = HEAT_WIDTH
        val h = max(1, (w * (worldTop - worldBottom) / (worldRight - worldLeft)).toInt())
        val pixels = IntArray(w * h)
        val model = estimator.locations
        val alpha = if (night) 0.72f else 0.9f
        for (row in 0 until h) {
            val lat = projection.lat(worldTop - (row + 0.5) * (worldTop - worldBottom) / h)
            for (col in 0 until w) {
                val lon = projection.lon(worldLeft + (col + 0.5) * (worldRight - worldLeft) / w)
                pixels[row * w + col] = withAlpha(PriceColors.color(model.factorAt(lat, lon)), alpha)
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateBaseScale()
    }

    private fun updateBaseScale() {
        if (width == 0 || height == 0) return
        baseScale = min(width / (worldRight - worldLeft), height / (worldTop - worldBottom))
        clampCenter()
    }

    private fun sx(wx: Double): Float = (width / 2.0 + (wx - centerX) * scale).toFloat()
    private fun sy(wy: Double): Float = (height / 2.0 - (wy - centerY) * scale).toFloat()
    private fun sx(p: LatLon): Float = sx(projection.x(p.lon))
    private fun sy(p: LatLon): Float = sy(projection.y(p.lat))

    /** The coordinate under screen point ([x], [y]). */
    fun pointAt(x: Float, y: Float): LatLon {
        val wx = centerX + (x - width / 2.0) / scale
        val wy = centerY - (y - height / 2.0) / scale
        return LatLon(projection.lat(wy).coerceIn(-90.0, 90.0), projection.lon(wx).coerceIn(-180.0, 180.0))
    }

    /** Where [point] is drawn, for tests. */
    fun screenOf(point: LatLon): Pair<Float, Float> = sx(point) to sy(point)

    private fun path(points: List<LatLon>, close: Boolean): Path {
        val p = Path()
        points.forEachIndexed { i, pt -> if (i == 0) p.moveTo(sx(pt), sy(pt)) else p.lineTo(sx(pt), sy(pt)) }
        if (close) p.close()
        return p
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(outsideColor)
        val estimator = estimator ?: return
        val data = estimator.data
        val boundary = path(data.boundary.points, close = true)

        // Shading, clipped to the city.
        heat?.let { bitmap ->
            canvas.save()
            canvas.clipPath(boundary)
            val dest = RectF(sx(worldLeft), sy(worldTop), sx(worldRight), sy(worldBottom))
            canvas.drawBitmap(bitmap, null, dest, heatPaint)
            canvas.restore()
        }

        val kmPx = scale.toFloat()
        for (f in data.mapFeatures) {
            when (f.kind) {
                MapFeature.Kind.RIVER -> {
                    // The Ottawa River is roughly a kilometre wide; the Rideau much narrower.
                    val wide = f.name.contains("Ottawa")
                    waterPaint.strokeWidth = max(dp(if (wide) 4f else 2f), kmPx * if (wide) 0.7f else 0.12f)
                    canvas.drawPath(path(f.points, close = false), waterPaint)
                }
                MapFeature.Kind.CANAL -> {
                    waterPaint.strokeWidth = max(dp(1.5f), kmPx * 0.05f)
                    canvas.drawPath(path(f.points, close = false), waterPaint)
                }
                else -> Unit
            }
        }
        val roadWidth = dp(1.6f) * sqrt(zoom).toFloat()
        roadCasing.strokeWidth = roadWidth + dp(1.6f)
        roadPaint.strokeWidth = roadWidth
        val roads = data.mapFeatures.filter { it.kind == MapFeature.Kind.HIGHWAY }.map { path(it.points, close = false) }
        roads.forEach { canvas.drawPath(it, roadCasing) }
        roads.forEach { canvas.drawPath(it, roadPaint) }
        canvas.drawPath(boundary, boundaryPaint)

        // Place names, skipping any that would overlap one already drawn.
        placedLabels.clear()
        marker?.let { m -> placedLabels += RectF(sx(m) - dp(12f), sy(m) - dp(34f), sx(m) + dp(12f), sy(m)) }
        for (f in data.mapFeatures) {
            if (f.kind != MapFeature.Kind.LABEL && f.kind != MapFeature.Kind.LABEL_OUTSIDE) continue
            val paint = if (f.kind == MapFeature.Kind.LABEL) labelPaint else outsideLabelPaint
            drawLabel(canvas, f.name, f.points.first(), paint)
        }
        if (zoom >= 2.2) {
            for (n in data.neighbourhoods.sortedByDescending { it.radiusKm }) drawLabel(canvas, n.name, n.location, smallLabelPaint)
        }

        marker?.let { drawMarker(canvas, sx(it), sy(it)) }

        if (zoom > 1.01) {
            val text = "Reset view"
            val tw = buttonText.measureText(text)
            resetButton.set(width - tw - dp(28f), dp(8f), width - dp(8f), dp(38f))
            canvas.drawRoundRect(resetButton, dp(15f), dp(15f), buttonPaint)
            canvas.drawText(text, resetButton.left + dp(10f), resetButton.centerY() + buttonText.textSize / 3, buttonText)
        } else {
            resetButton.setEmpty()
        }
    }

    private fun drawLabel(canvas: Canvas, text: String, at: LatLon, paint: Paint) {
        val x = sx(at)
        val y = sy(at)
        if (x < 0 || y < 0 || x > width || y > height) return
        val tw = paint.measureText(text)
        val rect = RectF(x - tw / 2 - dp(2f), y - paint.textSize, x + tw / 2 + dp(2f), y + dp(3f))
        if (rect.left < 0 || rect.right > width || rect.top < 0 || rect.bottom > height) return
        if (placedLabels.any { RectF.intersects(it, rect) }) return
        placedLabels += rect
        canvas.drawText(text, x - tw / 2, y, haloPaint.also { it.textSize = paint.textSize; it.typeface = paint.typeface })
        canvas.drawText(text, x - tw / 2, y, paint)
    }

    private fun drawMarker(canvas: Canvas, x: Float, y: Float) {
        val r = dp(9f)
        val tip = dp(24f)
        canvas.drawOval(x - dp(6f), y - dp(2.5f), x + dp(6f), y + dp(2.5f), markerShadow)
        val pin = Path().apply {
            moveTo(x, y)
            cubicTo(x - r * 0.4f, y - tip * 0.45f, x - r, y - tip * 0.6f, x - r, y - tip + r)
            arcTo(x - r, y - tip, x + r, y - tip + 2 * r, 180f, 180f, false)
            cubicTo(x + r, y - tip * 0.6f, x + r * 0.4f, y - tip * 0.45f, x, y)
            close()
        }
        canvas.drawPath(pin, markerPaint)
        canvas.drawCircle(x, y - tip + r, r * 0.42f, markerInner)
    }

    // ---- Gestures ----------------------------------------------------------------------------

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomBy(detector.scaleFactor.toDouble(), detector.focusX, detector.focusY)
            return true
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (zoom <= 1.01 && !scaleDetector.isInProgress) return false
            centerX += distanceX / scale
            centerY -= distanceY / scale
            clampCenter()
            invalidate()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (resetButton.contains(e.x, e.y)) {
                resetView()
            } else {
                onPointPicked?.invoke(pointAt(e.x, e.y))
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (resetButton.contains(e.x, e.y)) return true
            if (zoom >= MAX_ZOOM / 1.01) resetView() else zoomBy(2.0, e.x, e.y)
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (estimator == null) return false
        scaleDetector.onTouchEvent(event)
        gestures.onTouchEvent(event)
        // Zoomed in, or pinching: the map takes the gesture instead of the page scrolling.
        if (event.pointerCount > 1 || zoom > 1.01) parent?.requestDisallowInterceptTouchEvent(true)
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun zoomBy(factor: Double, focusX: Float, focusY: Float) {
        val before = pointAt(focusX, focusY)
        zoom = (zoom * factor).coerceIn(1.0, MAX_ZOOM)
        val wx = projection.x(before.lon)
        val wy = projection.y(before.lat)
        centerX = wx - (focusX - width / 2.0) / scale
        centerY = wy + (focusY - height / 2.0) / scale
        clampCenter()
        invalidate()
    }

    fun resetView() {
        zoom = 1.0
        centerX = (worldLeft + worldRight) / 2
        centerY = (worldBottom + worldTop) / 2
        invalidate()
    }

    /** Keeps the view over the city: at zoom 1 the whole frame shows. */
    private fun clampCenter() {
        if (width == 0 || scale <= 0) return
        val halfW = width / 2.0 / scale
        val halfH = height / 2.0 / scale
        centerX = if (worldRight - worldLeft <= 2 * halfW) (worldLeft + worldRight) / 2 else centerX.coerceIn(worldLeft + halfW, worldRight - halfW)
        centerY = if (worldTop - worldBottom <= 2 * halfH) (worldBottom + worldTop) / 2 else centerY.coerceIn(worldBottom + halfH, worldTop - halfH)
    }

    private companion object {
        const val HEAT_WIDTH = 320
        const val MAX_ZOOM = 16.0

        /** The shading depends only on the data, so it survives screen rotations. */
        @Volatile
        var heatCache: Triple<Estimator, Boolean, Bitmap>? = null
    }
}

/** The colour key under the map: how prices compare with the city average. */
class MapLegendView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.ink_muted); textSize = sp(11f) }
    private val bar = Paint()
    private val ticks = listOf(0.5, 0.75, 1.0, 1.5, 2.0, 3.0)

    init {
        contentDescription = "Colour key: blue is cheaper than the Ottawa average, yellow is about average, red is more expensive."
    }

    override fun onDraw(canvas: Canvas) {
        val left = dp(4f)
        val right = width - dp(4f)
        val top = dp(2f)
        val barHeight = dp(10f)
        val lnMin = kotlin.math.ln(PriceColors.minFactor)
        val lnMax = kotlin.math.ln(PriceColors.maxFactor)
        val steps = 120
        val stepWidth = (right - left) / steps
        for (i in 0 until steps) {
            val f = kotlin.math.exp(lnMin + (lnMax - lnMin) * (i + 0.5) / steps)
            bar.color = PriceColors.color(f)
            canvas.drawRect(left + i * stepWidth, top, left + (i + 1) * stepWidth + 0.5f, top + barHeight, bar)
        }
        for (t in ticks) {
            val x = left + (right - left) * ((kotlin.math.ln(t) - lnMin) / (lnMax - lnMin)).toFloat()
            val label = if (t == 1.0) "Ottawa avg" else com.ottawahomeprice.core.Format.decimals(t, if (t % 1.0 == 0.0) 0 else 2).trimEnd('0').trimEnd('.') + "×"
            val tw = text.measureText(label)
            canvas.drawText(label, (x - tw / 2).coerceIn(0f, width - tw), top + barHeight + dp(14f), text)
        }
    }
}
