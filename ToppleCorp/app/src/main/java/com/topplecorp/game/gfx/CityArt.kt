package com.topplecorp.game.gfx

import android.graphics.Bitmap
import android.graphics.Canvas
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Full-screen pixel-art cityscape for the city select screen. Each city has a central landmark "gate"
 * surrounded by buildings, painted procedurally in three parallax layers (sky, far, main).
 */
class CityArt(val city: Int, val w: Int, val h: Int) {
    /** Screen y of the street line the main landmark stands on. */
    val ground = h - 150

    private val sky: Bitmap
    private val far: Bitmap
    private val main: Bitmap
    private val farMargin = w / 4
    private val clouds: List<FloatArray>
    private val cloudColor: Int
    private val fog: Boolean = city == 4

    init {
        val s = P(w, h)
        val f = P(w + farMargin * 2, h)
        val m = P(w, h)
        val rnd = Random(1000 + city)
        when (city) {
            0 -> toronto(s, f, m, rnd)
            1 -> sydney(s, f, m, rnd)
            2 -> istanbul(s, f, m, rnd)
            3 -> zurich(s, f, m, rnd)
            else -> sanFrancisco(s, f, m, rnd)
        }
        sky = s.bitmap(); far = f.bitmap(); main = m.bitmap()
        cloudColor = when (city) { 2 -> 0xCCFFD9B8.toInt(); 4 -> 0xB3FFFFFF.toInt(); else -> 0xE6FFFFFF.toInt() }
        clouds = List(4) { i -> floatArrayOf(rnd.nextFloat() * w, 50f + i * 34f + rnd.nextFloat() * 20f, 0.4f + rnd.nextFloat() * 0.6f) }
    }

    /**
     * Draws the scene into the slot starting at [x]. [shift] is how far (in screens, -1..1) the slot is
     * from the centre; layers move by different amounts for parallax.
     */
    fun draw(c: Canvas, x: Float, shift: Float, time: Float) {
        Draw.bitmap(c, sky, x, 0f)
        for (cl in clouds) {
            val cw = 20f + cl[2] * 26f
            var cx = (cl[0] + time * 5f * cl[2]) % (w + 60f) - 30f
            cx += -shift * w * 0.35f
            Draw.rect(c, x + cx, cl[1], cw, 5f, cloudColor)
            Draw.rect(c, x + cx + cw * 0.2f, cl[1] - 3, cw * 0.45f, 3f, cloudColor)
        }
        Draw.bitmap(c, far, x - farMargin - shift * w * 0.22f, 0f)
        Draw.bitmap(c, main, x, 0f)
        if (fog) {
            for (i in 0 until 3) {
                val fy = ground - 70f + i * 26f
                val fx = (time * (6f + i * 3f)) % 80f
                var bx = -80f + fx
                while (bx < w) {
                    Draw.rect(c, x + bx, fy, 52f, 6f, 0x40FFFFFF)
                    Draw.rect(c, x + bx + 10, fy - 3, 30f, 3f, 0x33FFFFFF)
                    bx += 80f
                }
            }
        }
    }

    // ------------------------------------------------------------------ painter

    /** Tiny software painter on an ARGB buffer with alpha blending. */
    private class P(val w: Int, val h: Int) {
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

        fun vgrad(x: Int, y: Int, rw: Int, rh: Int, top: Int, bottom: Int) {
            for (i in 0 until rh) rect(x, y + i, rw, 1, Pal.mix(top, bottom, i / max(1f, rh - 1f)))
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
                3 -> tri(x, top, x + bw - 1, top, x + bw / 2, top - bw / 2, Pal.shade(body, 0.85f))
            }
        }

        fun tree(cx: Int, ground: Int, r: Int, leaf: Int) {
            rect(cx - 1, ground - r - 4, 3, r + 4, 0xFF5A3A28.toInt())
            circle(cx, ground - r * 2, r, leaf)
            circle(cx - r / 2, ground - r * 2 - r / 3, r / 2, Pal.tint(leaf, 0.2f))
        }

        fun bitmap(): Bitmap = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun sun(s: P, x: Int, y: Int, r: Int, color: Int) {
        s.circle(x, y, r + 4, Pal.alpha(color, 0.25f))
        s.circle(x, y, r, color)
    }

