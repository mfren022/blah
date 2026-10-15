package com.topplecorp.game.gfx

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** Tiny software painter on an ARGB buffer with alpha blending. */
internal class P(val w: Int, val h: Int) {
    val px = IntArray(w * h)

    fun set(x: Int, y: Int, c: Int) {
        if (x !in 0 until w || y !in 0 until h) return
        val a = c ushr 24
        if (a == 255) { px[y * w + x] = c; return }
        if (a == 0) return
        val d = px[y * w + x]
        val da = d ushr 24
        if (da == 0) { px[y * w + x] = c; return }
        val inv = 255 - a
        val r = (((c shr 16) and 0xFF) * a + ((d shr 16) and 0xFF) * inv) / 255
        val g = (((c shr 8) and 0xFF) * a + ((d shr 8) and 0xFF) * inv) / 255
        val b = ((c and 0xFF) * a + (d and 0xFF) * inv) / 255
        px[y * w + x] = (max(a, da) shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun rect(x: Int, y: Int, rw: Int, rh: Int, c: Int) {
        for (yy in max(0, y) until min(h, y + rh)) for (xx in max(0, x) until min(w, x + rw)) set(xx, yy, c)
    }

    fun ellipse(cx: Int, cy: Int, rx: Int, ry: Int, c: Int, topHalfOnly: Boolean = false) {
        for (dy in -ry..(if (topHalfOnly) 0 else ry)) for (dx in -rx..rx) {
            val v = (dx * dx).toFloat() / (rx * rx).coerceAtLeast(1) + (dy * dy).toFloat() / (ry * ry).coerceAtLeast(1)
            if (v <= 1f) set(cx + dx, cy + dy, c)
        }
    }

    fun circle(cx: Int, cy: Int, r: Int, c: Int) = ellipse(cx, cy, r, r, c)

    fun tri(x0: Int, y0: Int, x1: Int, y1: Int, x2: Int, y2: Int, c: Int) {
        val minX = min(x0, min(x1, x2)); val maxX = max(x0, max(x1, x2))
        val minY = min(y0, min(y1, y2)); val maxY = max(y0, max(y1, y2))
        fun edge(ax: Int, ay: Int, bx: Int, by: Int, px: Int, py: Int) = (bx - ax) * (py - ay) - (by - ay) * (px - ax)
        val area = edge(x0, y0, x1, y1, x2, y2)
        if (area == 0) return
        for (y in minY..maxY) for (x in minX..maxX) {
            val w0 = edge(x1, y1, x2, y2, x, y); val w1 = edge(x2, y2, x0, y0, x, y); val w2 = edge(x0, y0, x1, y1, x, y)
            if ((area > 0 && w0 >= 0 && w1 >= 0 && w2 >= 0) || (area < 0 && w0 <= 0 && w1 <= 0 && w2 <= 0)) set(x, y, c)
        }
    }

    fun line(x0: Int, y0: Int, x1: Int, y1: Int, c: Int, thick: Int = 1) {
        var x = x0; var y = y0
        val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            rect(x - thick / 2, y - thick / 2, thick, thick, c)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    /** Round-topped arch opening: rectangle + half ellipse on top. */
    fun arch(cx: Int, base: Int, halfW: Int, height: Int, c: Int) {
        val spring = base - height + halfW
        rect(cx - halfW, spring, halfW * 2 + 1, base - spring, c)
        ellipse(cx, spring, halfW, halfW, c, topHalfOnly = true)
    }

    /** Pointed (gothic / ottoman) arch opening. */
    fun pointedArch(cx: Int, base: Int, halfW: Int, height: Int, c: Int) {
        val spring = base - (height - halfW * 1.2f).toInt()
        rect(cx - halfW, spring, halfW * 2 + 1, base - spring, c)
        val r = halfW * 2
        for (y in spring - (halfW * 1.7f).toInt()..spring) for (x in cx - halfW..cx + halfW) {
            val dl = sqrt(((x - (cx + halfW)).toFloat()).let { it * it } + ((y - spring).toFloat()).let { it * it })
            val dr = sqrt(((x - (cx - halfW)).toFloat()).let { it * it } + ((y - spring).toFloat()).let { it * it })
            if (dl <= r && dr <= r) set(x, y, c)
        }
    }

    fun windows(x: Int, y: Int, rw: Int, rh: Int, lit: Int, dark: Int, rnd: Random, stepX: Int = 5, stepY: Int = 6, ww: Int = 2, wh: Int = 3, chance: Float = 0.45f) {
        var wy = y
        while (wy + wh <= y + rh) {
            var wx = x
            while (wx + ww <= x + rw) {
                rect(wx, wy, ww, wh, if (rnd.nextFloat() < chance) lit else dark)
                wx += stepX
            }
            wy += stepY
        }
    }

    /** Generic tower block with windows and an optional rooftop detail. */
    fun building(x: Int, ground: Int, bw: Int, bh: Int, body: Int, window: Int, rnd: Random, roof: Int = 0) {
        val top = ground - bh
        rect(x, top, bw, bh, body)
        rect(x + bw - 2, top, 2, bh, Pal.shade(body, 0.8f))
        rect(x, top, bw, 1, Pal.tint(body, 0.25f))
        windows(x + 2, top + 4, bw - 5, bh - 8, window, Pal.shade(body, 0.75f), rnd)
        when (roof) {
            1 -> rect(x + bw / 2, top - 8, 1, 8, body)
            2 -> rect(x + 2, top - 3, bw - 4, 3, Pal.shade(body, 0.9f))
        }
    }

    fun bitmap(): Bitmap = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}
