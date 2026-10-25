package com.draftpad.app

import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.widget.Toast

fun Context.dp(value: Float): Float = value * resources.displayMetrics.density

fun Context.toast(text: Int) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

fun Context.toast(text: CharSequence) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

/**
 * Pads [view] by the system bars and display cutout, so its content stays clear of them when the
 * window is drawn edge to edge (always on Android 15 and newer), while its background fills behind.
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
    intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
}