    /** Ground plaza from [y] to the bottom of the screen with paving lines. */
    private fun plaza(m: P, y: Int, base: Int, line: Int) {
        m.vgrad(0, y, w, h - y, base, Pal.shade(base, 0.7f))
        var yy = y + 6
        var step = 6
        while (yy < h) {
            m.rect(0, yy, w, 1, Pal.alpha(line, 0.5f))
            step += 2; yy += step
        }
        val cx = w / 2
        for (i in -6..6) m.line(cx + i * 10, y, cx + i * 40, h, Pal.alpha(line, 0.35f))
    }

    private fun skyline(f: P, ground: Int, color: Int, window: Int, rnd: Random, minH: Int, maxH: Int) {
        var x = 0
        while (x < f.w) {
            val bw = 12 + rnd.nextInt(16)
            val bh = minH + rnd.nextInt(maxH - minH)
            f.building(x, ground, bw, bh, Pal.shade(color, 0.9f + rnd.nextFloat() * 0.15f), window, rnd, rnd.nextInt(3))
            x += bw + rnd.nextInt(3)
        }
    }

    // ------------------------------------------------------------------ Toronto

    private fun toronto(s: P, f: P, m: P, rnd: Random) {
        val g = ground
        val cx = w / 2
        s.vgrad(0, 0, w, h, 0xFF5BB8F0.toInt(), 0xFFD4F0FF.toInt())
        sun(s, (w * 0.82f).toInt(), 70, 11, 0xFFFFF4C2.toInt())

        // Far: skyline and the CN Tower
        val fx = farMargin
        skyline(f, g - 30, 0xFF9DB8D2.toInt(), 0xFFDCEBF7.toInt(), rnd, 50, 150)
        val tx = fx + (w * 0.74f).toInt()
        val towerTop = 40
        for (y in towerTop + 70 until g - 30) {
            val t = (y - towerTop - 70f) / (g - 30 - towerTop - 70f)
            val half = (2 + t * 5).roundToInt()
            f.rect(tx - half, y, half * 2 + 1, 1, 0xFFC9CFD6.toInt())
            f.rect(tx + half - 1, y, 1, 1, 0xFF9BA3AD.toInt())
        }
        f.ellipse(tx, towerTop + 120, 13, 7, 0xFF6E7884.toInt())
        f.rect(tx - 12, towerTop + 119, 25, 2, 0xFFFFE7A0.toInt())
        f.ellipse(tx, towerTop + 82, 5, 3, 0xFF6E7884.toInt())
        f.rect(tx, towerTop, 1, 72, 0xFFB0B6BE.toInt())
        f.rect(tx, towerTop - 2, 1, 3, 0xFFE8484F.toInt())

        // Lake band behind the plaza
        m.rect(0, g - 30, w, 14, 0xFF3F86C4.toInt())
        m.rect(0, g - 30, w, 1, 0xFF8CC4EE.toInt())

        // City Hall: two curved towers on the left
        val ch = 0xFFD8DCE2.toInt()
        for ((bx, bh, bw) in listOf(Triple(4, 170, 22), Triple(30, 135, 20))) {
            for (x in 0 until bw) {
                val curve = (sqrt(1f - (x.toFloat() / bw).let { it * it }) * 16).toInt()
                m.rect(bx + x, g - bh + (16 - curve), 1, bh - (16 - curve), if (x % 3 == 0) Pal.shade(ch, 0.82f) else ch)
            }
        }
        // Glass condos on the right
        m.building(w - 46, g, 26, 205, 0xFF3FA7B8.toInt(), 0xFFBFF3F7.toInt(), rnd, 1)
        m.building(w - 22, g, 22, 160, 0xFF2E8597.toInt(), 0xFFA8E6EE.toInt(), rnd, 2)
        m.building(54, g, 18, 95, 0xFFB5553C.toInt(), 0xFFFFE3A0.toInt(), rnd, 2)

        // Princes' Gates style triumphal arch
        val gw = min(170, (w * 0.62f).toInt())
        val gh = 150
        val stone = 0xFFEADFC6.toInt()
        val shadow = 0xFFBFAF8E.toInt()
        val left = cx - gw / 2
        m.rect(left, g - gh, gw, gh, stone)
        m.rect(left - 4, g - gh, gw + 8, 8, Pal.shade(stone, 0.9f))      // cornice
        m.rect(left - 4, g - gh + 8, gw + 8, 2, shadow)
        m.rect(left, g - 70, gw, 3, shadow)                              // impost line
        // Openings
        m.arch(cx, g, 26, 105, 0xFF2D3A4A.toInt())
        m.arch(left + 24, g, 11, 62, 0xFF2D3A4A.toInt())
        m.arch(left + gw - 24, g, 11, 62, 0xFF2D3A4A.toInt())
        // Columns
        for (colX in listOf(left + 4, left + 42, cx - 34, cx + 30, left + gw - 46, left + gw - 8)) {
            m.rect(colX, g - gh + 12, 4, gh - 12, Pal.tint(stone, 0.35f))
            m.rect(colX + 3, g - gh + 12, 1, gh - 12, shadow)
        }
        // Attic and maple leaf crest
        m.rect(cx - 30, g - gh - 22, 60, 22, stone)
        m.rect(cx - 30, g - gh - 22, 60, 3, Pal.shade(stone, 0.9f))
        val leaf = 0xFFE8303A.toInt()
        val ly = g - gh - 48
        m.tri(cx, ly - 14, cx - 6, ly + 6, cx + 6, ly + 6, leaf)
        m.tri(cx - 16, ly - 2, cx - 2, ly + 10, cx - 4, ly - 2, leaf)
        m.tri(cx + 16, ly - 2, cx + 2, ly + 10, cx + 4, ly - 2, leaf)
        m.tri(cx - 11, ly + 10, cx, ly + 4, cx + 11, ly + 10, leaf)
        m.rect(cx, ly + 10, 1, 8, leaf)

        plaza(m, g, 0xFFC9C2B5.toInt(), 0xFF8E867A.toInt())
        for (x in listOf(14, 44, w - 44, w - 14)) m.tree(x, g + 6, 9, if (x < cx) 0xFFE0532D.toInt() else 0xFFF08A24.toInt())
    }

