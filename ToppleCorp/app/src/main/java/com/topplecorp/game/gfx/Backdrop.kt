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
    /** In-game themes derived from each city's colour identity (see [CityIdentity]). */
    val cities = CityIdentity.all.map { id ->
        CityTheme(
            skyTop = id.skyTop, skyBottom = id.skyBottom,
            far = id.hill, near = Pal.shade(id.hill, 0.75f), window = id.lit,
            ground = id.surface, groundEdge = id.surfaceEdge, accent = id.roof,
        )
    }

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
