package com.topplecorp.game.gfx

import android.graphics.Bitmap
import android.graphics.Canvas
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * One continuous pixel-art panorama of all campaign cities, side by side on the same perfectly
 * horizontal ground. Each city is composed like a toy castle: its landmark "gate" is the central keep,
 * flanked by inner and outer towers joined by crenellated walls. Walls, sky and ground blend across the
 * seams so neighbouring cities merge into each other.
 */
class CityPanorama(val w: Int, val h: Int, val count: Int) {
    /** Screen y of the ground line (identical for every city). */
    val ground = h - 150
    private val pw = w * count
    private val farMargin = w / 2

    private val sky: Bitmap
    private val far: Bitmap
    private val main: Bitmap
    private val clouds: List<FloatArray>

    // Per-city palettes, blended across seams.
    private val skyTop = intArrayOf(0xFF5BB8F0.toInt(), 0xFF3FA9F0.toInt(), 0xFF5B3F8C.toInt(), 0xFF7DBBE6.toInt(), 0xFFFF8E6B.toInt())
    private val skyBottom = intArrayOf(0xFFD4F0FF.toInt(), 0xFFC5ECFF.toInt(), 0xFFF7A55A.toInt(), 0xFFEAF6FF.toInt(), 0xFFFFDDB0.toInt())
    private val wallColor = intArrayOf(0xFFE5DAC0.toInt(), 0xFFD9C29A.toInt(), 0xFFC9A27E.toInt(), 0xFFCFC3A8.toInt(), 0xFFD4D0C8.toInt())
    private val groundColor = intArrayOf(0xFFC9C2B5.toInt(), 0xFFE3C58F.toInt(), 0xFFB08D6E.toInt(), 0xFFA9A49A.toInt(), 0xFF8E8A90.toInt())
    private val farColor = intArrayOf(0xFF9DB8D2.toInt(), 0xFF8FB0D2.toInt(), 0xFF8E5F86.toInt(), 0xFF8A9BB8.toInt(), 0xFFA88AA0.toInt())

    init {
        val s = P(pw, h)
        val f = P(pw + farMargin * 2, h)
        val m = P(pw, h)
        val rnd = Random(4242)
        paintSky(s)
        paintFar(f, rnd)
        paintWallsAndGround(m)
        for (i in 0 until count) {
            val ox = i * w
            when (i) {
                0 -> toronto(m, ox, rnd)
                1 -> sydney(m, ox, rnd)
                2 -> istanbul(m, ox, rnd)
                3 -> zurich(m, ox, rnd)
                else -> sanFrancisco(m, ox, rnd)
            }
        }
        sky = s.bitmap(); far = f.bitmap(); main = m.bitmap()
        clouds = List(14) { floatArrayOf(rnd.nextFloat() * pw, 56f + rnd.nextFloat() * 120f, 0.4f + rnd.nextFloat() * 0.6f) }
    }

    /**
     * Draws the panorama so that city [pos] (fractional during transitions) is centred on screen.
     */
    fun draw(c: Canvas, pos: Float, time: Float) {
        val x = -pos * w
        Draw.bitmap(c, sky, x, 0f)
        for (cl in clouds) {
            val cw = 20f + cl[2] * 26f
            val cx = x * 0.85f + (cl[0] + time * 4f * cl[2]) % pw
            if (cx + cw < -10 || cx > w + 10) continue
            val col = Pal.alpha(0xFFFFFFFF.toInt(), 0.75f + cl[2] * 0.2f)
            Draw.rect(c, cx, cl[1], cw, 5f, col)
            Draw.rect(c, cx + cw * 0.2f, cl[1] - 3, cw * 0.45f, 3f, col)
        }
        Draw.bitmap(c, far, x * 0.7f - farMargin + w * 0.15f, 0f)
        Draw.bitmap(c, main, x, 0f)
        // Bay fog drifting over San Francisco
        val sf = 4 * w + x
        if (sf < w && sf > -w) {
            for (i in 0 until 3) {
                val fy = ground - 110f + i * 30f
                var bx = -80f + (time * (6f + i * 3f)) % 80f
                while (bx < w) {
                    Draw.rect(c, sf + bx, fy, 52f, 6f, 0x33FFFFFF)
                    Draw.rect(c, sf + bx + 10, fy - 3, 30f, 3f, 0x26FFFFFF)
                    bx += 80f
                }
            }
        }
    }