    // ------------------------------------------------------------------ Sydney

    private fun sydney(s: P, f: P, m: P, rnd: Random) {
        val g = ground
        val cx = w / 2
        s.vgrad(0, 0, w, h, 0xFF3FA9F0.toInt(), 0xFFC5ECFF.toInt())
        sun(s, (w * 0.18f).toInt(), 64, 12, 0xFFFFF6C8.toInt())

        // Far: CBD and Sydney Tower
        skyline(f, g - 70, 0xFF8FB0D2.toInt(), 0xFFE0F0FF.toInt(), rnd, 30, 120)
        val tx = farMargin + (w * 0.8f).toInt()
        f.rect(tx - 1, 50, 3, g - 70 - 50, 0xFFB8C2CC.toInt())
        f.ellipse(tx, 58, 7, 9, 0xFFE7B53C.toInt())
        f.rect(tx - 7, 58, 15, 2, 0xFF9C7A2C.toInt())
        f.rect(tx, 36, 1, 14, 0xFFB8C2CC.toInt())

        // Harbour water
        m.vgrad(0, g - 70, w, h - g + 70, 0xFF2E8BD0.toInt(), 0xFF1C5F96.toInt())
        for (i in 0 until 60) m.rect(rnd.nextInt(w), g - 66 + rnd.nextInt(h - g + 60), 4 + rnd.nextInt(6), 1, 0x55FFFFFF)

        // Harbour Bridge: arch as the gate
        val steel = 0xFF6F7F8E.toInt()
        val span = (w * 0.42f).toInt()
        val deckY = g - 78
        val rise = 120
        for (x in -span..span) {
            val t = x.toFloat() / span
            val yTop = deckY - 20 - (rise * (1 - t * t)).toInt()
            val yBot = yTop + 10 + (abs(t) * 18).toInt()
            m.rect(cx + x, yTop, 1, 3, steel)
            m.rect(cx + x, yBot, 1, 2, steel)
            if ((x + span) % 9 == 0) m.line(cx + x, yTop, cx + x, yBot, steel)
            if ((x + span) % 18 == 0 && abs(t) < 0.92f) m.rect(cx + x, yBot, 1, deckY - yBot, Pal.alpha(steel, 0.75f))
            if ((x + span) % 9 == 4) m.line(cx + x - 4, yTop + 2, cx + x + 4, yBot, Pal.alpha(steel, 0.8f))
        }
        m.rect(0, deckY, w, 5, 0xFF56636F.toInt())
        m.rect(0, deckY + 5, w, 2, 0xFF3D4650.toInt())
        // Granite pylons
        val granite = 0xFFD1BE9D.toInt()
        for (px in listOf(cx - span - 16, cx + span - 12)) {
            m.rect(px, deckY - 70, 28, g - deckY + 70 - 50, granite)
            m.rect(px + 22, deckY - 70, 6, g - deckY + 20, Pal.shade(granite, 0.8f))
            m.rect(px - 2, deckY - 74, 32, 5, Pal.shade(granite, 0.9f))
            m.rect(px + 8, deckY - 60, 10, 20, Pal.shade(granite, 0.75f))
        }

        // Opera House on its platform
        val pf = g - 20
        m.rect(0, pf, (w * 0.55f).toInt(), 22, 0xFFD9C29A.toInt())
        m.rect(0, pf, (w * 0.55f).toInt(), 2, 0xFFEAD8B5.toInt())
        val shell = 0xFFF6F2E9.toInt()
        val shellShade = 0xFFCFC8BA.toInt()
        for ((sx, sh, sw) in listOf(Triple(14, 56, 30), Triple(40, 72, 34), Triple(70, 60, 30), Triple(96, 42, 24))) {
            m.tri(sx, pf, sx + sw, pf, sx + sw - 4, pf - sh, shell)
            m.tri(sx + sw / 2, pf, sx + sw, pf, sx + sw - 4, pf - sh, shellShade)
            for (k in 1..3) m.line(sx + k * sw / 4, pf, sx + sw - 4, pf - sh, Pal.alpha(0xFF9E9686.toInt(), 0.6f))
        }
        // Ferry
        val fy = g + 30
        val fxp = (w * 0.68f).toInt()
        m.rect(fxp, fy, 46, 7, 0xFF2E8B57.toInt())
        m.rect(fxp + 6, fy - 6, 30, 6, 0xFFF4E2A0.toInt())
        m.rect(fxp + 8, fy - 4, 26, 2, 0xFF2E8B57.toInt())
        m.rect(fxp + 18, fy - 11, 6, 5, 0xFF2E8B57.toInt())
        m.rect(fxp - 6, fy + 7, 60, 1, 0x88FFFFFF.toInt())
        // Promenade
        m.vgrad(0, g + 60, w, h - g - 60, 0xFFE3C58F.toInt(), 0xFFB89A66.toInt())
        m.rect(0, g + 60, w, 2, 0xFFF2DDB0.toInt())
        for (x in listOf(20, w - 20)) m.tree(x, g + 66, 7, 0xFF3E9E57.toInt())
    }

