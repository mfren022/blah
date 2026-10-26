package com.ultimatesudoku.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max

/**
 * Tabs along the bottom of a screen: an icon over a label for each, the chosen one in blue with a bar
 * along its top edge, and a hairline above them all.
 */
class TabBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {

    class Tab(val icon: Int, val label: String)

    fun interface OnTabSelectedListener {
        fun onTabSelected(index: Int)
    }

    var onTabSelected: OnTabSelectedListener? = null

    /** The tab chosen, from 0. */
    var selected = 0
        private set

    private val palette = Palette(context)
    private val fonts = Fonts.get(context)
    private val items = ArrayList<LinearLayout>()
    private val rule = Paint().apply { color = palette.line }
    private val bar = Paint().apply { color = palette.userDigit }

    init {
        orientation = HORIZONTAL
        setBackgroundColor(palette.bg)
        setWillNotDraw(false)
    }

    fun setTabs(tabs: List<Tab>) {
        removeAllViews()
        items.clear()
        val ripple = TypedValue().also { context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true) }
        tabs.forEachIndexed { index, tab ->
            val item = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setBackgroundResource(ripple.resourceId)
                contentDescription = tab.label
                setOnClickListener { select(index, notify = true) }
            }
            item.addView(ImageView(context).apply { setImageResource(tab.icon) }, LayoutParams(dp(24), dp(24)))
            item.addView(
                TextView(context).apply {
                    text = tab.label
                    typeface = fonts.medium
                    textSize = 11f
                    letterSpacing = 0.1f
                    isAllCaps = true
                    maxLines = 1
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) },
            )
            addView(item, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            items += item
        }
        select(selected.coerceIn(0, max(0, tabs.size - 1)))
    }

    /** Shows [index] as the chosen tab, telling the listener if [notify] is set. */
    fun select(index: Int, notify: Boolean = false) {
        selected = index
        items.forEachIndexed { i, item ->
            val chosen = i == index
            val color = if (chosen) palette.userDigit else palette.inkMuted
            item.isSelected = chosen
            (item.getChildAt(0) as ImageView).setColorFilter(color)
            (item.getChildAt(1) as TextView).setTextColor(color)
        }
        invalidate()
        if (notify) onTabSelected?.onTabSelected(index)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), max(1, dp(1)).toFloat(), rule)
        val item = items.getOrNull(selected) ?: return
        canvas.drawRect(item.left.toFloat(), 0f, item.right.toFloat(), dp(3f), bar)
    }
}