    // ------------------------------------------------------------------ blending

    /** Colour for global x, pure in each city's middle and smoothly blended across seams. */
    private fun blend(colors: IntArray, gx: Int): Int {
        val p = gx.toFloat() / w - 0.5f // 0 at the centre of city 0
        val i = kotlin.math.floor(p).toInt().coerceIn(0, count - 1)
        val j = (i + 1).coerceAtMost(count - 1)
        val t = (p - i).coerceIn(0f, 1f)
        // Hold the city's own colour for most of its width, blend in the middle 50% around the seam.
        val u = ((t - 0.25f) / 0.5f).coerceIn(0f, 1f)
        val smooth = u * u * (3 - 2 * u)
        return Pal.mix(colors[i], colors[j], smooth)
    }

    private fun paintSky(s: P) {
        for (x in 0 until pw) {
            val top = blend(skyTop, x)
            val bottom = blend(skyBottom, x)
            for (y in 0 until h) s.px[y * pw + x] = Pal.mix(top, bottom, y / (h - 1f))
        }
        // Sun or moon for each city
        val suns = listOf(0.8f to 70, 0.2f to 64, 0.25f to ground - 150, 0.78f to 60, 0.72f to ground - 180)
        suns.forEachIndexed { i, (fx, y) ->
            val x = (i * w + fx * w).toInt()
            val col = if (i == 2) 0xFFFFD27A.toInt() else 0xFFFFF4C8.toInt()
            s.circle(x, y, 15, Pal.alpha(col, 0.25f))
            s.circle(x, y, 11, col)
        }
    }

    private fun paintFar(f: P, rnd: Random) {
        val gy = ground - 30
        // Alps behind Zurich
        val zx = farMargin + 3 * w
        for ((px, ph) in listOf(0.0f to 120, 0.2f to 160, 0.45f to 140, 0.7f to 175, 0.95f to 130)) {
            val x = zx + (px * w).toInt()
            val top = gy - 40 - ph
            f.tri(x - 70, gy, x + 70, gy, x, top, 0xFF8A9BB8.toInt())
            f.tri(x, gy, x + 70, gy, x, top, 0xFF6F809E.toInt())
            f.tri(x - 20, top + 28, x + 20, top + 28, x, top, 0xFFF4F8FF.toInt())
            f.tri(x, top + 28, x + 20, top + 28, x, top, 0xFFD5DEEC.toInt())
        }
        // Hills behind Istanbul and San Francisco
        for (x in 0 until f.w) {
            val gx = x - farMargin
            val cityI = (gx / w).coerceIn(0, count - 1)
            if (cityI == 2 || cityI == 4) {
                val local = gx - cityI * w
                val edge = min(local, w - local).coerceAtLeast(0) / (w * 0.25f)
                val amp = edge.coerceAtMost(1f)
                val hy = gy - ((22 + sin(x / 30f) * 10 + sin(x / 11f) * 4) * amp).toInt()
                f.rect(x, hy, 1, gy - hy, Pal.shade(blend(farColor, gx.coerceIn(0, pw - 1)), 0.85f))
            }
        }
        // Continuous skyline behind the walls
        var x = 0
        while (x < f.w) {
            val bw = 12 + rnd.nextInt(16)
            val gx = (x - farMargin).coerceIn(0, pw - 1)
            val bh = 30 + rnd.nextInt(100)
            val col = Pal.shade(blend(farColor, gx), 0.92f + rnd.nextFloat() * 0.12f)
            f.building(x, gy, bw, bh, col, Pal.tint(col, 0.55f), rnd, rnd.nextInt(3))
            x += bw + rnd.nextInt(3)
        }
    }