    // ------------------------------------------------------------------ Istanbul

    private fun istanbul(s: P, f: P, m: P, rnd: Random) {
        val g = ground
        val cx = w / 2
        s.vgrad(0, 0, w, h, 0xFF5B3F8C.toInt(), 0xFFF7A55A.toInt())
        sun(s, (w * 0.22f).toInt(), g - 120, 18, 0xFFFFD27A.toInt())

        // Far: hills, houses and the Galata Tower
        for (x in 0 until f.w) {
            val hy = g - 60 - (sin(x / 30f) * 10 + sin(x / 11f) * 4).toInt()
            f.rect(x, hy, 1, g - hy, 0xFF7A4F7E.toInt())
        }
        var hx = 0
        while (hx < f.w) {
            val hw = 8 + rnd.nextInt(8)
            val hh = 10 + rnd.nextInt(14)
            val hy = g - 62 - hh + rnd.nextInt(10)
            f.rect(hx, hy, hw, hh, Pal.shade(0xFF9C6E8F.toInt(), 0.9f + rnd.nextFloat() * 0.2f))
            f.windows(hx + 2, hy + 3, hw - 3, hh - 4, 0xFFFFD9A0.toInt(), 0xFF7A4F7E.toInt(), rnd, 4, 5, 1, 2)
            hx += hw + 1
        }
        val gx = farMargin + (w * 0.86f).toInt()
        f.rect(gx - 8, g - 150, 17, 90, 0xFFB08A72.toInt())
        f.rect(gx + 4, g - 150, 5, 90, 0xFF8E6C59.toInt())
        f.rect(gx - 10, g - 152, 21, 4, 0xFF8E6C59.toInt())
        f.tri(gx - 10, g - 152, gx + 10, g - 152, gx, g - 182, 0xFF4A3B4C.toInt())
        for (i in 0..2) f.rect(gx - 5 + i * 5, g - 140, 2, 5, 0xFFFFD9A0.toInt())

        // Mosque: big dome, semi-domes and minarets
        val stone = 0xFFCBB79E.toInt()
        val dome = 0xFF9EA7B3.toInt()
        m.rect(cx - 70, g - 120, 140, 120, stone)
        m.ellipse(cx, g - 150, 52, 44, dome, topHalfOnly = true)
        m.rect(cx - 52, g - 150, 105, 30, stone)
        for (i in -4..4) m.rect(cx + i * 11, g - 140, 3, 8, 0xFF5A4636.toInt())
        m.ellipse(cx, g - 150, 52, 44, Pal.alpha(0xFFFFFFFF.toInt(), 0.12f), topHalfOnly = true)
        m.rect(cx, g - 200, 1, 8, 0xFFE7B53C.toInt())
        for (side in listOf(-1, 1)) {
            m.ellipse(cx + side * 62, g - 120, 26, 22, dome, topHalfOnly = true)
            m.ellipse(cx + side * 96, g - 104, 14, 12, dome, topHalfOnly = true)
            m.rect(cx + side * 96 - 14, g - 104, 29, 104, stone)
        }
        for (mx in listOf(cx - 118, cx - 84, cx + 84, cx + 118)) {
            val top = if (abs(mx - cx) > 100) g - 230 else g - 250
            m.rect(mx - 3, top, 7, g - top, 0xFFE2D5C2.toInt())
            m.rect(mx + 2, top, 2, g - top, 0xFFB9A88F.toInt())
            for (b in listOf(top + 30, top + 80)) m.rect(mx - 5, b, 11, 3, 0xFFB9A88F.toInt())
            m.tri(mx - 3, top, mx + 3, top, mx, top - 22, 0xFF6F7E91.toInt())
        }

        // Ottoman gate portal with Iznik tiles
        val pw = 96
        val ph = 132
        val pl = cx - pw / 2
        m.rect(pl, g - ph, pw, ph, 0xFFE4D3B8.toInt())
        for (y in g - ph + 4 until g step 4) for (x in pl + 4 until pl + pw - 4 step 4) {
            val inBorder = x < pl + 16 || x >= pl + pw - 16 || y < g - ph + 16
            if (inBorder) m.rect(x, y, 3, 3, if (((x + y) / 4) % 2 == 0) 0xFF2BA3A6.toInt() else 0xFF1E5F9E.toInt())
        }
        m.pointedArch(cx, g, 24, 100, 0xFF2B1B3A.toInt())
        m.pointedArch(cx, g, 20, 94, 0xFF3D2850.toInt())
        m.rect(cx - 2, g - 70, 5, 7, 0xFFFFC94D.toInt())
        m.rect(cx, g - 82, 1, 12, 0xFF8E6C59.toInt())
        for (x in pl until pl + pw step 8) m.rect(x, g - ph - 6, 5, 6, 0xFFE4D3B8.toInt())

        // Houses with red roofs on both sides
        for ((bx, bw, bh, col) in listOf(
            listOf(0, 34, 70, 0xFFE8A87C.toInt()), listOf(w - 34, 34, 64, 0xFFF2D08A.toInt()),
        )) {
            m.rect(bx, g - bh, bw, bh, col)
            m.tri(bx - 2, g - bh, bx + bw + 1, g - bh, bx + bw / 2, g - bh - 14, 0xFFC0392B.toInt())
            m.windows(bx + 4, g - bh + 8, bw - 8, bh - 14, 0xFFFFE3A0.toInt(), Pal.shade(col, 0.7f), rnd, 7, 10, 3, 5, 0.6f)
        }
        // Seagulls
        for ((sx, sy) in listOf(60 to 90, 80 to 104, w - 70 to 76)) {
            m.line(sx - 4, sy - 2, sx, sy, 0xFF3A2A40.toInt()); m.line(sx, sy, sx + 4, sy - 2, 0xFF3A2A40.toInt())
        }
        // Cobblestones
        m.vgrad(0, g, w, h - g, 0xFFB08D6E.toInt(), 0xFF7A5E48.toInt())
        for (y in g + 3 until h step 5) for (x in ((y / 5) % 2) * 4 until w step 8) m.rect(x, y, 6, 3, 0x22FFFFFF)
    }

