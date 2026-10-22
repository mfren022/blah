package com.ultimatesudoku.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit
import org.junit.After
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/** Helpers for driving screens in Robolectric and saving what they look like. */
abstract class ScreenTest {

    /** Lets pending disk writes finish, then closes the database before Robolectric resets the app. */
    @After
    fun closeDatabase() {
        val app = RuntimeEnvironment.getApplication() as SudokuApp
        app.io.submit {}.get(10, TimeUnit.SECONDS)
        app.history.close()
    }

    protected fun idle(ms: Long = 0) {
        val looper = shadowOf(Looper.getMainLooper())
        if (ms > 0) looper.idleFor(Duration.ofMillis(ms)) else looper.idle()
    }

    /** Taps the centre of [view], or a point inside it given as fractions of its size. */
    protected fun tap(view: View, fx: Float = 0.5f, fy: Float = 0.5f) {
        val x = view.width * fx
        val y = view.height * fy
        val time = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, x, y, 0))
        idle()
    }

    /** Draws the whole window into a PNG under build/screenshots. */
    protected fun capture(activity: Activity, name: String) {
        idle()
        save(activity.window.decorView, name)
    }

    /** Draws the dialog on show (dialogs have their own window) over a dimmed copy of [activity]. */
    protected fun captureDialog(activity: Activity, name: String) {
        idle()
        val dialog = checkNotNull(ShadowDialog.getLatestDialog()) { "No dialog showing" }
        val screen = activity.window.decorView
        val panel = dialog.window!!.decorView
        val bitmap = Bitmap.createBitmap(screen.width, screen.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        screen.draw(canvas)
        canvas.drawColor(0x8C000000.toInt())
        canvas.translate((screen.width - panel.width) / 2f, (screen.height - panel.height) / 2f)
        panel.draw(canvas)
        write(bitmap, name)
    }

    private fun save(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        write(bitmap, name)
    }

    private fun write(bitmap: Bitmap, name: String) {
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    protected fun <T : View> Activity.view(id: Int): T = findViewById(id)

    /** Waits for background work (puzzle generation) to post its result. */
    protected fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            Thread.sleep(20)
            idle()
        }
    }
}