    /** The shared crenellated wall and the flat ground run the full width of the panorama. */
    private fun paintWallsAndGround(m: P) {
        val wallTop = ground - WALL_H
        for (gx in 0 until pw) {
            val col = blend(wallColor, gx)
            for (y in wallTop until ground) {
                val row = (y - wallTop) / 6
                val joint = (gx + if (row % 2 == 0) 0 else 6) % 12 == 0
                val mortar = (y - wallTop) % 6 == 5
                m.px[y * pw + gx] = when {
                    mortar || joint -> Pal.shade(col, 0.82f)
                    else -> col
                }
            }
            m.px[wallTop * pw + gx] = Pal.tint(col, 0.3f)
            // Merlons aligned to the global grid so the battlements continue across seams.
            if ((gx / 6) % 2 == 0) for (y in wallTop - 6 until wallTop) m.px[y * pw + gx] = if (gx % 6 == 5) Pal.shade(col, 0.85f) else col
        }
        // Ground: perfectly horizontal for the whole panorama
        for (gx in 0 until pw) {
            val col = blend(groundColor, gx)
            m.px[ground * pw + gx] = Pal.tint(col, 0.35f)
            for (y in ground + 1 until h) {
                val d = (y - ground).toFloat() / (h - ground)
                var c = Pal.shade(col, 1f - d * 0.3f)
                if ((y - ground) % 9 == 0 || (gx + (y - ground) / 9 * 7) % 22 == 0) c = Pal.shade(c, 0.88f)
                m.px[y * pw + gx] = c
            }
        }
    }

    // ------------------------------------------------------------------ shared castle parts

    /** A square castle tower from the ground with merlons on top. */
    private fun tower(m: P, x: Int, tw: Int, th: Int, body: Int, rnd: Random, windows: Int = 0, merlons: Boolean = true) {
        val top = ground - th
        m.rect(x, top, tw, th, body)
        m.rect(x + tw - 3, top, 3, th, Pal.shade(body, 0.8f))
        m.rect(x, top, tw, 1, Pal.tint(body, 0.3f))
        if (merlons) {
            var mx = x
            while (mx < x + tw) {
                m.rect(mx, top - 5, min(4, x + tw - mx), 5, body)
                mx += 7
            }
        }
        if (windows != 0) m.windows(x + 3, top + 8, tw - 7, th - 20, windows, Pal.shade(body, 0.7f), rnd, 6, 9, 3, 4, 0.6f)
    }

    private fun tree(m: P, cx: Int, r: Int, leaf: Int) {
        m.rect(cx - 1, ground - r - 4, 3, r + 4, 0xFF5A3A28.toInt())
        m.circle(cx, ground - r * 2, r, leaf)
        m.circle(cx - r / 2, ground - r * 2 - r / 3, r / 2, Pal.tint(leaf, 0.2f))
    }

    // Symmetric layout offsets from the city centre (fractions of the screen width).
    private val outer get() = (w * 0.38f).toInt()
    private val inner get() = (w * 0.22f).toInt()

    // ------------------------------------------------------------------ Toronto

