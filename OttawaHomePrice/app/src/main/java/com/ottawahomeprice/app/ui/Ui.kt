package com.ottawahomeprice.app.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import com.ottawahomeprice.app.R
import kotlin.math.ln
import kotlin.math.roundToInt

fun Context.dp(value: Float): Float = value * resources.displayMetrics.density
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
fun Context.sp(value: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
fun View.dp(value: Float): Float = context.dp(value)
fun View.dp(value: Int): Int = context.dp(value)
fun View.sp(value: Float): Float = context.sp(value)

val Context.isNight: Boolean
    get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/**
 * Pads [view] by the system bars and display cutout on top of its own padding. From Android 15
 * apps draw behind the bars; earlier versions report the insets the bars already take.
 */
fun fitSystemBars(view: View) {
    val left = view.paddingLeft
    val top = view.paddingTop
    val right = view.paddingRight
    val bottom = view.paddingBottom
    view.setOnApplyWindowInsetsListener { v, insets ->
        val bars = systemBarInsets(insets)
        v.setTag(R.id.system_bars, bars)
        v.setPadding(left + bars[0], top + bars[1], right + bars[2], bottom + bars[3])
        insets
    }
    view.requestApplyInsets()
}

@Suppress("DEPRECATION")
private fun systemBarInsets(insets: WindowInsets): IntArray = if (Build.VERSION.SDK_INT >= 30) {
    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
    intArrayOf(bars.left, bars.top, bars.right, bars.bottom)
} else {
    intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
}

/** Dark status and navigation bar icons on the light theme (navigation needs Android 8.1). */
@Suppress("DEPRECATION")
fun Activity.styleSystemBars() {
    if (isNight) return
    var flags = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    if (Build.VERSION.SDK_INT >= 27) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    window.decorView.systemUiVisibility = flags
}

/** [color] with its alpha replaced by [alpha] (0..1). */
fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha * 255).roundToInt().coerceIn(0, 255) shl 24)

/**
 * The map's colour scale for how prices compare with the city average: blues below, pale yellow
 * at the average, oranges and reds above (ColorBrewer RdYlBu), spaced on a log scale.
 */
object PriceColors {
    private val stops = doubleArrayOf(0.5, 0.65, 0.8, 0.92, 1.0, 1.12, 1.35, 1.7, 2.2, 3.0)
    private val colors = intArrayOf(
        0xFF313695.toInt(), 0xFF4575B4.toInt(), 0xFF74ADD1.toInt(), 0xFFABD9E9.toInt(), 0xFFFFFFBF.toInt(),
        0xFFFEE090.toInt(), 0xFFFDAE61.toInt(), 0xFFF46D43.toInt(), 0xFFD73027.toInt(), 0xFFA50026.toInt(),
    )
    private val logStops = DoubleArray(stops.size) { ln(stops[it]) }

    val minFactor: Double get() = stops.first()
    val maxFactor: Double get() = stops.last()

    fun color(factor: Double): Int {
        val x = ln(factor.coerceIn(stops.first(), stops.last()))
        var i = 0
        while (i < logStops.size - 2 && x > logStops[i + 1]) i++
        val t = ((x - logStops[i]) / (logStops[i + 1] - logStops[i])).coerceIn(0.0, 1.0).toFloat()
        val a = colors[i]
        val b = colors[i + 1]
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt(),
        )
    }
}