    // ------------------------------------------------------------------ Zurich

    private fun zurich(s: P, f: P, m: P, rnd: Random) {
        val g = ground
        val cx = w / 2
        s.vgrad(0, 0, w, h, 0xFF7DBBE6.toInt(), 0xFFEAF6FF.toInt())
        sun(s, (w * 0.78f).toInt(), 60, 10, 0xFFFFFBE0.toInt())

        // Far: the Alps and the lake
        val peaks = listOf(0.05f to 120, 0.22f to 160, 0.4f to 135, 0.58f to 175, 0.78f to 140, 0.95f to 165)
        for ((px, ph) in peaks) {
            val x = (px * f.w).toInt()
            val top = g - 70 - ph
            f.tri(x - 70, g - 70, x + 70, g - 70, x, top, 0xFF8A9BB8.toInt())
            f.tri(x, g - 70, x + 70, g - 70, x, top, 0xFF6F809E.toInt())
            f.tri(x - 20, top + 28, x + 20, top + 28, x, top, 0xFFF4F8FF.toInt())
            f.tri(x, top + 28, x + 20, top + 28, x, top, 0xFFD5DEEC.toInt())
        }
        f.rect(0, g - 72, f.w, 30, 0xFF5D9CCB.toInt())
        for (i in 0 until 40) f.rect(rnd.nextInt(f.w), g - 70 + rnd.nextInt(28), 5, 1, 0x66FFFFFF)

        // Old town houses
        val facades = listOf(0xFFEADBB4.toInt(), 0xFFCFE0CF.toInt(), 0xFFF0C9B0.toInt(), 0xFFD9CFE8.toInt(), 0xFFF3E6C4.toInt())
        val roofs = listOf(0xFF8C3B2E.toInt(), 0xFF5E3A2E.toInt(), 0xFF3E4C5E.toInt())
        var hx = 0
        while (hx < w) {
            val bw = 18 + rnd.nextInt(10)
            val bh = 60 + rnd.nextInt(40)
            if (abs(hx + bw / 2 - cx) > 62) {
                val col = facades[rnd.nextInt(facades.size)]
                m.rect(hx, g - bh, bw, bh, col)
                m.tri(hx - 1, g - bh, hx + bw, g - bh, hx + bw / 2, g - bh - bw * 3 / 4, roofs[rnd.nextInt(roofs.size)])
                m.windows(hx + 3, g - bh + 6, bw - 6, bh - 12, 0xFF6F8FB0.toInt(), 0xFF4E6C8C.toInt(), rnd, 6, 9, 3, 5, 0.5f)
            }
            hx += bw
        }

        // St. Peter's clock tower (left)
        val ct = (w * 0.16f).toInt()
        m.rect(ct - 10, g - 170, 21, 170, 0xFFE3D6BC.toInt())
        m.rect(ct + 6, g - 170, 5, 170, 0xFFC4B597.toInt())
        m.tri(ct - 12, g - 170, ct + 12, g - 170, ct, g - 214, 0xFF4F8A6E.toInt())
        m.circle(ct, g - 150, 8, 0xFF2E2A28.toInt())
        m.circle(ct, g - 150, 6, 0xFF3A3532.toInt())
        for (i in 0 until 12) {
            val a = i * Math.PI / 6
            m.set(ct + (kotlin.math.cos(a) * 6).roundToInt(), g - 150 + (kotlin.math.sin(a) * 6).roundToInt(), 0xFFE7B53C.toInt())
        }
        m.line(ct, g - 150, ct, g - 155, 0xFFE7B53C.toInt()); m.line(ct, g - 150, ct + 3, g - 150, 0xFFE7B53C.toInt())

        // Grossmünster: twin towers and an arched portal
        val sand = 0xFFDCC9A2.toInt()
        val sandDark = 0xFFB9A57F.toInt()
        m.rect(cx - 52, g - 110, 104, 110, sand)
        m.tri(cx - 52, g - 110, cx + 52, g - 110, cx, g - 140, 0xFF7E4A3A.toInt())
        for (tx in listOf(cx - 40, cx + 40)) {
            m.rect(tx - 15, g - 220, 30, 220, sand)
            m.rect(tx + 10, g - 220, 5, 220, sandDark)
            for (wy in listOf(g - 200, g - 170, g - 140)) {
                m.arch(tx - 4, wy + 14, 3, 14, 0xFF4A3B33.toInt())
                m.arch(tx + 5, wy + 14, 3, 14, 0xFF4A3B33.toInt())
            }
            m.rect(tx - 17, g - 224, 34, 5, sandDark)
            m.rect(tx - 12, g - 244, 24, 20, sand)
            m.ellipse(tx, g - 244, 12, 14, 0xFF3B4250.toInt(), topHalfOnly = true)
            m.rect(tx, g - 268, 1, 12, 0xFF3B4250.toInt())
        }
        m.arch(cx, g, 20, 76, 0xFF3A2E28.toInt())
        m.arch(cx, g, 16, 70, 0xFF4E3E34.toInt())
        m.circle(cx, g - 94, 9, 0xFF6F8FB0.toInt())
        m.circle(cx, g - 94, 7, 0xFF9CB8D4.toInt())
        // Swiss flag
        val fx = w - 26
        m.rect(fx, g - 150, 1, 60, 0xFF8E8E8E.toInt())
        m.rect(fx + 1, g - 150, 14, 14, 0xFFE03A3E.toInt())
        m.rect(fx + 6, g - 147, 4, 8, 0xFFFFFFFF.toInt()); m.rect(fx + 4, g - 145, 8, 4, 0xFFFFFFFF.toInt())

        // Cobbled square and a tram
        m.vgrad(0, g, w, h - g, 0xFFA9A49A.toInt(), 0xFF6E6A62.toInt())
        for (y in g + 3 until h step 5) for (x in ((y / 5) % 2) * 4 until w step 8) m.rect(x, y, 6, 3, 0x1FFFFFFF)
        val tr = g + 26
        m.rect(20, tr - 18, 90, 18, 0xFF2F6DB5.toInt())
        m.rect(20, tr - 18, 90, 3, 0xFFF4F4F4.toInt())
        for (i in 0 until 7) m.rect(26 + i * 12, tr - 13, 8, 7, 0xFFBFE3FF.toInt())
        m.rect(20, tr, 90, 2, 0xFF22252A.toInt())
        m.line(64, tr - 18, 76, tr - 30, 0xFF22252A.toInt())
        m.rect(0, tr + 2, w, 1, 0xFF8A8A8A.toInt())
    }

