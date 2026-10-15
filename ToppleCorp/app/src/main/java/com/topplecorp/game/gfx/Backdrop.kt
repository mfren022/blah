package com.topplecorp.game.gfx

import android.graphics.Bitmap
import android.graphics.Canvas
import kotlin.math.sin
import kotlin.random.Random

/** Colour scheme for each city. */
data class CityTheme(
    val skyTop: Int,
    val skyBottom: Int,
    val far: Int,
    val near: Int,
    val window: Int,
    val ground: Int,
    val groundEdge: Int,
    val accent: Int,
    val night: Boolean = false,
    val sun: Int = 0xFFFFF1B8.toInt(),
)

object Themes {
    val cities = listOf(
        // Toronto: clear day
        CityTheme(0xFF5BB8F0.toInt(), 0xFFD4F0FF.toInt(), 0xFF9DB8D2.toInt(), 0xFF6C8FA8.toInt(), 0xFFFFE89C.toInt(),
            0xFFC9C2B5.toInt(), 0xFF8E867A.toInt(), 0xFFE8303A.toInt()),
        // Sydney: bright harbour
        CityTheme(0xFF3FA9F0.toInt(), 0xFFC5ECFF.toInt(), 0xFF8FB0D2.toInt(), 0xFF5D7FA3.toInt(), 0xFFE0F0FF.toInt(),
            0xFFE3C58F.toInt(), 0xFFB89A66.toInt(), 0xFF2E8BD0.toInt()),
        // Istanbul: sunset
        CityTheme(0xFF5B3F8C.toInt(), 0xFFF7A55A.toInt(), 0xFF9C6E8F.toInt(), 0xFF6B4A6A.toInt(), 0xFFFFD9A0.toInt(),
            0xFFB08D6E.toInt(), 0xFF7A5E48.toInt(), 0xFF2BA3A6.toInt(), sun = 0xFFFFD27A.toInt()),
        // Zurich: alpine morning
        CityTheme(0xFF7DBBE6.toInt(), 0xFFEAF6FF.toInt(), 0xFF8A9BB8.toInt(), 0xFF6F809E.toInt(), 0xFFFFF7D6.toInt(),
            0xFFA9A49A.toInt(), 0xFF6E6A62.toInt(), 0xFFE03A3E.toInt(), sun = 0xFFFFFBE0.toInt()),
        // San Francisco: golden hour
        CityTheme(0xFFFF8E6B.toInt(), 0xFFFFDDB0.toInt(), 0xFFA88AA0.toInt(), 0xFF8C6E8E.toInt(), 0xFFFFE2B8.toInt(),
            0xFF6E6A70.toInt(), 0xFF4A464C.toInt(), 0xFFC8372D.toInt(), sun = 0xFFFFF0C0.toInt()),
    )

    fun of(city: Int) = cities[city.coerceIn(0, cities.size - 1)]
}

/**
 * Parallax city backdrop. Two skyline layers move at different speeds as the camera scrolls,
 * which together with drifting clouds gives the scene depth.
 */
class Backdrop(val theme: CityTheme, val width: Int, seed: Int) {
    private val far: Bitmap = skyline(theme.far, Pal.tint(theme.window, 0.0f), 0.25f, 60, 120, seed)
    private val near: Bitmap = skyline(theme.near, theme.window, 0.45f, 40, 90, seed + 99)
    private val stars = List(40) { Random(seed + it).let { r -> Triple(r.nextInt(width), r.nextInt(400), r.nextFloat()) } }
    private val clouds = List(5) { Random(seed * 3 + it).let { r -> floatArrayOf(r.nextFloat() * width, 20f + r.nextFloat() * 160f, 0.3f + r.nextFloat() * 0.7f) } }
    private var time = 0f