    private fun toronto(m: P, ox: Int, rnd: Random) {
        val cx = ox + w / 2
        val g = ground
        // CN Tower rising behind the keep as its spire
        val towerTop = g - 330
        for (y in towerTop + 70 until g) {
            val t = (y - towerTop - 70f) / (g - towerTop - 70f)
            val half = (2 + t * 6).roundToInt()
            m.rect(cx - half, y, half * 2 + 1, 1, 0xFFC9CFD6.toInt())
            m.rect(cx + half - 1, y, 1, 1, 0xFF9BA3AD.toInt())
        }
        m.ellipse(cx, towerTop + 120, 14, 7, 0xFF6E7884.toInt())
        m.rect(cx - 13, towerTop + 119, 27, 2, 0xFFFFE7A0.toInt())
        m.ellipse(cx, towerTop + 82, 5, 3, 0xFF6E7884.toInt())
        m.rect(cx, towerTop, 1, 72, 0xFFB0B6BE.toInt())
        m.rect(cx, towerTop - 2, 1, 3, 0xFFE8484F.toInt())

        // Outer towers: glass condos
        for (side in listOf(-1, 1)) tower(m, cx + side * outer - 12, 24, 120, 0xFF3FA7B8.toInt(), rnd, 0xFFBFF3F7.toInt())
        // Inner towers: City Hall's curved towers, mirrored
        val ch = 0xFFD8DCE2.toInt()
        for (side in listOf(-1, 1)) {
            val bx = cx + side * inner - 15
            val bh = 175
            for (x in 0 until 30) {
                val k = if (side < 0) x else 29 - x
                val curve = (sqrt(1f - (k / 30f) * (k / 30f)) * 18).toInt()
                m.rect(bx + x, g - bh + (18 - curve), 1, bh - (18 - curve), if (x % 3 == 0) Pal.shade(ch, 0.82f) else ch)
            }
        }
        // Keep: Princes' Gates arch
        val gw = 100
        val gh = 130
        val stone = 0xFFEADFC6.toInt()
        val shadow = 0xFFBFAF8E.toInt()
        val left = cx - gw / 2
        m.rect(left, g - gh, gw, gh, stone)
        m.rect(left - 4, g - gh, gw + 8, 7, Pal.shade(stone, 0.9f))
        m.rect(left - 4, g - gh + 7, gw + 8, 2, shadow)
        m.arch(cx, g, 20, 92, 0xFF2D3A4A.toInt())
        m.arch(left + 14, g, 7, 50, 0xFF2D3A4A.toInt())
        m.arch(left + gw - 14, g, 7, 50, 0xFF2D3A4A.toInt())
        for (colX in listOf(left + 3, left + 25, cx - 27, cx + 24, left + gw - 28, left + gw - 6)) {
            m.rect(colX, g - gh + 10, 3, gh - 10, Pal.tint(stone, 0.35f))
            m.rect(colX + 2, g - gh + 10, 1, gh - 10, shadow)
        }
        m.rect(cx - 24, g - gh - 18, 48, 18, stone)
        m.rect(cx - 24, g - gh - 18, 48, 3, Pal.shade(stone, 0.9f))
        val leaf = 0xFFE8303A.toInt()
        val ly = g - gh - 40
        m.tri(cx, ly - 12, cx - 5, ly + 5, cx + 5, ly + 5, leaf)
        m.tri(cx - 14, ly - 2, cx - 2, ly + 9, cx - 4, ly - 2, leaf)
        m.tri(cx + 14, ly - 2, cx + 2, ly + 9, cx + 4, ly - 2, leaf)
        m.tri(cx - 10, ly + 9, cx, ly + 3, cx + 10, ly + 9, leaf)
        m.rect(cx, ly + 9, 1, 8, leaf)
        for (x in listOf(cx - 78, cx + 78)) tree(m, x, 8, if (x < cx) 0xFFE0532D.toInt() else 0xFFF08A24.toInt())
    }

    // ------------------------------------------------------------------ Sydney

