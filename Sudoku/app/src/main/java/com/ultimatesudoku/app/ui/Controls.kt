package com.ultimatesudoku.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CompoundButton
import kotlin.math.max

/** An on/off switch with a square track and a square thumb. */
class SquareToggle @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val palette = Palette(context)
    private val paint = Paint()
    private var position = 0f
    private var animator: ValueAnimator? = null

    var onChange: ((Boolean) -> Unit)? = null

    var isChecked = false
        private set

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { setChecked(!isChecked, animate = true, notify = true) }
    }

    fun setChecked(checked: Boolean, animate: Boolean = false, notify: Boolean = false) {
        if (checked == isChecked && !animate) {
            position = if (checked) 1f else 0f
            invalidate()
            return
        }
        isChecked = checked
        val target = if (checked) 1f else 0f
        animator?.cancel()
        if (animate) {
            animator = ValueAnimator.ofFloat(position, target).apply {
                duration = 140
                addUpdateListener {
                    position = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            position = target
            invalidate()
        }
        if (notify) onChange?.invoke(checked)
    }

    override fun getAccessibilityClassName(): CharSequence = CompoundButton::class.java.name

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isCheckable = true
        info.isChecked = isChecked
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dp(46), dp(26))
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.4f
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val stroke = dp(1.5f)
        // Track: outlined when off, filled with the accent when on (cross-faded while moving).
        paint.style = Paint.Style.FILL
        paint.color = palette.bg
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.color = palette.accent
        paint.alpha = (position * 255).toInt()
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.alpha = 255
        if (position < 1f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = palette.inkMuted
            paint.alpha = ((1f - position) * 255).toInt()
            canvas.drawRect(stroke / 2, stroke / 2, w - stroke / 2, h - stroke / 2, paint)
            paint.alpha = 255
        }
        val inset = dp(4f)
        val thumb = h - 2 * inset
        val left = inset + (w - 2 * inset - thumb) * position
        paint.style = Paint.Style.FILL
        paint.color = if (position > 0.5f) palette.onAccent else palette.inkMuted
        canvas.drawRect(left, inset, left + thumb, inset + thumb, paint)
    }
}

/** A row of mutually exclusive options in square cells, the chosen one filled with the accent. */
class SegmentedView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val palette = Palette(context)
    private val fonts = Fonts.get(context)
    private val fill = Paint()
    private val stroke = Paint().apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    var options: List<String> = emptyList()
        set(value) {
            field = value
            contentDescription = value.joinToString()
            requestLayout()
            invalidate()
        }

    var selectedIndex = 0
        set(value) {
            field = value
            invalidate()
        }

    var onSelect: ((Int) -> Unit)? = null

    init {
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(44))
    }

    override fun onDraw(canvas: Canvas) {
        if (options.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val segment = w / options.size
        val line = max(1f, dp(1f))
        text.typeface = fonts.medium
        text.textSize = sp(12f)
        text.letterSpacing = 0.08f
        val fm = text.fontMetrics
        for ((i, option) in options.withIndex()) {
            val left = i * segment
            if (i == selectedIndex) {
                fill.color = palette.accent
                canvas.drawRect(left, 0f, left + segment, h, fill)
            }
            text.color = if (i == selectedIndex) palette.onAccent else palette.ink
            canvas.drawText(option.uppercase(), left + segment / 2, h / 2 - (fm.ascent + fm.descent) / 2, text)
        }
        stroke.color = palette.line
        stroke.strokeWidth = line
        canvas.drawRect(line / 2, line / 2, w - line / 2, h - line / 2, stroke)
        for (i in 1 until options.size) {
            if (i == selectedIndex || i == selectedIndex + 1) continue
            val x = i * segment
            canvas.drawLine(x, 0f, x, h, stroke)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || options.isEmpty()) return false
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val index = (event.x / (width.toFloat() / options.size)).toInt().coerceIn(0, options.size - 1)
            performClick()
            if (index != selectedIndex) {
                selectedIndex = index
                onSelect?.invoke(index)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