    private fun skyline(color: Int, window: Int, windowChance: Float, minH: Int, maxH: Int, seed: Int): Bitmap {
        val w = width
        val h = maxH + 12
        val px = IntArray(w * h)
        val rnd = Random(seed)
        var x = -rnd.nextInt(10)
        while (x < w) {
            val bw = 14 + rnd.nextInt(26)
            val bh = minH + rnd.nextInt(maxH - minH)
            val top = h - bh
            val shade = Pal.shade(color, 0.9f + rnd.nextFloat() * 0.15f)
            for (yy in top until h) for (xx in x until x + bw) {
                if (xx !in 0 until w) continue
                px[yy * w + xx] = if (xx == x + bw - 1) Pal.shade(shade, 0.8f) else shade
            }
            // Rooftop details
            if (rnd.nextFloat() < 0.5f) {
                val ax = x + 3 + rnd.nextInt((bw - 6).coerceAtLeast(1))
                for (yy in (top - 8).coerceAtLeast(0) until top) if (ax in 0 until w) px[yy * w + ax] = shade
            } else if (rnd.nextFloat() < 0.5f) {
                for (yy in (top - 3).coerceAtLeast(0) until top) for (xx in x + 3 until x + bw - 3) if (xx in 0 until w) px[yy * w + xx] = shade
            }
            // Windows
            var wy = top + 4
            while (wy < h - 4) {
                var wx = x + 3
                while (wx < x + bw - 4) {
                    if (rnd.nextFloat() < windowChance) {
                        for (yy in wy until wy + 2) for (xx in wx until wx + 2) if (xx in 0 until w) px[yy * w + xx] = window
                    }
                    wx += 4
                }
                wy += 5
            }
            x += bw + rnd.nextInt(3)
        }
        return Sprite.fromPixels(px, w, h)
    }

    fun update(dt: Float) {
        time += dt
        for (c in clouds) {
            c[0] += dt * 4f * c[2]
            if (c[0] > width + 40) c[0] = -40f
        }
    }

    /**
     * Draws the backdrop. [groundY] is the screen y of the street surface when the camera is at rest;
     * [cam] is how far the camera has scrolled up (positive = looking higher).
     */
    fun draw(c: Canvas, h: Float, groundY: Float, cam: Float) {
        Draw.gradient(c, 0f, 0f, width.toFloat(), h, theme.skyTop, theme.skyBottom, 32)
        if (theme.night) {
            for ((sx, sy, tw) in stars) {
                val a = 0.4f + 0.6f * ((sin(time * 2f + tw * 10f) + 1f) / 2f)
                Draw.rect(c, sx.toFloat(), (sy + cam * 0.05f) % h, 1f, 1f, Pal.alpha(Pal.WHITE, a))
            }
        }
        // Sun / moon
        val sunY = 60f + cam * 0.08f
        Draw.rect(c, width * 0.72f - 9, sunY - 9, 18f, 18f, Pal.alpha(theme.sun, 0.25f))
        Draw.rect(c, width * 0.72f - 7, sunY - 7, 14f, 14f, theme.sun)
        // Clouds
        for (cl in clouds) {
            val cy = cl[1] + cam * 0.15f * cl[2]
            val cw = 18f + cl[2] * 22f
            val col = if (theme.night) Pal.alpha(0xFF6B5A9E.toInt(), 0.5f) else Pal.alpha(Pal.WHITE, 0.85f)
            Draw.rect(c, cl[0], cy, cw, 5f, col)
            Draw.rect(c, cl[0] + cw * 0.2f, cy - 3, cw * 0.5f, 3f, col)
        }
        // Far and near skylines with parallax
        Draw.bitmap(c, far, 0f, groundY - far.height + 6 + cam * 0.3f)
        Draw.bitmap(c, near, 0f, groundY - near.height + 8 + cam * 0.6f)
    }

    /** Street the tower and crew stand on. Scrolls with the world. */
    fun drawGround(c: Canvas, groundY: Float, h: Float) {
        Draw.rect(c, 0f, groundY, width.toFloat(), h - groundY, theme.ground)
        Draw.rect(c, 0f, groundY, width.toFloat(), 2f, Pal.tint(theme.ground, 0.25f))
        Draw.rect(c, 0f, groundY + 2, width.toFloat(), 1f, theme.groundEdge)
        // Paving lines for depth
        var x = 0f
        while (x < width) {
            Draw.rect(c, x, groundY + 8, 8f, 1f, Pal.alpha(theme.groundEdge, 0.5f))
            Draw.rect(c, x + 6, groundY + 18, 10f, 1f, Pal.alpha(theme.groundEdge, 0.4f))
            x += 16f
        }
    }
}