    private fun sydney(m: P, ox: Int, rnd: Random) {
        val cx = ox + w / 2
        val g = ground
        val pylon = (w * 0.30f).toInt()
        // Sydney Tower as the keep's spire
        m.rect(cx - 1, g - 320, 3, 200, 0xFFB8C2CC.toInt())
        m.ellipse(cx, g - 300, 8, 10, 0xFFE7B53C.toInt())
        m.rect(cx - 8, g - 300, 17, 2, 0xFF9C7A2C.toInt())
        m.rect(cx, g - 336, 1, 16, 0xFFB8C2CC.toInt())

        // Outer towers: Opera House sails on sandstone podiums
        val shell = 0xFFF6F2E9.toInt()
        val shellShade = 0xFFCFC8BA.toInt()
        for (side in listOf(-1, 1)) {
            val bx = cx + side * (w * 0.43f).toInt()
            m.rect(bx - 22, g - 30, 44, 30, 0xFFD9C29A.toInt())
            m.rect(bx - 22, g - 30, 44, 2, 0xFFEAD8B5.toInt())
            for ((dx, sh) in listOf(-18 to 46, -6 to 70, 6 to 54)) {
                val sx = bx + side * dx
                m.tri(sx - 10, g - 30, sx + 10, g - 30, sx + side * 6, g - 30 - sh, shell)
                m.tri(sx, g - 30, sx + 10, g - 30, sx + side * 6, g - 30 - sh, shellShade)
            }
        }
        // Inner towers: granite bridge pylons
        val granite = 0xFFD1BE9D.toInt()
        for (side in listOf(-1, 1)) {
            val px = cx + side * pylon - 14
            m.rect(px, g - 170, 28, 170, granite)
            m.rect(px + 22, g - 170, 6, 170, Pal.shade(granite, 0.8f))
            m.rect(px - 2, g - 175, 32, 6, Pal.shade(granite, 0.9f))
            m.rect(px + 9, g - 160, 10, 24, Pal.shade(granite, 0.72f))
            var mx = px
            while (mx < px + 28) { m.rect(mx, g - 181, 4, 6, granite); mx += 7 }
        }
        // Keep: the Harbour Bridge arch spanning between the pylons
        val steel = 0xFF6F7F8E.toInt()
        val span = pylon - 14
        val deckY = g - 72
        val rise = 78
        for (x in -span..span) {
            val t = x.toFloat() / span
            val yTop = deckY - 18 - (rise * (1 - t * t)).toInt()
            val yBot = yTop + 10 + (abs(t) * 14).toInt()
            m.rect(cx + x, yTop, 1, 3, steel)
            m.rect(cx + x, yBot, 1, 2, steel)
            if ((x + span) % 8 == 0) m.line(cx + x, yTop, cx + x, yBot, steel)
            if ((x + span) % 8 == 4) m.line(cx + x - 4, yTop + 2, cx + x + 4, yBot, Pal.alpha(steel, 0.8f))
            if ((x + span) % 16 == 0 && abs(t) < 0.9f) m.rect(cx + x, yBot, 1, deckY - yBot, Pal.alpha(steel, 0.75f))
        }
        m.rect(cx - pylon, deckY, pylon * 2, 5, 0xFF56636F.toInt())
        m.rect(cx - pylon, deckY + 5, pylon * 2, 2, 0xFF3D4650.toInt())
        // Sandstone gateway under the deck
        m.rect(cx - span, deckY + 7, span * 2, g - deckY - 7, 0xFFD9C29A.toInt())
        m.arch(cx, g, 20, 56, 0xFF2D3A4A.toInt())
        for (x in listOf(cx - 52, cx + 52)) tree(m, x, 7, 0xFF3E9E57.toInt())
    }

    // ------------------------------------------------------------------ Istanbul

