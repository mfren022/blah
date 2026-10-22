package com.ultimatesudoku.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.ultimatesudoku.app.R

/** An icon over a small uppercase label, with an optional square badge such as "ON". */
class ToolButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val palette = Palette(context)
    private val fonts = Fonts.get(context)
    private var icon: Drawable? = null

    var label: String = ""
        set(value) {
            field = value
            contentDescription = value
            invalidate()
        }

    /** Text in the corner badge, or null for none. */
    var badge: String? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Draws the icon and badge in the accent colour. */
    var active = false
        set(value) {
            field = value
            invalidate()
        }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val boxPaint = Paint()

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ToolButton)
        a.getDrawable(R.styleable.ToolButton_toolIcon)?.let { setIcon(it) }
        label = a.getString(R.styleable.ToolButton_toolLabel).orEmpty()
        a.recycle()
        val ripple = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
        setBackgroundResource(ripple.resourceId)
        isClickable = true
        isFocusable = true
    }

    fun setIcon(drawable: Drawable) {
        icon = drawable.mutate()
        invalidate()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.35f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            dp(60)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        val iconSize = dp(24)
        val labelSize = sp(11f)
        val spacing = dp(6f)
        val contentHeight = iconSize + spacing + labelSize
        val top = (height - contentHeight) / 2f
        val cx = width / 2f
        val color = if (active) palette.accent else palette.ink

        icon?.let {
            val left = (cx - iconSize / 2f).toInt()
            it.setBounds(left, top.toInt(), left + iconSize, top.toInt() + iconSize)
            it.setTint(color)
            it.draw(canvas)
        }

        labelPaint.typeface = fonts.medium
        labelPaint.textSize = labelSize
        labelPaint.letterSpacing = 0.08f
        labelPaint.color = if (active) palette.accent else palette.inkMuted
        canvas.drawText(label.uppercase(), cx, top + iconSize + spacing + labelSize * 0.8f, labelPaint)

        val text = badge ?: return
        badgePaint.typeface = fonts.bold
        badgePaint.textSize = sp(8.5f)
        badgePaint.letterSpacing = 0.05f
        val w = badgePaint.measureText(text) + dp(7f)
        val h = sp(8.5f) + dp(5f)
        val left = cx + iconSize / 2f - dp(4f)
        val boxTop = top - dp(5f)
        if (active) {
            boxPaint.style = Paint.Style.FILL
            boxPaint.color = palette.accent
            canvas.drawRect(left, boxTop, left + w, boxTop + h, boxPaint)
            badgePaint.color = palette.onAccent
        } else {
            boxPaint.style = Paint.Style.FILL
            boxPaint.color = palette.bg
            canvas.drawRect(left, boxTop, left + w, boxTop + h, boxPaint)
            boxPaint.style = Paint.Style.STROKE
            boxPaint.strokeWidth = dp(1f)
            boxPaint.color = palette.inkMuted
            canvas.drawRect(left, boxTop, left + w, boxTop + h, boxPaint)
            badgePaint.color = palette.inkMuted
        }
        val fm = badgePaint.fontMetrics
        canvas.drawText(text, left + w / 2, boxTop + h / 2 - (fm.ascent + fm.descent) / 2, badgePaint)
    }
}
