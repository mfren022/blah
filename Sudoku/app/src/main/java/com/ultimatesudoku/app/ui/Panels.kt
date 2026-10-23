package com.ultimatesudoku.app.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ultimatesudoku.app.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A section label followed by a faint rule that runs to the end of the row. */
class SectionHeader @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    TextView(context, attrs, 0, R.style.Text_Caption) {

    private val rule = Paint().apply { color = Palette(context).line }
    private val capitals = Rect()

    init {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val text = layout ?: return
        val start = totalPaddingLeft + text.getLineRight(0) + dp(12f)
        val end = (width - totalPaddingRight).toFloat()
        if (end - start < dp(8f)) return
        // Level with the middle of the capitals rather than the middle of the line.
        paint.getTextBounds("H", 0, 1, capitals)
        val thickness = max(1, dp(1))
        val top = (totalPaddingTop + text.getLineBaseline(0) + capitals.exactCenterY() - thickness / 2f).roundToInt()
        canvas.drawRect(start, top.toFloat(), end, (top + thickness).toFloat(), rule)
    }
}

/** A full-width menu tile: icon square, title and subtitle, then a badge or a chevron. */
class TileView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {

    private val palette = Palette(context)
    private val fonts = Fonts.get(context)
    private val iconBox = FrameLayout(context)
    private val icon = ImageView(context)
    private val title = TextView(context)
    private val subtitle = TextView(context)
    private val badge = TextView(context)
    private val chevron = ImageView(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(76)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        isClickable = true
        isFocusable = true

        iconBox.addView(icon, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        addView(iconBox, LayoutParams(dp(44), dp(44)))

        val texts = LinearLayout(context).apply { orientation = VERTICAL }
        title.typeface = fonts.medium
        title.textSize = 16f
        title.letterSpacing = 0.08f
        title.isAllCaps = true
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        subtitle.typeface = fonts.regular
        subtitle.textSize = 13f
        texts.addView(title)
        texts.addView(subtitle, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })
        addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16) })

        badge.typeface = fonts.bold
        badge.textSize = 10f
        badge.letterSpacing = 0.12f
        badge.isAllCaps = true
        badge.setPadding(dp(7), dp(3), dp(7), dp(3))
        addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })

        chevron.setImageResource(R.drawable.ic_chevron)
        addView(chevron, LayoutParams(dp(20), dp(20)).apply { marginStart = dp(6) })
    }

    fun bind(iconRes: Int, title: String, subtitle: String, badge: String? = null, primary: Boolean = false) {
        this.title.text = title
        this.subtitle.text = subtitle
        icon.setImageResource(iconRes)
        contentDescription = "$title. $subtitle"
        val foreground = if (primary) palette.onAccent else palette.ink
        this.title.setTextColor(foreground)
        this.subtitle.setTextColor(if (primary) palette.onAccent else palette.inkMuted)
        this.subtitle.alpha = if (primary) 0.8f else 1f
        setBackgroundResource(if (primary) R.drawable.bg_tile_primary else R.drawable.bg_tile)
        iconBox.setBackgroundColor(if (primary) palette.accentPressed else palette.bg)
        icon.setColorFilter(if (primary) palette.onAccent else palette.userDigit)
        chevron.setColorFilter(if (primary) palette.onAccent else palette.inkMuted)
        this.badge.visibility = if (badge == null) View.GONE else View.VISIBLE
        this.badge.text = badge
        this.badge.setTextColor(if (primary) palette.accent else palette.onAccent)
        this.badge.setBackgroundColor(if (primary) palette.onAccent else palette.accent)
    }
}

/** Square dialogs and menus, styled like the rest of the app. */
object Dialogs {
    class Action(val label: String, val primary: Boolean = false, val run: () -> Unit = {})