    private fun istanbul(m: P, ox: Int, rnd: Random) {
        val cx = ox + w / 2
        val g = ground
        // Red brick stripes in the Theodosian-style walls
        for (x in ox until ox + w) {
            for (y in listOf(g - WALL_H + 14, g - WALL_H + 28)) m.rect(x, y, 1, 2, Pal.alpha(0xFF9C3B2E.toInt(), 0.35f))
        }
        // Outer towers: round Galata-style towers with conical roofs
        for (side in listOf(-1, 1)) {
            val tx = cx + side * outer
            m.rect(tx - 12, g - 130, 25, 130, 0xFFB08A72.toInt())
            m.rect(tx + 6, g - 130, 7, 130, 0xFF8E6C59.toInt())
            m.rect(tx - 14, g - 133, 29, 4, 0xFF8E6C59.toInt())
            m.tri(tx - 14, g - 133, tx + 14, g - 133, tx, g - 170, 0xFF4A3B4C.toInt())
            for (i in 0..2) m.rect(tx - 7 + i * 6, g - 120, 2, 6, 0xFFFFD9A0.toInt())
        }
        // Keep: mosque with central dome over a tiled portal
        val stone = 0xFFCBB79E.toInt()
        val dome = 0xFF9EA7B3.toInt()
        m.rect(cx - 58, g - 120, 116, 120, stone)
        m.ellipse(cx, g - 150, 46, 40, dome, topHalfOnly = true)
        m.rect(cx - 46, g - 150, 93, 30, stone)
        for (i in -3..3) m.rect(cx + i * 12, g - 140, 3, 8, 0xFF5A4636.toInt())
        m.rect(cx, g - 196, 1, 8, 0xFFE7B53C.toInt())
        for (side in listOf(-1, 1)) m.ellipse(cx + side * 50, g - 120, 22, 18, dome, topHalfOnly = true)
        // Inner towers: minarets
        for (side in listOf(-1, 1)) {
            val mx = cx + side * inner
            val top = g - 250
            m.rect(mx - 4, top, 9, g - top, 0xFFE2D5C2.toInt())
            m.rect(mx + 2, top, 3, g - top, 0xFFB9A88F.toInt())
            for (b in listOf(top + 30, top + 90)) m.rect(mx - 6, b, 13, 3, 0xFFB9A88F.toInt())
            m.tri(mx - 4, top, mx + 4, top, mx, top - 26, 0xFF6F7E91.toInt())
        }
        // Tiled portal
        val pw = 72
        val ph = 104
        val pl = cx - pw / 2
        m.rect(pl, g - ph, pw, ph, 0xFFE4D3B8.toInt())
        for (y in g - ph + 4 until g step 4) for (x in pl + 4 until pl + pw - 4 step 4) {
            val inBorder = x < pl + 14 || x >= pl + pw - 14 || y < g - ph + 14
            if (inBorder) m.rect(x, y, 3, 3, if (((x + y) / 4) % 2 == 0) 0xFF2BA3A6.toInt() else 0xFF1E5F9E.toInt())
        }
        m.pointedArch(cx, g, 18, 80, 0xFF2B1B3A.toInt())
        m.pointedArch(cx, g, 15, 75, 0xFF3D2850.toInt())
        m.rect(cx - 2, g - 56, 5, 6, 0xFFFFC94D.toInt())
        for (x in pl until pl + pw step 8) m.rect(x, g - ph - 6, 5, 6, 0xFFE4D3B8.toInt())
        for ((sx, sy) in listOf(cx - 70 to g - 250, cx - 50 to g - 236, cx + 64 to g - 270)) {
            m.line(sx - 4, sy - 2, sx, sy, 0xFF3A2A40.toInt()); m.line(sx, sy, sx + 4, sy - 2, 0xFF3A2A40.toInt())
        }
    }

    // ------------------------------------------------------------------ Zurich

