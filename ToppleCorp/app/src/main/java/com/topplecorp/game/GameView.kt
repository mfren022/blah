package com.topplecorp.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.topplecorp.game.ui.Touch
import com.topplecorp.game.ui.TouchAction
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Hosts the game loop. The game renders into a small virtual frame buffer which is then scaled up
 * with nearest-neighbour filtering by an integer factor, keeping pixels crisp on every screen.
 */
@SuppressLint("ViewConstructor")
class GameView(context: Context, private val game: Game) : SurfaceView(context), SurfaceHolder.Callback {

    private var thread: Thread? = null
    @Volatile private var running = false
    private var buffer: Bitmap? = null
    private var bufferCanvas: Canvas? = null
    private var scale = 4
    private val blit = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val dst = Rect()
    private val touches = ConcurrentLinkedQueue<Touch>()

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        synchronized(this) { setupBuffer(width, height) }
        start()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = stop()

    private fun setupBuffer(width: Int, height: Int) {
        // Aim for a virtual width of roughly 240-320 pixels using an integer scale.
        scale = (width / 240).coerceAtLeast(1)
        val vw = width / scale
        val vh = height / scale
        buffer?.recycle()
        buffer = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888)
        bufferCanvas = Canvas(buffer!!)
        // Centre the scaled buffer (any leftover pixels become a thin border).
        val ox = (width - vw * scale) / 2
        val oy = (height - vh * scale) / 2
        dst.set(ox, oy, ox + vw * scale, oy + vh * scale)
        game.resize(vw, vh)
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "ToppleCorpLoop").also { it.start() }
    }

    fun stop() {
        running = false
        thread?.let { t -> runCatching { t.join(500) } }
        thread = null
    }

    private fun loop() {
        var last = System.nanoTime()
        val frameNs = 1_000_000_000L / 60
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - last) / 1e9f).coerceAtMost(1f / 20f)
            last = now
            while (true) {
                val t = touches.poll() ?: break
                game.touch(t)
            }
            game.update(dt)
            render()
            val spent = System.nanoTime() - now
            if (spent < frameNs) {
                try { Thread.sleep((frameNs - spent) / 1_000_000L) } catch (_: InterruptedException) {}
            }
        }
    }

    private fun render() {
        val surface = holder.surface ?: return
        if (!surface.isValid) return
        val canvas = try {
            if (Build.VERSION.SDK_INT >= 26) holder.lockHardwareCanvas() else holder.lockCanvas()
        } catch (e: Exception) { null } ?: return
        try {
            synchronized(this) {
                val b = buffer ?: return
                val bc = bufferCanvas ?: return
                game.draw(bc)
                canvas.drawColor(0xFF1B1726.toInt())
                canvas.drawBitmap(b, null, dst, blit)
            }
        } finally {
            try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        // Only the primary pointer drives the UI.
        if (e.actionIndex != 0 && e.actionMasked != MotionEvent.ACTION_MOVE) return true
        val action = when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> TouchAction.DOWN
            MotionEvent.ACTION_MOVE -> TouchAction.MOVE
            MotionEvent.ACTION_UP -> TouchAction.UP
            MotionEvent.ACTION_CANCEL -> TouchAction.CANCEL
            else -> return true
        }
        val x = (e.getX(0) - dst.left) / scale
        val y = (e.getY(0) - dst.top) / scale
        touches.add(Touch(action, x, y))
        return true
    }
}
