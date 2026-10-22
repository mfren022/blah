package com.ultimatesudoku.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

/** A thumbnail of a starting board: each clue is a filled square, so the clue pattern shows at a glance. */
class MiniBoardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val palette = Palette(context)
    private val fill = Paint()
    private val line = Paint().apply { style = Paint.Style.STROKE }

    var givens: IntArray = IntArray(81)
        set(value) {
            field = value
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            dp(52)
        } else {
            MeasureSpec.getSize(widthMeasureSpec)
        }
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val size = width.toFloat()
        val border = maxOf(1f, dp(1.5f).roundToInt().toFloat())
        val span = size - 2 * border
        fun edge(i: Int) = (border + span * i / 9f).roundToInt().toFloat()

        fill.color = palette.board
        canvas.drawRect(0f, 0f, size, size, fill)
        fill.color = palette.accent
        val inset = span / 9f * 0.16f
        for (cell in 0 until 81) {
            if (givens[cell] == 0) continue
            val r = cell / 9
            val c = cell % 9
            canvas.drawRect(edge(c) + inset, edge(r) + inset, edge(c + 1) - inset, edge(r + 1) - inset, fill)
        }
        line.color = palette.lineStrong
        line.strokeWidth = maxOf(1f, dp(1f).roundToInt().toFloat())
        for (i in intArrayOf(3, 6)) {
            canvas.drawLine(edge(i), edge(0), edge(i), edge(9), line)
            canvas.drawLine(edge(0), edge(i), edge(9), edge(i), line)
        }
        line.strokeWidth = border
        canvas.drawRect(border / 2, border / 2, size - border / 2, size - border / 2, line)
    }
}