    private fun zurich(m: P, ox: Int, rnd: Random) {
        val cx = ox + w / 2
        val g = ground
        val facades = listOf(0xFFEADBB4.toInt(), 0xFFCFE0CF.toInt(), 0xFFF0C9B0.toInt(), 0xFFD9CFE8.toInt())
        // Outer towers: tall old-town houses with steep roofs
        for ((k, side) in listOf(-1, 1).withIndex()) {
            val bx = cx + side * outer - 13
            val col = facades[k * 2]
            m.rect(bx, g - 110, 26, 110, col)
            m.rect(bx + 22, g - 110, 4, 110, Pal.shade(col, 0.85f))
            m.tri(bx - 2, g - 110, bx + 27, g - 110, bx + 13, g - 140, 0xFF8C3B2E.toInt())
            m.windows(bx + 4, g - 102, 18, 90, 0xFF6F8FB0.toInt(), 0xFF4E6C8C.toInt(), rnd, 6, 10, 3, 5, 0.6f)
        }
        // Inner towers: St. Peter-style clock towers with green spires
        for (side in listOf(-1, 1)) {
            val ct = cx + side * inner
            m.rect(ct - 11, g - 175, 23, 175, 0xFFE3D6BC.toInt())
            m.rect(ct + 7, g - 175, 5, 175, 0xFFC4B597.toInt())
            m.tri(ct - 13, g - 175, ct + 13, g - 175, ct, g - 222, 0xFF4F8A6E.toInt())
            m.circle(ct, g - 155, 8, 0xFF2E2A28.toInt())
            m.circle(ct, g - 155, 6, 0xFF3A3532.toInt())
            for (i in 0 until 12) {
                val a = i * Math.PI / 6
                m.set(ct + (cos(a) * 6).roundToInt(), g - 155 + (sin(a) * 6).roundToInt(), 0xFFE7B53C.toInt())
            }
            m.line(ct, g - 155, ct, g - 160, 0xFFE7B53C.toInt()); m.line(ct, g - 155, ct + 3, g - 155, 0xFFE7B53C.toInt())
        }
        // Keep: Grossmünster twin towers with an arched portal
        val sand = 0xFFDCC9A2.toInt()
        val sandDark = 0xFFB9A57F.toInt()
        m.rect(cx - 40, g - 110, 80, 110, sand)
        m.tri(cx - 40, g - 110, cx + 40, g - 110, cx, g - 136, 0xFF7E4A3A.toInt())
        for (tx in listOf(cx - 28, cx + 28)) {
            m.rect(tx - 13, g - 230, 26, 230, sand)
            m.rect(tx + 8, g - 230, 5, 230, sandDark)
            for (wy in listOf(g - 210, g - 180, g - 150)) {
                m.arch(tx - 4, wy + 14, 3, 14, 0xFF4A3B33.toInt())
                m.arch(tx + 4, wy + 14, 3, 14, 0xFF4A3B33.toInt())
            }
            m.rect(tx - 15, g - 234, 30, 5, sandDark)
            m.rect(tx - 10, g - 252, 20, 18, sand)
            m.ellipse(tx, g - 252, 10, 13, 0xFF3B4250.toInt(), topHalfOnly = true)
            m.rect(tx, g - 276, 1, 12, 0xFF3B4250.toInt())
        }
        m.arch(cx, g, 16, 70, 0xFF3A2E28.toInt())
        m.arch(cx, g, 12, 64, 0xFF4E3E34.toInt())
        m.circle(cx, g - 90, 8, 0xFF6F8FB0.toInt())
        m.circle(cx, g - 90, 6, 0xFF9CB8D4.toInt())
        // Swiss flags on the outer towers
        for (side in listOf(-1, 1)) {
            val fx = cx + side * outer
            m.rect(fx, g - 165, 1, 26, 0xFF8E8E8E.toInt())
            m.rect(fx + 1, g - 165, 11, 11, 0xFFE03A3E.toInt())
            m.rect(fx + 5, g - 163, 3, 7, 0xFFFFFFFF.toInt()); m.rect(fx + 3, g - 161, 7, 3, 0xFFFFFFFF.toInt())
        }
    }

    // ------------------------------------------------------------------ San Francisco

