package com.draftpad.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.min

/** A round pen-colour swatch; the selected one gets a ring. */
class SwatchView(context: Context, val color: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = context.getColor(R.color.ink)

    init {
        isClickable = true
        background = context.getDrawable(R.drawable.bg_bar_button)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(context.dp(11f), min(width, height) / 2f - context.dp(4f))
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawCircle(cx, cy, if (isSelected) r - context.dp(3f) else r, paint)
        if (isSelected) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = context.dp(2f)
            paint.color = ink
            canvas.drawCircle(cx, cy, r, paint)
        }
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        invalidate()
    }
}

/** A line-width choice, drawn as a dot of that width. */
class WidthView(context: Context, private val lineWidth: Float) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = context.getColor(R.color.icon)
    private val accent = context.getColor(R.color.accent)

    init {
        isClickable = true
        background = context.getDrawable(R.drawable.bg_tool)
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = if (isSelected) accent else ink
        // Thin lines are shown a little larger than life so all three read clearly.
        canvas.drawCircle(width / 2f, height / 2f, context.dp(1.5f + lineWidth * 0.6f), paint)
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        invalidate()
    }
}