    fun show(
        activity: Activity,
        title: String,
        message: CharSequence?,
        actions: List<Action>,
        cancelable: Boolean = true,
    ): Dialog {
        val fonts = Fonts.get(activity)
        val palette = Palette(activity)
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val panel = panel(activity, palette)
        panel.addView(TextView(activity).apply {
            text = title
            typeface = fonts.bold
            textSize = 18f
            letterSpacing = 0.1f
            isAllCaps = true
            setTextColor(palette.ink)
        })
        if (message != null) {
            panel.addView(
                TextView(activity).apply {
                    text = message
                    typeface = fonts.regular
                    textSize = 15f
                    setLineSpacing(0f, 1.25f)
                    setTextColor(palette.inkMuted)
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = activity.dp(12) },
            )
        }
        actions.forEachIndexed { i, action ->
            val style = if (action.primary) R.style.Button_Primary else R.style.Button_Outline
            val button = TextView(activity, null, 0, style).apply {
                text = action.label
                setOnClickListener {
                    dialog.dismiss()
                    action.run()
                }
            }
            panel.addView(
                button,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(52))
                    .apply { topMargin = activity.dp(if (i == 0) 24 else 8) },
            )
        }
        return present(activity, dialog, panel, cancelable)
    }

    fun menu(activity: Activity, title: String, items: List<Pair<String, () -> Unit>>): Dialog {
        val fonts = Fonts.get(activity)
        val palette = Palette(activity)
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = panel(activity, palette)
        panel.setPadding(0, activity.dp(20), 0, activity.dp(8))
        panel.addView(TextView(activity).apply {
            text = title
            typeface = fonts.medium
            textSize = 12f
            letterSpacing = 0.14f
            isAllCaps = true
            setTextColor(palette.inkMuted)
            setPadding(activity.dp(24), 0, activity.dp(24), activity.dp(8))
        })
        for ((label, run) in items) {
            panel.addView(
                TextView(activity).apply {
                    text = label
                    typeface = fonts.regular
                    textSize = 16f
                    setTextColor(palette.ink)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(activity.dp(24), 0, activity.dp(24), 0)
                    setBackgroundResource(R.drawable.bg_row)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        dialog.dismiss()
                        run()
                    }
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(56)),
            )
        }
        return present(activity, dialog, panel, cancelable = true)
    }

    private fun panel(activity: Activity, palette: Palette) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(palette.bg)
        val pad = activity.dp(24)
        setPadding(pad, pad, pad, activity.dp(20))
    }

    private fun present(activity: Activity, dialog: Dialog, panel: LinearLayout, cancelable: Boolean): Dialog {
        val palette = Palette(activity)
        // A dark-blue rule along the top edge marks every dialog.
        val frame = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(View(activity).apply { setBackgroundColor(palette.accent) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(4)))
            addView(ScrollView(activity).apply { addView(panel) })
        }
        dialog.setContentView(frame)
        dialog.setCancelable(cancelable)
        dialog.setCanceledOnTouchOutside(cancelable)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = min(activity.resources.displayMetrics.widthPixels - activity.dp(48), activity.dp(420))
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return dialog
    }
}

/** A square message bar along the bottom of the screen that fades away by itself. */
object Snack {
    private const val TAG = "snack"

    fun show(activity: Activity, message: CharSequence) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        content.findViewWithTag<View>(TAG)?.let { content.removeView(it) }
        val palette = Palette(activity)
        val view = TextView(activity).apply {
            tag = TAG
            text = message
            typeface = Fonts.get(activity).medium
            textSize = 14f
            setTextColor(palette.bg)
            setBackgroundColor(palette.ink)
            setPadding(activity.dp(18), activity.dp(14), activity.dp(18), activity.dp(14))
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        ).apply {
            val margin = activity.dp(16)
            setMargins(margin, 0, margin, margin + bottomOverlap(content))
        }
        content.addView(view, params)
        view.alpha = 0f
        view.translationY = activity.dp(12f)
        view.animate().alpha(1f).translationY(0f).setDuration(160).start()
        view.postDelayed({
            view.animate().alpha(0f).setDuration(220).withEndAction { content.removeView(view) }.start()
        }, 2600)
    }

    /** How far the navigation bar reaches into [content] (zero unless drawing edge to edge). */
    private fun bottomOverlap(content: View): Int {
        val insets = content.rootWindowInsets ?: return 0
        val bar = systemBarInsets(insets)[3]
        val location = IntArray(2)
        content.getLocationInWindow(location)
        val gapBelow = content.rootView.height - (location[1] + content.height)
        return max(0, bar - gapBelow)
    }
}
