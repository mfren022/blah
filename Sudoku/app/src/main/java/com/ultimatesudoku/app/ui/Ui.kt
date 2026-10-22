package com.ultimatesudoku.app.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsets
import com.ultimatesudoku.app.R

/** The current theme's colours (see attrs.xml), resolved once for drawing code. */
class Palette(context: Context) {
    private val typedValue = TypedValue()
    private val theme = context.theme

    private fun color(attr: Int): Int {
        check(theme.resolveAttribute(attr, typedValue, true)) { "Theme is missing a palette colour" }
        return typedValue.data
    }

    val bg = color(R.attr.suBg)
    val surface = color(R.attr.suSurface)
    val surfacePressed = color(R.attr.suSurfacePressed)
    val board = color(R.attr.suBoard)
    val ink = color(R.attr.suInk)
    val inkMuted = color(R.attr.suInkMuted)
    val line = color(R.attr.suLine)
    val lineStrong = color(R.attr.suLineStrong)
    val accent = color(R.attr.suAccent)
    val accentPressed = color(R.attr.suAccentPressed)
    val onAccent = color(R.attr.suOnAccent)
    val accentSoft = color(R.attr.suAccentSoft)
    val accentMid = color(R.attr.suAccentMid)
    val userDigit = color(R.attr.suUserDigit)
    val error = color(R.attr.suError)
    val errorSoft = color(R.attr.suErrorSoft)
}

/** The bundled Roboto weights. */
class Fonts(context: Context) {
    val light: Typeface = context.resources.getFont(R.font.roboto_light)
    val regular: Typeface = context.resources.getFont(R.font.roboto_regular)
    val medium: Typeface = context.resources.getFont(R.font.roboto_medium)
    val bold: Typeface = context.resources.getFont(R.font.roboto_bold)
    val black: Typeface = context.resources.getFont(R.font.roboto_black)

    companion object {
        @Volatile
        private var instance: Fonts? = null

        fun get(context: Context): Fonts =
            instance ?: synchronized(this) { instance ?: Fonts(context.applicationContext).also { instance = it } }
    }
}

fun Context.dp(value: Float): Float = value * resources.displayMetrics.density
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
fun Context.sp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

fun View.dp(value: Float): Float = context.dp(value)
fun View.dp(value: Int): Int = context.dp(value)
fun View.sp(value: Float): Float = context.sp(value)

/**
 * Pads [view] by the system bars and display cutout on top of its own padding. On Android 15 and
 * later apps draw behind the bars; earlier versions report zero insets here.
 */
fun fitSystemBars(view: View) {
    val left = view.paddingLeft
    val top = view.paddingTop
    val right = view.paddingRight
    val bottom = view.paddingBottom
    view.setOnApplyWindowInsetsListener { v, insets ->
        val (l, t, r, b) = systemBarInsets(insets)
        v.setPadding(left + l, top + t, right + r, bottom + b)
        insets
    }
    view.requestApplyInsets()
}

/** Left, top, right and bottom insets of the system bars and display cutout. */
@Suppress("DEPRECATION")
fun systemBarInsets(insets: WindowInsets): IntArray = if (Build.VERSION.SDK_INT >= 30) {
    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
    intArrayOf(bars.left, bars.top, bars.right, bars.bottom)
} else {
    intArrayOf(
        insets.systemWindowInsetLeft,
        insets.systemWindowInsetTop,
        insets.systemWindowInsetRight,
        insets.systemWindowInsetBottom,
    )
}

/** A light tap, honouring the user's vibration setting. */
fun View.tick(enabled: Boolean) {
    if (enabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}

/** A firmer buzz for a mistake. */
fun View.buzz(enabled: Boolean) {
    if (enabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}

/** "4:05", "12:30" or "1:02:03". */
fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = total / 60 % 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