    private fun sanFrancisco(m: P, ox: Int, rnd: Random) {
        val cx = ox + w / 2
        val g = ground
        val red = 0xFFC8372D.toInt()
        val redDark = 0xFF8F2620.toInt()
        val top = g - 300
        val legs = listOf(cx - 24, cx + 12)
        // Cables sweeping from the keep down to the outer towers
        for (side in listOf(-1, 1)) {
            val tx = if (side < 0) legs[0] + 6 else legs[1] + 6
            val endX = cx + side * outer
            val endY = g - 130
            var prevX = tx; var prevY = top + 6
            val n = abs(endX - tx)
            for (i in 0..n) {
                val x = tx + side * i
                val t = i / n.toFloat()
                val y = (top + 6 + (endY - top - 6) * (1 - (1 - t) * (1 - t))).toInt()
                m.line(prevX, prevY, x, y, red, 2)
                if (i % 7 == 0 && i > 4) m.rect(x, y, 1, g - WALL_H - y, Pal.alpha(red, 0.6f))
                prevX = x; prevY = y
            }
        }
        // Outer towers: Painted Lady turrets
        val colors = listOf(0xFFF7C6D9.toInt(), 0xFFB9E3D3.toInt())
        for ((k, side) in listOf(-1, 1).withIndex()) {
            val bx = cx + side * outer - 14
            val col = colors[k]
            m.rect(bx, g - 120, 28, 120, col)
            m.rect(bx + 24, g - 120, 4, 120, Pal.shade(col, 0.85f))
            m.tri(bx - 2, g - 120, bx + 29, g - 120, bx + 14, g - 150, Pal.shade(col, 0.75f))
            m.rect(bx + 4, g - 112, 20, 2, 0xFFFFFFFF.toInt())
            m.windows(bx + 5, g - 104, 18, 90, 0xFFFFE6B0.toInt(), 0xFF5A4A5A.toInt(), rnd, 7, 12, 4, 7, 0.7f)
        }
        // Inner towers: fluted Coit-style towers
        for (side in listOf(-1, 1)) {
            val tx = cx + side * inner
            m.rect(tx - 10, g - 165, 21, 165, 0xFFE8E2D6.toInt())
            for (fx in tx - 8..tx + 8 step 4) m.rect(fx, g - 160, 1, 150, 0xFFC9C0AE.toInt())
            m.rect(tx - 12, g - 170, 25, 6, 0xFFD4CCBC.toInt())
            var mx = tx - 12
            while (mx < tx + 13) { m.rect(mx, g - 176, 3, 6, 0xFFD4CCBC.toInt()); mx += 6 }
        }
        // Keep: Golden Gate tower
        for (lx in legs) {
            m.rect(lx, top, 12, g - top, red)
            m.rect(lx + 9, top, 3, g - top, redDark)
            for (step in listOf(top + 40, top + 100, top + 160)) m.rect(lx - 1, step, 14, 3, redDark)
        }
        for ((py, ph) in listOf(top to 12, top + 50 to 14, top + 110 to 14, top + 170 to 14)) {
            m.rect(legs[0], py, legs[1] - legs[0] + 12, ph, red)
            for (k in 0 until 2) m.rect(legs[0] + 14 + k * 8, py + 3, 4, ph - 6, redDark)
        }
        // Bridge deck forms the gate's lintel
        m.rect(cx - inner + 10, g - 70, (inner - 10) * 2, 6, red)
        m.rect(cx - inner + 10, g - 64, (inner - 10) * 2, 3, redDark)
        // Cable car parked in the gateway
        val cc = cx - 26
        val cy = g - 24
        m.rect(cc, cy, 52, 22, 0xFFC0392B.toInt())
        m.rect(cc, cy, 52, 3, 0xFFF4E2A0.toInt())
        for (i in 0 until 4) m.rect(cc + 4 + i * 12, cy + 6, 8, 8, 0xFFFFE6B0.toInt())
        m.rect(cc + 2, cy + 16, 48, 3, 0xFFF4E2A0.toInt())
        m.line(cc + 26, cy, cc + 26, cy - 10, 0xFF22252A.toInt())
    }

    companion object {
        /** Height of the shared castle wall that runs through every city. */
        const val WALL_H = 46
    }
}

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
