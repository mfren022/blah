package com.ottawahomeprice.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import com.ottawahomeprice.app.geo.AddressLookup
import com.ottawahomeprice.app.geo.GeocodedAddress
import com.ottawahomeprice.app.geo.GeocodingProvider
import java.io.File
import java.io.IOException
import java.time.Duration
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Helpers for driving screens in Robolectric and saving what they look like. */
abstract class ScreenTest {

    protected val app: HomePriceApp get() = RuntimeEnvironment.getApplication() as HomePriceApp

    protected fun idle(ms: Long = 0) {
        val looper = shadowOf(Looper.getMainLooper())
        if (ms > 0) looper.idleFor(Duration.ofMillis(ms)) else looper.idle()
    }

    /** Taps [view] at a point given as fractions of its size. */
    protected fun tap(view: View, fx: Float = 0.5f, fy: Float = 0.5f) {
        val x = view.width * fx
        val y = view.height * fy
        val time = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, x, y, 0))
        idle()
    }

    /** Drags across [view] from one point to another (fractions of its size). */
    protected fun drag(view: View, fromX: Float, toX: Float, fy: Float = 0.5f, steps: Int = 10) {
        val y = view.height * fy
        val start = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, view.width * fromX, y, 0))
        for (i in 1..steps) {
            val x = view.width * (fromX + (toX - fromX) * i / steps)
            view.dispatchTouchEvent(MotionEvent.obtain(start, start + i * 16L, MotionEvent.ACTION_MOVE, x, y, 0))
        }
        view.dispatchTouchEvent(MotionEvent.obtain(start, start + steps * 16L + 16, MotionEvent.ACTION_UP, view.width * toX, y, 0))
        idle()
    }

    /** Draws the visible window into a PNG under build/screenshots. */
    protected fun capture(activity: Activity, name: String) {
        idle()
        val view = activity.window.decorView
        write(draw(view, view.width, view.height), name)
    }

    /** Draws the whole scrolling content of [scroll], top to bottom, as the user would scroll through it. */
    protected fun captureScroll(scroll: ScrollView, name: String) {
        idle()
        val content = scroll.getChildAt(0)
        val bitmap = Bitmap.createBitmap(scroll.width, content.height + scroll.paddingTop + scroll.paddingBottom, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        scroll.background?.let { it.setBounds(0, 0, bitmap.width, bitmap.height); it.draw(canvas) }
        canvas.translate(content.left.toFloat(), (scroll.paddingTop).toFloat())
        content.draw(canvas)
        write(bitmap, name)
    }

    private fun draw(view: View, w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    protected fun write(bitmap: Bitmap, name: String) {
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    protected fun <T : View> Activity.view(id: Int): T = findViewById(id)

    /** Waits for background work (lookups, map shading) to post its result. */
    protected fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            Thread.sleep(20)
            idle()
        }
    }

    /** A geocoder that answers from a fixed table (or fails like a phone with no network). */
    protected class FakeGeocoder(
        private val answers: Map<String, List<GeocodedAddress>>,
        private val offline: Boolean = false,
    ) : GeocodingProvider {
        override val name = "fake"
        val queries = ArrayList<String>()

        override fun search(query: String): List<GeocodedAddress> {
            queries += query
            if (offline) throw IOException("offline")
            return answers.entries.firstOrNull { query.startsWith(it.key) }?.value ?: emptyList()
        }
    }

    protected fun useGeocoder(fake: FakeGeocoder) {
        app.lookup = AddressLookup(app.estimator, listOf(fake))
    }
}
