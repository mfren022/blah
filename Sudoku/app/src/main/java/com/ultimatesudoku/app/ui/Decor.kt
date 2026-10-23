package com.ultimatesudoku.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** Difficulty shown as five squares, the first [level] filled. */
class LevelView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val palette = Palette(context)
    private val paint = Paint()

    var level = 1
        set(value) {
            field = value
            contentDescription = "Level $value of $LEVELS"
            invalidate()
        }

    private val square = dp(7f)
    private val gap = dp(3f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((square * LEVELS + gap * (LEVELS - 1)).toInt() + 1, (square * 3).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        for (i in 0 until LEVELS) {
            // Bars step up like a signal meter.
            val h = square + (square * 2) * i / (LEVELS - 1)
            val left = i * (square + gap)
            val top = height - h
            paint.color = if (i < level) palette.accent else palette.line
            canvas.drawRect(left, top, left + square, height.toFloat(), paint)
        }
    }

    private companion object {
        const val LEVELS = 5
    }
}

/** A faint sudoku grid fading in from the right edge, for dark-blue banners. */
class GridMotifView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val palette = Palette(context)
    private val paint = Paint().apply { style = Paint.Style.STROKE }

    override fun onDraw(canvas: Canvas) {
        val cell = dp(22f)
        val cols = 9
        val left = width - cell * (cols - 2)
        paint.color = palette.onAccent
        // Vertical lines fade in towards the right edge.
        for (i in 0..cols) {
            val box = i % 3 == 0
            paint.strokeWidth = if (box) dp(1.6f) else dp(0.8f)
            paint.alpha = ((if (box) 46 else 22) * i / cols.toFloat()).toInt() + 6
            val x = left + i * cell
            canvas.drawLine(x, 0f, x, height.toFloat(), paint)
        }
        // Horizontal lines run the full height, drawn in segments that share the same fade.
        var row = 0
        var y = cell * 0.5f
        while (y < height) {
            val box = row % 3 == 0
            paint.strokeWidth = if (box) dp(1.6f) else dp(0.8f)
            for (i in 0 until cols) {
                paint.alpha = ((if (box) 46 else 22) * (i + 0.5f) / cols).toInt() + 6
                canvas.drawLine(left + i * cell, y, left + (i + 1) * cell, y, paint)
            }
            row++
            y += cell
        }
    }
}

/**
 * Placeholder art for a story chapter: small white squares 1dp apart, lit by a circular gradient at
 * full strength just left of the screen's top-right corner that fades to nothing by the middle of the
 * screen. The gradient is placed on the screen, not on this view, which may run on below it.
 */
class SquareGlowView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint().apply { color = Color.WHITE }

    override fun onDraw(canvas: Canvas) {
        val screenHeight = rootView.height.takeIf { it > 0 } ?: height
        val gap = max(1, dp(GAP).roundToInt())
        // Whole pixels for every square, and as many as fit across, centred, with the same margin at the top.
        val columns = max(1, (width + gap) / (max(1, dp(PITCH - GAP).roundToInt()) + gap))
        val size = (width - gap * (columns - 1)) / columns
        val pitch = size + gap
        val left = (width - columns * pitch + gap) / 2
        val cx = width * CENTRE_X
        val cy = screenHeight * CENTRE_Y
        val radius = hypot(cx - width / 2f, cy - screenHeight / 2f)
        var top = left
        while (top < height && top < cy + radius) {
            for (column in 0 until columns) {
                val x = left + column * pitch
                val distance = hypot(x + size / 2f - cx, top + size / 2f - cy) / radius
                if (distance >= 1f) continue
                // Eased, so the light gathers in the corner rather than washing over the screen.
                val light = 1f - distance
                paint.alpha = (255 * light * light).roundToInt()
                canvas.drawRect(x.toFloat(), top.toFloat(), (x + size).toFloat(), (top + size).toFloat(), paint)
            }
            top += pitch
        }
    }

    private companion object {
        /** From one square to the next, setting how dense the grid is; the gap comes out of it. */
        const val PITCH = 6.5f
        const val GAP = 1f

        /** The gradient's centre, as fractions of the screen's width and height. */
        const val CENTRE_X = 0.82f
        const val CENTRE_Y = 0.03f
    }
}

/** Squares of paper tumbling down the screen for a few seconds. */
class ConfettiView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private class Piece(
        val x: Float,
        val size: Float,
        val speed: Float,
        val drift: Float,
        val spin: Float,
        val delay: Float,
        val color: Int,
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pieces = emptyList<Piece>()
    private var startedAt = 0L

    fun burst(seed: Long = SystemClock.uptimeMillis()) {
        val random = Random(seed)
        val palette = Palette(context)
        val colors = intArrayOf(palette.onAccent, palette.accentMid, palette.accentSoft, 0xFF7FA8FF.toInt())
        pieces = List(90) {
            Piece(
                x = random.nextFloat(),
                size = dp(5f) + random.nextFloat() * dp(7f),
                speed = 0.18f + random.nextFloat() * 0.25f,
                drift = (random.nextFloat() - 0.5f) * 0.12f,
                spin = (random.nextFloat() - 0.5f) * 720f,
                delay = random.nextFloat() * 0.9f,
                color = colors[random.nextInt(colors.size)],
            )
        }
        startedAt = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        if (pieces.isEmpty()) return
        val t = (SystemClock.uptimeMillis() - startedAt) / 1000f
        var alive = false
        for (p in pieces) {
            val life = t - p.delay
            if (life < 0) {
                alive = true
                continue
            }
            val y = -p.size + life * p.speed * height * 1.2f
            if (y > height + p.size) continue
            alive = true
            val x = (p.x + p.drift * life) * width
            paint.color = p.color
            paint.alpha = (255 * (1f - (life / 5f)).coerceIn(0f, 1f)).toInt()
            canvas.save()
            canvas.rotate(p.spin * life, x, y)
            canvas.drawRect(x - p.size / 2, y - p.size / 2, x + p.size / 2, y + p.size / 2, paint)
            canvas.restore()
        }
        if (alive) postInvalidateOnAnimation() else pieces = emptyList()
    }
}