    // ------------------------------------------------------------------ San Francisco

    private fun sanFrancisco(s: P, f: P, m: P, rnd: Random) {
        val g = ground
        val cx = w / 2
        s.vgrad(0, 0, w, h, 0xFFFF8E6B.toInt(), 0xFFFFDDB0.toInt())
        sun(s, (w * 0.75f).toInt(), g - 160, 16, 0xFFFFF0C0.toInt())

        // Far: hills, skyline and Transamerica Pyramid
        for (x in 0 until f.w) {
            val hy = g - 70 - (sin(x / 40f) * 14 + 10).toInt()
            f.rect(x, hy, 1, g - hy, 0xFF8C6E8E.toInt())
        }
        skyline(f, g - 64, 0xFFA88AA0.toInt(), 0xFFFFE2B8.toInt(), rnd, 20, 70)
        val px = farMargin + (w * 0.2f).toInt()
        f.tri(px - 14, g - 64, px + 14, g - 64, px, g - 220, 0xFFEDE6DC.toInt())
        f.tri(px, g - 64, px + 14, g - 64, px, g - 220, 0xFFCFC4B6.toInt())
        f.rect(px - 9, g - 150, 4, 10, 0xFFEDE6DC.toInt()); f.rect(px + 6, g - 150, 4, 10, 0xFFCFC4B6.toInt())

        // Bay
        m.vgrad(0, g - 60, w, h - g + 60, 0xFF3A6E9C.toInt(), 0xFF213F63.toInt())
        for (i in 0 until 50) m.rect(rnd.nextInt(w), g - 56 + rnd.nextInt(h - g + 50), 4 + rnd.nextInt(5), 1, 0x44FFFFFF)

        // Golden Gate tower and cables
        val red = 0xFFC8372D.toInt()
        val redDark = 0xFF8F2620.toInt()
        val deckY = g - 96
        val top = 52
        val legs = listOf(cx - 26, cx + 14)
        // Main cables sweeping to both sides
        for (side in listOf(-1, 1)) {
            val tx = if (side < 0) legs[0] + 6 else legs[1] + 6
            var prevY = top + 6
            for (i in 0..(w / 2 + 20)) {
                val x = tx + side * i
                val t = i / (w / 2f + 20f)
                val y = (top + 6 + (deckY - 8 - top - 6) * (1 - (1 - t) * (1 - t))).toInt()
                m.line(x - side, prevY, x, y, red, 2)
                if (i % 7 == 0) m.rect(x, y, 1, deckY - y, Pal.alpha(red, 0.8f))
                prevY = y
            }
        }
        // Tower legs and portals
        for (lx in legs) {
            m.rect(lx, top, 12, g - top - 40, red)
            m.rect(lx + 9, top, 3, g - top - 40, redDark)
            for (step in listOf(top + 40, top + 100)) m.rect(lx - 1, step, 14, 3, redDark)
        }
        for ((py, ph) in listOf(top to 12, top + 44 to 14, top + 104 to 14)) {
            m.rect(legs[0], py, legs[1] - legs[0] + 12, ph, red)
            for (k in 0 until 3) m.rect(legs[0] + 16 + k * 8, py + 3, 4, ph - 6, redDark)
        }
        // Deck
        m.rect(0, deckY, w, 7, red)
        m.rect(0, deckY + 7, w, 4, redDark)
        for (x in 0 until w step 6) m.line(x, deckY + 7, x + 3, deckY + 11, red)
        // Tower base piers
        for (lx in legs) m.rect(lx - 3, g - 42, 18, 12, 0xFF9A9A9A.toInt())

        // Painted Ladies on both edges
        val colors = listOf(0xFFF7C6D9.toInt(), 0xFFB9E3D3.toInt(), 0xFFF9E3A3.toInt(), 0xFFC9C3F0.toInt())
        for ((i, bx) in listOf(0, 28, w - 56, w - 28).withIndex()) {
            val bh = 64
            val col = colors[i]
            m.rect(bx, g - bh + 20, 28, bh + 20, col)
            m.tri(bx - 1, g - bh + 20, bx + 28, g - bh + 20, bx + 14, g - bh, Pal.shade(col, 0.8f))
            m.rect(bx + 4, g - bh + 26, 20, 2, 0xFFFFFFFF.toInt())
            m.windows(bx + 5, g - bh + 32, 18, 40, 0xFFFFE6B0.toInt(), 0xFF5A4A5A.toInt(), rnd, 7, 12, 4, 7, 0.7f)
            m.rect(bx + 11, g + 22, 6, 18, 0xFF5A4A5A.toInt())
        }
        // Street with a cable car
        m.vgrad(0, g + 40, w, h - g - 40, 0xFF6E6A70.toInt(), 0xFF4A464C.toInt())
        m.rect(0, g + 52, w, 1, 0xFFB0B0B0.toInt()); m.rect(0, g + 58, w, 1, 0xFFB0B0B0.toInt())
        // Cable car on the left so it stays clear of the Play button
        val cc = 6
        val cy = g + 44
        m.rect(cc, cy, 60, 26, 0xFFC0392B.toInt())
        m.rect(cc, cy, 60, 3, 0xFFF4E2A0.toInt())
        for (i in 0 until 5) m.rect(cc + 4 + i * 11, cy + 6, 8, 9, 0xFFFFE6B0.toInt())
        m.rect(cc + 2, cy + 18, 56, 4, 0xFFF4E2A0.toInt())
        m.rect(cc + 10, cy + 26, 6, 3, 0xFF22252A.toInt()); m.rect(cc + 44, cy + 26, 6, 3, 0xFF22252A.toInt())
        m.line(cc + 30, cy, cc + 30, cy - 12, 0xFF22252A.toInt())
    }
}
