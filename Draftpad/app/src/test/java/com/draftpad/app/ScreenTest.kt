package com.draftpad.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.After
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/** Helpers for driving screens in Robolectric and saving what they look like. */
abstract class ScreenTest {
    protected val app get() = RuntimeEnvironment.getApplication() as DraftpadApp

    /** Lets disk and network work finish before Robolectric resets the app. */
    @After
    fun drainWork() = settle()

    protected fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Waits for the disk and network threads, then runs what they posted back to the main thread. */
    protected fun settle() {
        repeat(3) {
            app.net.submit {}.get(30, TimeUnit.SECONDS)
            app.io.submit {}.get(30, TimeUnit.SECONDS)
            idle()
        }
    }

    /** Taps [view] at a point given as fractions of its size. */
    protected fun tap(view: View, fx: Float = 0.5f, fy: Float = 0.5f) {
        val time = SystemClock.uptimeMillis()
        val x = view.width * fx
        val y = view.height * fy
        view.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(time, time + 40, MotionEvent.ACTION_UP, x, y, 0))
        idle()
    }

    /** Drags one finger across [view] through points given as fractions of its size. */
    protected fun drag(view: View, vararg points: Pair<Float, Float>) {
        val time = SystemClock.uptimeMillis()
        fun event(action: Int, i: Int, p: Pair<Float, Float>) =
            MotionEvent.obtain(time, time + i * 16L, action, view.width * p.first, view.height * p.second, 0)
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 0, points.first()))
        // Fill in between the given points, as a real finger reports many samples.
        for (i in 1 until points.size) {
            val (ax, ay) = points[i - 1]
            val (bx, by) = points[i]
            for (s in 1..8) {
                val t = s / 8f
                view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, i * 8 + s, (ax + (bx - ax) * t) to (ay + (by - ay) * t)))
            }
        }
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, points.size * 8 + 1, points.last()))
        idle()
    }

    /** Two fingers moving from [from] to [to] (each a pair of points, as fractions of the view). */
    protected fun pinch(view: View, from: List<Pair<Float, Float>>, to: List<Pair<Float, Float>>) {
        val time = SystemClock.uptimeMillis()
        val props = Array(2) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        fun coords(points: List<Pair<Float, Float>>) = Array(points.size) {
            MotionEvent.PointerCoords().apply { x = view.width * points[it].first; y = view.height * points[it].second; pressure = 1f; size = 1f }
        }
        fun event(action: Int, count: Int, points: List<Pair<Float, Float>>, t: Long) =
            MotionEvent.obtain(time, time + t, action, count, props, coords(points), 0, 0, 1f, 1f, 0, 0, 0, 0)
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 1, from.take(1), 0))
        view.dispatchTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, from, 10))
        for (s in 1..10) {
            val t = s / 10f
            val points = from.indices.map { i ->
                (from[i].first + (to[i].first - from[i].first) * t) to (from[i].second + (to[i].second - from[i].second) * t)
            }
            view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 2, points, 10L + s * 16))
        }
        view.dispatchTouchEvent(event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, to, 200))
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 1, to.take(1), 210))
        idle()
    }

    protected fun latestDialog(): AlertDialog = checkNotNull(ShadowDialog.getLatestDialog() as? AlertDialog) { "No dialog showing" }

    /** Draws the whole window into a PNG under build/screenshots. */
    protected fun capture(activity: Activity, name: String) {
        idle()
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        write(bitmap, name)
    }

    /** Draws the dialog showing (dialogs have their own window) over a dimmed copy of [activity]. */
    protected fun captureDialog(activity: Activity, name: String) {
        idle()
        val screen = activity.window.decorView
        val panel = latestDialog().window!!.decorView
        val bitmap = Bitmap.createBitmap(screen.width, screen.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        screen.draw(canvas)
        canvas.drawColor(0x8C000000.toInt())
        canvas.translate((screen.width - panel.width) / 2f, (screen.height - panel.height) / 2f)
        panel.draw(canvas)
        write(bitmap, name)
    }

    protected fun write(bitmap: Bitmap, name: String) {
        File(screenshots, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    protected val screenshots: File
        get() = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
}
