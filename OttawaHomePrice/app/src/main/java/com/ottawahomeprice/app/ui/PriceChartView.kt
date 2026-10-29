package com.ottawahomeprice.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.ottawahomeprice.app.R
import com.ottawahomeprice.core.Format
import com.ottawahomeprice.core.Month
import com.ottawahomeprice.core.PriceSeries
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The estimate over every month the app offers, with its likely range as a band and the city
 * average as a dashed line. Months before the first data and after the last are shaded and
 * labelled. Tapping or dragging sideways picks a month.
 */
class PriceChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** Called with the month under the finger while the user taps or drags. */
    var onMonthPicked: ((Month) -> Unit)? = null

    private var series: PriceSeries? = null
    private var selected: Month? = null
    private var firstData: Month? = null
    private var lastData: Month? = null
    private var showCity = false
    private var inflationAdjusted = false
    private var logScale = true

    private val accent = context.getColor(R.color.accent)
    private val ink = context.getColor(R.color.ink)
    private val muted = context.getColor(R.color.ink_muted)
    private val faint = context.getColor(R.color.ink_faint)
    private val lineColor = context.getColor(R.color.line)
    private val surface = context.getColor(R.color.surface)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(2.5f); color = accent; strokeJoin = Paint.Join.ROUND
    }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = withAlpha(accent, 0.16f) }
    private val cityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(1.5f); color = context.getColor(R.color.city_line)
        pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
    }
    private val gridPaint = Paint().apply { color = lineColor; strokeWidth = dp(1f) }
    private val shadePaint = Paint().apply { color = withAlpha(muted, 0.07f) }
    private val axisText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = sp(11f) }
    private val regionText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = faint; textSize = sp(10f) }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; strokeWidth = dp(1f) }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val dotRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = surface }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }
    private val bubbleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = surface; textSize = sp(12f); typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    private val plot = RectF()
    private var yMin = 1.0
    private var yMax = 2.0
    private var paths: Triple<Path, Path, Path?>? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    fun setSeries(series: PriceSeries, firstData: Month, lastData: Month, showCity: Boolean) {
        this.series = series
        this.firstData = firstData
        this.lastData = lastData
        this.showCity = showCity
        invalidatePaths()
    }

    fun setOptions(inflationAdjusted: Boolean, logScale: Boolean) {
        if (this.inflationAdjusted == inflationAdjusted && this.logScale == logScale) return
        this.inflationAdjusted = inflationAdjusted
        this.logScale = logScale
        invalidatePaths()
    }

    fun setSelected(month: Month) {
        selected = month
        updateDescription()
        invalidate()
    }

    private fun invalidatePaths() {
        paths = null
        updateDescription()
        invalidate()
    }

    private fun value(array: DoubleArray, i: Int): Double {
        val s = series!!
        return if (inflationAdjusted) array[i] * s.toReference[i] else array[i]
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        paths = null
    }

    private fun layoutPlot() {
        val s = series ?: return
        var lo = Double.MAX_VALUE
        var hi = 0.0
        for (i in 0 until s.size) {
            lo = min(lo, value(s.low, i))
            hi = max(hi, value(s.high, i))
            if (showCity) {
                lo = min(lo, value(s.city, i))
                hi = max(hi, value(s.city, i))
            }
        }
        if (logScale) {
            yMin = 10.0.pow(floor(log10(lo) * 2) / 2)
            yMax = 10.0.pow(ceil(log10(hi) * 2) / 2)
        } else {
            yMin = 0.0
            yMax = niceCeiling(hi * 1.04)
        }
        val labelWidth = yTicks().maxOf { axisText.measureText(Format.compact(it)) }
        plot.set(labelWidth + dp(8f), dp(30f), width - dp(6f), height - dp(20f))
    }

    private fun x(i: Int): Float {
        val s = series!!
        return plot.left + plot.width() * i / (s.size - 1).coerceAtLeast(1)
    }

    private fun y(v: Double): Float {
        val f = if (logScale) (ln(v) - ln(yMin)) / (ln(yMax) - ln(yMin)) else (v - yMin) / (yMax - yMin)
        return (plot.bottom - plot.height() * f).toFloat()
    }

    private fun yTicks(): List<Double> {
        if (!logScale) {
            val ticks = ArrayList<Double>()
            val step = niceStep(yMax / 4)
            var v = 0.0
            while (v <= yMax + 1e-6) { ticks += v; v += step }
            return ticks
        }
        // 1-2-5 per decade, thinned to 1-3 or whole decades on a tall range.
        for (multiples in listOf(doubleArrayOf(1.0, 2.0, 5.0), doubleArrayOf(1.0, 3.0), doubleArrayOf(1.0))) {
            val ticks = ArrayList<Double>()
            var decade = 10.0.pow(floor(log10(yMin)))
            while (decade <= yMax) {
                for (m in multiples) {
                    val v = decade * m
                    if (v >= yMin * 0.999 && v <= yMax * 1.001) ticks += v
                }
                decade *= 10
            }
            if (ticks.size <= 7) return ticks
        }
        return listOf(yMin, yMax)
    }

    private fun niceStep(raw: Double): Double {
        val magnitude = 10.0.pow(floor(log10(raw)))
        val f = raw / magnitude
        return magnitude * when { f <= 1 -> 1.0; f <= 2 -> 2.0; f <= 2.5 -> 2.5; f <= 5 -> 5.0; else -> 10.0 }
    }

    private fun niceCeiling(v: Double): Double {
        val step = niceStep(v / 4)
        return ceil(v / step) * step
    }

    private fun buildPaths() {
        val s = series ?: return
        layoutPlot()
        val line = Path()
        val band = Path()
        for (i in 0 until s.size) {
            val px = x(i)
            val py = y(value(s.price, i))
            if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
            if (i == 0) band.moveTo(px, y(value(s.high, i))) else band.lineTo(px, y(value(s.high, i)))
        }
        for (i in s.size - 1 downTo 0) band.lineTo(x(i), y(value(s.low, i)))
        band.close()
        val city = if (showCity) Path().also { p ->
            for (i in 0 until s.size) {
                val px = x(i)
                val py = y(value(s.city, i))
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
        } else null
        paths = Triple(line, band, city)
    }

    override fun onDraw(canvas: Canvas) {
        val s = series ?: return
        if (paths == null) buildPaths()
        val (line, band, city) = paths ?: return

        // Back-cast and projection regions.
        val first = firstData
        val last = lastData
        if (first != null && first > s.start) {
            val right = x(s.indexOf(first))
            canvas.drawRect(plot.left, plot.top, right, plot.bottom, shadePaint)
            drawRegionLabel(canvas, "Back-cast", plot.left, right)
        }
        if (last != null && last < s.month(s.size - 1)) {
            val left = x(s.indexOf(last))
            canvas.drawRect(left, plot.top, plot.right, plot.bottom, shadePaint)
            drawRegionLabel(canvas, "Projection", left, plot.right)
        }

        // Grid and axes.
        for (v in yTicks()) {
            val py = y(v)
            canvas.drawLine(plot.left, py, plot.right, py, gridPaint)
            val label = Format.compact(v)
            canvas.drawText(label, plot.left - dp(6f) - axisText.measureText(label), py + axisText.textSize / 3, axisText)
        }
        val startYear = s.start.year
        val endYear = s.month(s.size - 1).year
        val span = endYear - startYear
        val step = when { span > 120 -> 30; span > 60 -> 20; span > 30 -> 10; else -> 5 }
        var year = ((startYear + step - 1) / step) * step
        while (year <= endYear) {
            val px = x(s.indexOf(Month.of(year, 1)))
            canvas.drawLine(px, plot.bottom, px, plot.bottom + dp(4f), gridPaint)
            val label = year.toString()
            val tw = axisText.measureText(label)
            val tx = (px - tw / 2).coerceIn(plot.left - tw / 2, width - tw)
            canvas.drawText(label, tx, plot.bottom + dp(16f), axisText)
            year += step
        }

        canvas.save()
        canvas.clipRect(plot.left, plot.top - dp(2f), plot.right, plot.bottom)
        canvas.drawPath(band, bandPaint)
        city?.let { canvas.drawPath(it, cityPaint) }
        canvas.drawPath(line, linePaint)
        canvas.restore()

        // Cursor.
        val m = selected ?: return
        val i = s.indexOf(m)
        val px = x(i)
        val py = y(value(s.price, i))
        canvas.drawLine(px, plot.top, px, plot.bottom, cursorPaint)
        canvas.drawCircle(px, py, dp(6f), dotRing)
        canvas.drawCircle(px, py, dp(4.5f), dotPaint)
        val text = m.shortLabel() + "  " + Format.compact(value(s.price, i))
        val tw = bubbleText.measureText(text)
        val pad = dp(8f)
        val bw = tw + pad * 2
        val bh = dp(22f)
        val left = (px - bw / 2).coerceIn(0f, width - bw)
        val rect = RectF(left, dp(2f), left + bw, dp(2f) + bh)
        canvas.drawRoundRect(rect, bh / 2, bh / 2, bubblePaint)
        canvas.drawText(text, rect.left + pad, rect.centerY() + bubbleText.textSize / 3, bubbleText)
    }

    private fun drawRegionLabel(canvas: Canvas, text: String, left: Float, right: Float) {
        val tw = regionText.measureText(text)
        if (right - left < tw + dp(8f)) return
        canvas.drawText(text, (left + right - tw) / 2, plot.bottom - dp(6f), regionText)
    }

    private fun monthAt(x: Float): Month? {
        val s = series ?: return null
        if (plot.width() <= 0) return null
        val f = ((x - plot.left) / plot.width()).coerceIn(0f, 1f)
        return s.month(((s.size - 1) * f).roundToInt())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (series == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    if (dx > touchSlop && dx > dy) {
                        // A sideways drag scrubs through time; keep the page from scrolling.
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    } else if (dy > touchSlop) {
                        return false
                    }
                }
                if (dragging) pick(event.x)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    pick(event.x)
                    performClick()
                }
                dragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun pick(x: Float) {
        val m = monthAt(x) ?: return
        if (m != selected) {
            setSelected(m)
            onMonthPicked?.invoke(m)
        }
    }

    private fun updateDescription() {
        val s = series ?: return
        val m = selected ?: return
        val i = s.indexOf(m)
        contentDescription = "Price chart, ${s.start.year} to ${s.month(s.size - 1).year}. " +
            "${m.label()}: ${Format.money(value(s.price, i))}" + (if (inflationAdjusted) " after inflation" else "")
    }

    /** For tests: the x coordinate of [month] on screen. */
    internal fun xOf(month: Month): Float {
        if (paths == null) buildPaths()
        val s = series ?: return 0f
        return x(s.indexOf(month))
    }
}
