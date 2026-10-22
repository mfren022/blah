package com.ultimatesudoku.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.ultimatesudoku.app.R
import kotlin.math.min

/** Digits 1-9 as flat square keys, in one row of nine or a 3x3 block, with optional counts. */
class NumberPadView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    fun interface OnDigitListener {
        fun onDigit(digit: Int)
    }

    var onDigit: OnDigitListener? = null

    private val palette = Palette(context)
    private val fonts = Fonts.get(context)
    private val columns: Int
    private val gap = dp(6f)

    /** Digits left to place, indexed 1..9; null hides the counts. */
    var remaining: IntArray? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Pencil mode: keys enter notes instead of digits. */
    var notesMode = false
        set(value) {
            field = value
            invalidate()
        }

    /** Hide a digit's key once all nine are placed. */
    var hideFinished = true
        set(value) {
            field = value
            invalidate()
        }

    private var pressed = -1
    private val keyPaint = Paint()
    private val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.NumberPadView)
        columns = a.getInt(R.styleable.NumberPadView_columns, 9)
        a.recycle()
        isClickable = true
    }

    private val rows get() = 9 / columns

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val keyWidth = (width - gap * (columns - 1)) / columns
        val keyHeight = if (columns == 9) min(keyWidth * 1.6f, dp(76f)) else min(keyWidth * 0.72f, dp(72f))
        var height = (keyHeight * rows + gap * (rows - 1)).toInt()
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            height = min(height, MeasureSpec.getSize(heightMeasureSpec))
        }
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            height = MeasureSpec.getSize(heightMeasureSpec)
        }
        setMeasuredDimension(width, height)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.35f
    }

    private fun keyWidth() = (width - gap * (columns - 1)) / columns
    private fun keyHeight() = (height - gap * (rows - 1)) / rows

    private fun isHidden(digit: Int) = hideFinished && (remaining?.get(digit) ?: 1) == 0

    override fun onDraw(canvas: Canvas) {
        val kw = keyWidth()
        val kh = keyHeight()
        val counts = remaining
        for (i in 0 until 9) {
            val digit = i + 1
            val left = (i % columns) * (kw + gap)
            val top = (i / columns) * (kh + gap)
            keyPaint.color = if (pressed == i) palette.surfacePressed else palette.surface
            canvas.drawRect(left, top, left + kw, top + kh, keyPaint)
            if (isHidden(digit)) continue

            val finished = counts != null && counts[digit] == 0
            val size = min(kw, kh)
            digitPaint.textSize = size * if (columns == 9) 0.62f else 0.5f
            digitPaint.typeface = if (notesMode) fonts.light else fonts.regular
            digitPaint.color = if (notesMode) palette.inkMuted else palette.userDigit
            digitPaint.alpha = if (finished) 80 else 255
            val cx = left + kw / 2
            val digitY = if (counts != null && columns == 9) top + kh * 0.42f else top + kh / 2
            val fm = digitPaint.fontMetrics
            canvas.drawText(digit.toString(), cx, digitY - (fm.ascent + fm.descent) / 2, digitPaint)

            if (counts != null) {
                countPaint.textSize = sp(11f)
                countPaint.typeface = fonts.medium
                countPaint.color = palette.inkMuted
                val text = counts[digit].toString()
                if (columns == 9) {
                    canvas.drawText(text, cx, top + kh - dp(9f), countPaint)
                } else {
                    countPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(text, left + kw - dp(8f), top + sp(11f) + dp(6f), countPaint)
                    countPaint.textAlign = Paint.Align.CENTER
                }
            }
        }
    }

    private fun keyAt(x: Float, y: Float): Int {
        val kw = keyWidth()
        val kh = keyHeight()
        val col = (x / (kw + gap)).toInt()
        val row = (y / (kh + gap)).toInt()
        if (col !in 0 until columns || row !in 0 until rows) return -1
        val index = row * columns + col
        return if (isHidden(index + 1)) -1 else index
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = keyAt(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressed >= 0 && keyAt(event.x, event.y) != pressed) {
                    pressed = -1
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                val key = pressed
                pressed = -1
                invalidate()
                if (key >= 0 && keyAt(event.x, event.y) == key) {
                    performClick()
                    onDigit?.onDigit(key + 1)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = -1
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
