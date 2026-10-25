package com.ultimatesudoku.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.ultimatesudoku.app.R
import com.ultimatesudoku.core.LevelMap
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A chapter's levels as a node graph, like a metro map: square stops in rows and lanes, joined by
 * right-angled paths. The entrance is at the top and the chapter's final level, drawn larger with a
 * star, at the bottom. Finished levels are filled, and the paths on from them drawn bright; levels
 * ready to play are filled and ringed; the rest are drawn locked.
 */
class LevelMapView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    fun interface OnLevelTapListener {
        fun onLevelTapped(level: LevelMap.Level)
    }

    var onLevelTap: OnLevelTapListener? = null

    var map: LevelMap? = null
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    /** Numbers of the levels ready to play next. */
    var open: Set<Int> = emptySet()
        set(value) {
            field = value
            invalidate()
        }

    /** Numbers of the levels finished. */
    var finished: Set<Int> = emptySet()
        set(value) {
            field = value
            invalidate()
        }

    private val palette = Palette(context)
    private val fonts = Fonts.get(context)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.MITER
    }
    private val fill = Paint()
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val route = Path()
    private val finalMark = context.getDrawable(R.drawable.ic_star)!!.mutate()

    private val stop = dp(44f)
    private val finalStop = dp(60f)
    private val ring = dp(7f)
    private val rowGap = dp(76f)

    /** The level under the finger, by number, or 0. */
    private var pressed = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val rows = map?.rows ?: 0
        val content = if (rows == 0) 0f else 2 * ring + finalStop + (rows - 1) * rowGap
        val height = paddingTop + paddingBottom + content.roundToInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    private fun x(lane: Int): Float {
        val inner = width - paddingLeft - paddingRight
        val spread = min(inner * 0.3f, dp(116f))
        return paddingLeft + inner / 2f + (lane - 1) * spread
    }

    private fun y(row: Int): Float = paddingTop + ring + finalStop / 2 + row * rowGap

    private fun half(map: LevelMap, level: LevelMap.Level) = (if (level == map.final) finalStop else stop) / 2

    override fun onDraw(canvas: Canvas) {
        val map = map ?: return
        // Paths run centre to centre under the stops: down, across halfway between the rows, down.
        // Those on from a finished level are walked, and drawn bright.
        track.color = palette.ink
        for (path in map.paths) {
            val walked = path.from in finished
            track.strokeWidth = dp(if (walked) 3f else 2f)
            track.alpha = if (walked) 210 else 70
            val from = map.level(path.from)
            val to = map.level(path.to)
            val mid = (y(from.row) + y(to.row)) / 2
            route.reset()
            route.moveTo(x(from.lane), y(from.row))
            route.lineTo(x(from.lane), mid)
            route.lineTo(x(to.lane), mid)
            route.lineTo(x(to.lane), y(to.row))
            canvas.drawPath(route, track)
        }
        for (level in map.levels) drawStop(canvas, map, level)
    }

    private fun drawStop(canvas: Canvas, map: LevelMap, level: LevelMap.Level) {
        val cx = x(level.lane)
        val cy = y(level.row)
        val h = half(map, level)
        val isOpen = level.number in open
        val isFinished = level.number in finished
        val ink: Int
        if (isOpen || isFinished) {
            if (!isFinished) {
                // A ring marks where to go next.
                edge.color = palette.ink
                edge.alpha = 110
                edge.strokeWidth = dp(1.5f)
                canvas.drawRect(cx - h - ring, cy - h - ring, cx + h + ring, cy + h + ring, edge)
            }
            fill.color = if (pressed == level.number) palette.accentPressed else palette.accent
            canvas.drawRect(cx - h, cy - h, cx + h, cy + h, fill)
            ink = palette.onAccent
        } else {
            // Opaque first, so the paths stop at the edge.
            fill.color = palette.bg
            canvas.drawRect(cx - h, cy - h, cx + h, cy + h, fill)
            fill.color = if (pressed == level.number) palette.surfacePressed else palette.surface
            canvas.drawRect(cx - h, cy - h, cx + h, cy + h, fill)
            edge.color = palette.ink
            edge.alpha = 90
            edge.strokeWidth = dp(1.5f)
            val inset = edge.strokeWidth / 2
            canvas.drawRect(cx - h + inset, cy - h + inset, cx + h - inset, cy + h - inset, edge)
            ink = palette.inkMuted
        }
        if (level == map.final) {
            val size = (h * 0.9f).roundToInt()
            finalMark.setBounds((cx - size / 2f).roundToInt(), (cy - size / 2f).roundToInt(), (cx + size / 2f).roundToInt(), (cy + size / 2f).roundToInt())
            finalMark.setTint(ink)
            finalMark.draw(canvas)
        } else {
            label.typeface = fonts.medium
            label.textSize = sp(15f)
            label.color = ink
            val fm = label.fontMetrics
            canvas.drawText(level.number.toString(), cx, cy - (fm.ascent + fm.descent) / 2, label)
        }
    }

    private fun levelAt(x: Float, y: Float): LevelMap.Level? {
        val map = map ?: return null
        val reach = dp(6f)
        return map.levels.firstOrNull {
            val h = half(map, it) + reach
            abs(x - x(it.lane)) <= h && abs(y - y(it.row)) <= h
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = levelAt(event.x, event.y)?.number ?: 0
                invalidate()
                // Elsewhere, leave the touch to the scroll view.
                return pressed != 0
            }
            MotionEvent.ACTION_UP -> {
                val level = levelAt(event.x, event.y)
                if (level != null && level.number == pressed) {
                    performClick()
                    onLevelTap?.onLevelTapped(level)
                }
                pressed = 0
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = 0
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
