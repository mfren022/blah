package com.topplecorp.game.gfx

import android.graphics.Bitmap
import android.graphics.Canvas
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Colour identity of a city, used by its castle, sky, ground and in-game theme. */
data class CityIdentity(
    val skyTop: Int, val skyBottom: Int, val hill: Int,
    val wall: Int, val wallShade: Int, val outline: Int,
    val roof: Int, val roofShade: Int, val accent: Int, val accent2: Int,
    val window: Int, val lit: Int,
    val surface: Int, val surfaceEdge: Int, val soil: Int, val strata1: Int, val strata2: Int, val rock: Int,
) {
    companion object {
        val all = listOf(
            // Toronto: maple red and white, teal glass, autumn orange
            CityIdentity(0xFF4FA8F7.toInt(), 0xFFDFF2FF.toInt(), 0xFF8FC1E3.toInt(),
                0xFFF6F1EA.toInt(), 0xFFD9CFC2.toInt(), 0xFF3A1D2A.toInt(),
                0xFFE0313F.toInt(), 0xFFA81E2E.toInt(), 0xFF29C2B4.toInt(), 0xFFF28C28.toInt(),
                0xFF2A3F5F.toInt(), 0xFFFFE08A.toInt(),
                0xFF6CC24A.toInt(), 0xFF3F8F33.toInt(), 0xFF8B5A3C.toInt(), 0xFF6E4630.toInt(), 0xFF5A3A28.toInt(), 0xFF9A8F88.toInt()),
            // Sydney: ocean blue, sandstone gold, white sails
            CityIdentity(0xFF1592E6.toInt(), 0xFFB9F0FF.toInt(), 0xFF6FB6E0.toInt(),
                0xFFF4CF86.toInt(), 0xFFD9A85B.toInt(), 0xFF1D2F4A.toInt(),
                0xFF1E73D8.toInt(), 0xFF1456A6.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFC23B.toInt(),
                0xFF1D3B66.toInt(), 0xFFFFF1B0.toInt(),
                0xFFF2D58F.toInt(), 0xFFD9B46A.toInt(), 0xFFE3BE78.toInt(), 0xFFCFA35E.toInt(), 0xFFB98A48.toInt(), 0xFFC9B79A.toInt()),
            // Istanbul: turquoise domes, terracotta, gold, sunset purple
            CityIdentity(0xFF3E2466.toInt(), 0xFFFF9A5C.toInt(), 0xFF7A3F6E.toInt(),
                0xFFF0C9A0.toInt(), 0xFFD29E72.toInt(), 0xFF35172F.toInt(),
                0xFF19B3A6.toInt(), 0xFF0F7F78.toInt(), 0xFFF2B630.toInt(), 0xFFC4452F.toInt(),
                0xFF3B1F4A.toInt(), 0xFFFFD27A.toInt(),
                0xFFC97B52.toInt(), 0xFF9E5638.toInt(), 0xFF8A5A44.toInt(), 0xFF6F4637.toInt(), 0xFF5A372C.toInt(), 0xFFA08A7A.toInt()),
            // Zurich: alpine white, Swiss red, slate blue, spire green
            CityIdentity(0xFF8EC0F0.toInt(), 0xFFFFEDEA.toInt(), 0xFFC9D8EE.toInt(),
                0xFFFBF8F2.toInt(), 0xFFDCD6CC.toInt(), 0xFF262B38.toInt(),
                0xFF3E5F96.toInt(), 0xFF2B4570.toInt(), 0xFFE5333B.toInt(), 0xFF4C9A6E.toInt(),
                0xFF26344F.toInt(), 0xFFFFF0B8.toInt(),
                0xFF9FD07A.toInt(), 0xFF5E9E4A.toInt(), 0xFF7A6A5A.toInt(), 0xFF68707C.toInt(), 0xFF545B66.toInt(), 0xFFB0B6C0.toInt()),
            // San Francisco: international orange, painted-lady pastels, lavender fog
            CityIdentity(0xFF7E6CCB.toInt(), 0xFFFFC7A1.toInt(), 0xFFA790C8.toInt(),
                0xFFFBD9CE.toInt(), 0xFFE6B3A6.toInt(), 0xFF3A2142.toInt(),
                0xFFF0532F.toInt(), 0xFFB83A21.toInt(), 0xFF9BE3C3.toInt(), 0xFFFFE08A.toInt(),
                0xFF3A2A50.toInt(), 0xFFFFE6B0.toInt(),
                0xFF8A8796.toInt(), 0xFF5E5B6A.toInt(), 0xFF8C6E62.toInt(), 0xFF74584F.toInt(), 0xFF5F463F.toInt(), 0xFFB4A2A0.toInt()),
        )

        fun of(city: Int) = all[city.coerceIn(0, all.size - 1)]
    }
}

/**
 * One continuous pixel-art panorama of all campaign cities. Each city is a cartoon toy castle whose
 * silhouette is roughly a half circle, built from its landmarks in its own colour identity. Below the
 * shared horizontal ground is a detailed cross-section of each city's underground. Sky and soil blend
 * across the seams so neighbouring cities merge into each other.
 */
class CityPanorama(val w: Int, val h: Int, val count: Int) {
    /** Screen y of the ground surface (identical for every city). */
    val ground = h - 210
    private val pw = w * count
    private val farMargin = w / 2
    private val ids = CityIdentity.all

    private val sky: Bitmap
    private val far: Bitmap
    private val main: Bitmap
    private val clouds: List<FloatArray>

    /** An underground tunnel with an animated train running through it (global coordinates). */
    private class Tunnel(val x0: Float, val x1: Float, val y: Float, val h: Float, val body: Int, val stripe: Int, val outline: Int, val speed: Float)

    private val tunnels = ArrayList<Tunnel>()

    init {
        val s = P(pw, h)
        val f = P(pw + farMargin * 2, h)
        val m = P(pw, h)
        val rnd = Random(777)
        paintSky(s)
        paintFar(f)
        paintGround(m, rnd)
        for (i in 0 until count) {
            val cx = i * w + w / 2
            paintUnderground(m, i, cx, rnd)
            deepDetails(m, i, cx, rnd)
            paintCastle(m, i, cx, rnd)
        }
        sky = s.bitmap(); far = f.bitmap(); main = m.bitmap()
        clouds = List(16) { floatArrayOf(rnd.nextFloat() * pw, 60f + rnd.nextFloat() * 110f, 0.4f + rnd.nextFloat() * 0.6f) }
    }

    // ------------------------------------------------------------------ drawing

    /** Draws the panorama so that city [pos] (fractional during transitions) is centred on screen. */
    fun draw(c: Canvas, pos: Float, time: Float) {
        val x = -pos * w
        Draw.bitmap(c, sky, x, 0f)
        for (cl in clouds) {
            val cw = 22f + cl[2] * 26f
            val cx = x * 0.85f + (cl[0] + time * 4f * cl[2]) % pw
            if (cx + cw < -10 || cx > w + 10) continue
            val col = Pal.alpha(0xFFFFFFFF.toInt(), 0.8f)
            Draw.rect(c, cx, cl[1], cw, 6f, col)
            Draw.rect(c, cx + cw * 0.15f, cl[1] - 4, cw * 0.5f, 4f, col)
            Draw.rect(c, cx + 2, cl[1] + 6, cw - 4, 1f, Pal.alpha(0xFF8899BB.toInt(), 0.3f))
        }
        Draw.bitmap(c, far, x * 0.7f - farMargin + w * 0.15f, 0f)
        Draw.bitmap(c, main, x, 0f)
        // Underground trains
        for (t in tunnels) {
            val x0 = t.x0 + x; val x1 = t.x1 + x
            if (x1 < 0 || x0 > w) continue
            val len = x1 - x0
            val trainLen = 64f
            val tx = x0 - trainLen + ((time * t.speed) % (len + trainLen))
            c.save()
            c.clipRect(x0, t.y, x1, t.y + t.h)
            val ty = t.y + 3
            val th = t.h - 6
            Draw.rect(c, tx - 1, ty - 1, trainLen + 2, th + 2, t.outline)
            Draw.rect(c, tx, ty, trainLen, th, t.body)
            Draw.rect(c, tx, ty + th - 4, trainLen, 2f, t.stripe)
            var wx = tx + 4
            while (wx < tx + trainLen - 6) { Draw.rect(c, wx, ty + 3, 6f, 4f, 0xFFFFF4C8.toInt()); wx += 10 }
            Draw.rect(c, tx + trainLen - 3, ty + 3, 2f, 3f, 0xFFFFFFA0.toInt())
            c.restore()
        }
        // Fog rolling over San Francisco
        val sf = 4 * w + x
        if (count > 4 && sf < w && sf > -w) {
            for (i in 0 until 3) {
                val fy = ground - 120f + i * 32f
                var bx = -80f + (time * (6f + i * 3f)) % 80f
                while (bx < w) {
                    Draw.rect(c, sf + bx, fy, 52f, 6f, 0x30FFFFFF)
                    Draw.rect(c, sf + bx + 10, fy - 3, 30f, 3f, 0x24FFFFFF)
                    bx += 80f
                }
            }
        }
    }

    // ------------------------------------------------------------------ blending

    /** Weight of city i+1 versus city i at global x: pure in each city's middle, smooth across seams. */
    private fun mixAt(gx: Int): Triple<Int, Int, Float> {
        val p = gx.toFloat() / w - 0.5f
        val i = floor(p).toInt().coerceIn(0, count - 1)
        val j = (i + 1).coerceAtMost(count - 1)
        val t = (p - i).coerceIn(0f, 1f)
        val u = ((t - 0.3f) / 0.4f).coerceIn(0f, 1f)
        return Triple(i, j, u * u * (3 - 2 * u))
    }

    private inline fun blend(gx: Int, pick: (CityIdentity) -> Int): Int {
        val (i, j, t) = mixAt(gx)
        return Pal.mix(pick(ids[i]), pick(ids[j]), t)
    }

    // ------------------------------------------------------------------ sky & far

    private fun paintSky(s: P) {
        for (x in 0 until pw) {
            val top = blend(x) { it.skyTop }
            val bottom = blend(x) { it.skyBottom }
            for (y in 0 until h) s.px[y * pw + x] = Pal.mix(top, bottom, (y / (ground + 20f)).coerceIn(0f, 1f))
        }
        val suns = listOf(0.82f to 76, 0.18f to 70, 0.2f to ground - 170, 0.8f to 66, 0.78f to ground - 200)
        for ((i, pair) in suns.withIndex()) {
            if (i >= count) break
            val (fx, y) = pair
            val x = (i * w + fx * w).toInt()
            val col = if (i == 2) 0xFFFFD27A.toInt() else 0xFFFFF6CC.toInt()
            s.circle(x, y, 16, Pal.alpha(col, 0.22f))
            s.circle(x, y, 12, col)
            s.circle(x - 4, y - 4, 3, Pal.alpha(0xFFFFFFFF.toInt(), 0.6f))
        }
    }

    private fun paintFar(f: P) {
        for (x in 0 until f.w) {
            val gx = (x - farMargin).coerceIn(0, pw - 1)
            val hill = blend(gx) { it.hill }
            val y1 = ground - 26 - (sin(x / 37f) * 12 + sin(x / 13f) * 4).toInt()
            f.rect(x, y1, 1, ground - y1, hill)
            f.set(x, y1, Pal.tint(hill, 0.25f))
        }
        // Snowy Alps behind Zurich
        if (count > 3) {
            val zx = farMargin + 3 * w
            for ((px, ph) in listOf(0.05f to 120, 0.25f to 155, 0.75f to 165, 0.95f to 125)) {
                val x = zx + (px * w).toInt()
                val base = ground - 20
                val top = base - ph
                f.tri(x - 62, base, x + 62, base, x, top, 0xFFA9B9D6.toInt())
                f.tri(x, base, x + 62, base, x, top, 0xFF8C9DBD.toInt())
                f.tri(x - 22, top + 32, x + 22, top + 32, x, top, 0xFFFFFFFF.toInt())
                f.tri(x, top + 32, x + 22, top + 32, x, top, 0xFFDCE6F4.toInt())
            }
        }
    }

    // ------------------------------------------------------------------ ground cross-section

    private fun paintGround(m: P, rnd: Random) {
        val g = ground
        for (gx in 0 until pw) {
            val surface = blend(gx) { it.surface }
            val edge = blend(gx) { it.surfaceEdge }
            val soil = blend(gx) { it.soil }
            val s1 = blend(gx) { it.strata1 }
            val s2 = blend(gx) { it.strata2 }
            val b1 = g + 18 + (sin(gx / 23f) * 3 + sin(gx / 7f)).toInt()
            val b2 = g + 52 + (sin(gx / 31f + 1f) * 5 + sin(gx / 9f) * 2).toInt()
            val b3 = g + 100 + (sin(gx / 41f + 2f) * 6).toInt()
            for (y in g until h) {
                val c = when {
                    y < g + 2 -> Pal.tint(surface, 0.3f)
                    y < g + 7 -> surface
                    y < g + 9 -> edge
                    y < b1 -> soil
                    y == b1 -> Pal.shade(soil, 0.75f)
                    y < b2 -> s1
                    y == b2 -> Pal.shade(s1, 0.7f)
                    y < b3 -> s2
                    else -> Pal.shade(s2, 0.82f)
                }
                m.px[y * pw + gx] = c
            }
            // Speckles and pebbles in the soil layers
            if (gx % 3 == 0) {
                val yy = g + 10 + ((gx * 37) % (h - g - 12))
                m.set(gx, yy, Pal.tint(m.px[yy * pw + gx], 0.15f))
            }
        }
        // Grass tufts, pebbles and rocks
        for (i in 0 until pw / 6) {
            val x = rnd.nextInt(pw)
            m.set(x, g - 1, blend(x) { it.surfaceEdge })
            m.set(x + 1, g - 2, blend(x) { it.surfaceEdge })
        }
        for (i in 0 until pw / 14) {
            val x = rnd.nextInt(pw)
            val y = g + 22 + rnd.nextInt(h - g - 24)
            val r = 2 + rnd.nextInt(4)
            val rock = blend(x) { it.rock }
            m.ellipse(x, y, r + 1, r, Pal.shade(rock, 0.6f))
            m.ellipse(x, y, r, r - 1, rock)
            m.set(x - r / 2, y - r / 2, Pal.tint(rock, 0.4f))
        }
    }

    /** Fossils, crystals and old pipes scattered through the deep strata. */
    private fun deepDetails(m: P, city: Int, cx: Int, rnd: Random) {
        val id = ids[city]
        val g = ground
        val gem = when (city) { 0 -> 0xFFE0313F.toInt(); 1 -> 0xFF3FA2E8.toInt(); 2 -> 0xFF19B3A6.toInt(); 3 -> 0xFFFFFFFF.toInt(); else -> 0xFFF2C230.toInt() }
        repeat(6) {
            val x = cx - 115 + rnd.nextInt(230)
            val y = g + 86 + rnd.nextInt((h - g - 92).coerceAtLeast(1))
            when (rnd.nextInt(3)) {
                0 -> { // bone
                    m.rect(x - 4, y, 9, 2, Pal.tint(id.rock, 0.5f))
                    m.rect(x - 5, y - 1, 2, 4, Pal.tint(id.rock, 0.5f)); m.rect(x + 4, y - 1, 2, 4, Pal.tint(id.rock, 0.5f))
                }
                1 -> { // crystal cluster
                    m.tri(x - 3, y + 4, x, y + 4, x - 1, y - 4, id.outline); m.tri(x - 2, y + 3, x, y + 3, x - 1, y - 2, gem)
                    m.tri(x, y + 4, x + 4, y + 4, x + 2, y - 2, id.outline); m.tri(x + 1, y + 3, x + 3, y + 3, x + 2, y, Pal.tint(gem, 0.3f))
                }
                else -> { // ammonite fossil
                    m.circle(x, y, 4, Pal.shade(id.rock, 0.7f)); m.circle(x, y, 3, id.rock); m.circle(x + 1, y, 1, Pal.shade(id.rock, 0.7f))
                }
            }
        }
    }

    private fun tunnel(m: P, x0: Int, x1: Int, y: Int, th: Int, id: CityIdentity, body: Int, stripe: Int, speed: Float) {
        m.rect(x0 - 2, y - 2, x1 - x0 + 4, th + 4, id.outline)
        m.rect(x0, y, x1 - x0, th, Pal.shade(id.strata2, 0.45f))
        m.rect(x0, y + th - 2, x1 - x0, 2, Pal.shade(id.rock, 0.7f))
        for (x in x0 until x1 step 6) m.rect(x, y + th - 1, 3, 1, Pal.shade(id.rock, 0.5f))
        // Rounded ends
        m.ellipse(x0, y + th / 2, 4, th / 2 + 2, id.outline); m.ellipse(x1, y + th / 2, 4, th / 2 + 2, id.outline)
        tunnels += Tunnel(x0.toFloat(), x1.toFloat(), y.toFloat(), th.toFloat(), body, stripe, id.outline, speed)
    }

    private fun paintUnderground(m: P, city: Int, cx: Int, rnd: Random) {
        val id = ids[city]
        val g = ground
        when (city) {
            0 -> { // Toronto: subway, utility pipes and maple roots
                tunnel(m, cx - 110, cx + 110, g + 40, 18, id, 0xFFE0313F.toInt(), 0xFFFFFFFF.toInt(), 46f)
                m.rect(cx - 96, g + 9, 5, 31, id.outline); m.rect(cx - 95, g + 9, 3, 31, 0xFF3D7CC9.toInt())
                m.rect(cx - 120, g + 22, 240, 5, id.outline); m.rect(cx - 120, g + 23, 240, 3, 0xFFF2C230.toInt())
                for (x in cx - 120 until cx + 120 step 24) m.rect(x, g + 21, 3, 7, id.outline)
                for (tx in listOf(cx - 116, cx + 116)) roots(m, tx, g + 9, id)
                m.rect(cx + 60, g + 9, 14, 6, id.outline); m.rect(cx + 61, g + 10, 12, 4, 0xFF5D6670.toInt()) // manhole shaft
            }
            1 -> { // Sydney: sand strata, shells, a buried treasure chest and a stormwater drain
                for (i in 0 until 14) shell(m, cx - 120 + rnd.nextInt(240), g + 12 + rnd.nextInt(70), rnd.nextBoolean())
                val dx = cx - 100; val dy = g + 46
                m.rect(dx - 2, dy - 2, 124, 18, id.outline)
                m.rect(dx, dy, 120, 14, 0xFF2D5D8C.toInt())
                m.rect(dx, dy + 6, 120, 8, 0xFF3FA2E8.toInt())
                for (x in dx until dx + 120 step 7) m.rect(x, dy + 7, 4, 1, 0x88FFFFFF.toInt())
                // Treasure chest
                val tx = cx + 64; val ty = g + 30
                m.rect(tx - 1, ty - 1, 26, 18, id.outline)
                m.rect(tx, ty, 24, 16, 0xFF9C5B2E.toInt())
                m.rect(tx, ty, 24, 6, 0xFFB86E3A.toInt())
                m.rect(tx, ty + 6, 24, 2, 0xFFFFC23B.toInt())
                m.rect(tx + 10, ty + 5, 4, 5, 0xFFFFC23B.toInt())
                m.rect(tx + 4, ty - 3, 3, 2, 0xFFFFE07A.toInt()); m.rect(tx + 15, ty - 2, 3, 2, 0xFFFFE07A.toInt())
                // Starfish fossil
                star(m, cx - 30, g + 30, 0xFFF08A5D.toInt(), id.outline)
            }
            2 -> { // Istanbul: the Basilica Cistern with columns, arches, water and lanterns
                val x0 = cx - 104; val x1 = cx + 104
                val top = g + 14; val bot = g + 74
                m.rect(x0 - 2, top - 2, x1 - x0 + 4, bot - top + 4, id.outline)
                m.rect(x0, top, x1 - x0, bot - top, 0xFF2A1630.toInt())
                for (c in 0..8) {
                    val colX = x0 + 6 + c * 24
                    for (a in 0 until 12) { // brick arch between columns
                        val ax = colX + a * 2
                        val ay = top + 4 + (abs(a - 6) * abs(a - 6)) / 6
                        if (c < 8) m.rect(ax, ay, 2, 3, 0xFFB5583C.toInt())
                    }
                    m.rect(colX - 2, top + 8, 5, bot - top - 20, 0xFFC9A784.toInt())
                    m.rect(colX + 1, top + 8, 1, bot - top - 20, 0xFF9E7E60.toInt())
                    m.rect(colX - 3, top + 7, 7, 2, 0xFFE0C29E.toInt())
                    if (c % 2 == 1) { m.rect(colX + 10, top + 14, 3, 4, 0xFFFFD27A.toInt()); m.rect(colX + 11, top + 10, 1, 4, 0xFF8E6C59.toInt()) }
                }
                // Water with reflections
                m.rect(x0, bot - 12, x1 - x0, 12, 0xFF1F5E6E.toInt())
                for (c in 0..8) m.rect(x0 + 5 + c * 24, bot - 11, 3, 8, 0x55C9A784)
                for (x in x0 until x1 step 9) m.rect(x, bot - 6, 4, 1, 0x66FFFFFF)
                // Medusa head stone at a column base
                m.rect(x0 + 75, bot - 18, 8, 6, 0xFF8FA39A.toInt()); m.rect(x0 + 77, bot - 16, 1, 1, id.outline); m.rect(x0 + 80, bot - 16, 1, 1, id.outline)
            }
            3 -> { // Zurich: a Swiss bank vault full of gold, deep in granite
                val x0 = cx - 92; val x1 = cx + 92
                val top = g + 16; val bot = g + 72
                m.rect(x0 - 3, top - 3, x1 - x0 + 6, bot - top + 6, id.outline)
                m.rect(x0 - 1, top - 1, x1 - x0 + 2, bot - top + 2, 0xFF9AA3AE.toInt())
                m.rect(x0 + 2, top + 2, x1 - x0 - 4, bot - top - 4, 0xFF4A505C.toInt())
                for (y in top + 8 until bot - 2 step 8) m.rect(x0 + 2, y, x1 - x0 - 4, 1, 0xFF3C414B.toInt())
                // Vault door
                val vx = x0 + 30; val vy = (top + bot) / 2
                m.circle(vx, vy, 23, id.outline); m.circle(vx, vy, 21, 0xFFB9C2CC.toInt()); m.circle(vx, vy, 16, 0xFF8E98A4.toInt())
                for (k in 0 until 8) {
                    val a = k * Math.PI / 4
                    m.line(vx, vy, vx + (cos(a) * 13).roundToInt(), vy + (sin(a) * 13).roundToInt(), 0xFFDCE2E8.toInt(), 2)
                }
                m.circle(vx, vy, 4, 0xFFFFC23B.toInt())
                // Gold bar stacks
                var stackX = x0 + 64
                for (k in 0 until 4) {
                    val rows = 3 + (k % 2)
                    for (r in 0 until rows) for (b in 0 until (rows - r)) {
                        val bx = stackX + b * 9 + r * 4
                        val by = bot - 8 - r * 6
                        m.rect(bx - 1, by - 1, 10, 7, id.outline)
                        m.rect(bx, by, 8, 5, 0xFFF2C230.toInt())
                        m.rect(bx + 1, by, 6, 1, 0xFFFFF0A0.toInt())
                    }
                    stackX += 28
                }
                // Swiss cross plaque
                m.rect(x1 - 18, top + 6, 12, 12, id.accent)
                m.rect(x1 - 13, top + 8, 2, 8, 0xFFFFFFFF.toInt()); m.rect(x1 - 16, top + 11, 8, 2, 0xFFFFFFFF.toInt())
            }
            else -> { // San Francisco: BART tube, cable car cable slot, the fault line and gold nuggets
                m.rect(cx - 120, g + 10, 240, 3, id.outline)
                m.rect(cx - 120, g + 11, 240, 1, 0xFFB0A8B8.toInt())
                for (px in listOf(cx - 80, cx + 40)) { m.circle(px, g + 16, 4, id.outline); m.circle(px, g + 16, 3, 0xFF9C9AA6.toInt()) }
                tunnel(m, cx - 115, cx + 115, g + 50, 18, id, 0xFFD8DCE4.toInt(), 0xFF2F7FD0.toInt(), 58f)
                // Fault line
                var fx = cx + 30; var fy = g + 9
                while (fy < h) {
                    val nx = fx + (if ((fy / 6) % 2 == 0) 5 else -3)
                    m.line(fx, fy, nx, fy + 6, Pal.shade(id.strata2, 0.4f), 2)
                    fx = nx; fy += 6
                }
                for ((nx, ny) in listOf(cx - 60 to g + 32, cx - 20 to g + 38, cx + 74 to g + 28, cx + 96 to g + 40)) {
                    m.ellipse(nx, ny, 4, 3, id.outline); m.ellipse(nx, ny, 3, 2, 0xFFF2C230.toInt()); m.set(nx - 1, ny - 1, 0xFFFFF6B0.toInt())
                }
            }
        }
    }

    private fun roots(m: P, x: Int, y: Int, id: CityIdentity) {
        val col = Pal.shade(id.soil, 0.6f)
        m.line(x, y, x - 6, y + 10, col); m.line(x, y, x + 5, y + 12, col); m.line(x, y, x, y + 14, col)
        m.line(x - 6, y + 10, x - 10, y + 13, col); m.line(x + 5, y + 12, x + 9, y + 14, col)
    }

    private fun shell(m: P, x: Int, y: Int, pink: Boolean) {
        val c = if (pink) 0xFFF7C6D0.toInt() else 0xFFFFF6E8.toInt()
        m.ellipse(x, y, 3, 2, 0xFF8E6C59.toInt(), topHalfOnly = true)
        m.ellipse(x, y, 2, 2, c, topHalfOnly = true)
        m.rect(x - 2, y, 5, 1, 0xFF8E6C59.toInt())
    }

    private fun star(m: P, x: Int, y: Int, c: Int, outline: Int) {
        for (k in 0 until 5) {
            val a = -Math.PI / 2 + k * 2 * Math.PI / 5
            m.line(x, y, x + (cos(a) * 6).roundToInt(), y + (sin(a) * 6).roundToInt(), outline, 3)
        }
        for (k in 0 until 5) {
            val a = -Math.PI / 2 + k * 2 * Math.PI / 5
            m.line(x, y, x + (cos(a) * 5).roundToInt(), y + (sin(a) * 5).roundToInt(), c, 1)
        }
    }

    // ------------------------------------------------------------------ toy castle parts (all outlined)

    private fun box(m: P, x: Int, y: Int, bw: Int, bh: Int, fill: Int, shade: Int, outline: Int) {
        m.rect(x - 1, y - 1, bw + 2, bh + 2, outline)
        m.rect(x, y, bw, bh, fill)
        m.rect(x + bw - 3, y, 3, bh, shade)
        m.rect(x, y, 2, bh, Pal.tint(fill, 0.35f))
    }

    /** Castle tower from the ground up to [top], with optional merlons. */
    private fun tower(m: P, cx: Int, tw: Int, top: Int, id: CityIdentity, fill: Int = id.wall, merlons: Boolean = true, windows: Int = 2) {
        val x = cx - tw / 2
        val shade = Pal.shade(fill, 0.82f)
        box(m, x, top, tw, ground - top, fill, shade, id.outline)
        if (merlons) {
            var mx = x - 1
            while (mx < x + tw) {
                m.rect(mx - 1, top - 6, 6, 6, id.outline)
                m.rect(mx, top - 5, 4, 5, fill)
                mx += 7
            }
            m.rect(x - 2, top - 1, tw + 4, 3, id.outline)
            m.rect(x - 1, top, tw + 2, 1, Pal.tint(fill, 0.3f))
        }
        var wy = top + 10
        repeat(windows) {
            if (wy + 10 < ground - 14) window(m, cx, wy, id)
            wy += 22
        }
    }

    private fun window(m: P, cx: Int, y: Int, id: CityIdentity) {
        m.rect(cx - 3, y - 1, 7, 10, id.outline)
        m.rect(cx - 2, y + 1, 5, 7, id.lit)
        m.rect(cx - 1, y, 3, 1, id.lit)
        m.rect(cx, y + 1, 1, 7, Pal.shade(id.lit, 0.8f))
    }

    private fun cone(m: P, cx: Int, base: Int, halfW: Int, height: Int, id: CityIdentity, color: Int = id.roof) {
        m.tri(cx - halfW - 2, base + 1, cx + halfW + 2, base + 1, cx, base - height - 2, id.outline)
        m.tri(cx - halfW, base, cx + halfW, base, cx, base - height, color)
        m.tri(cx, base, cx + halfW, base, cx, base - height, Pal.shade(color, 0.78f))
        m.rect(cx - halfW, base - 1, halfW * 2 + 1, 2, Pal.shade(color, 0.65f))
    }

    private fun dome(m: P, cx: Int, base: Int, rx: Int, ry: Int, id: CityIdentity, color: Int = id.roof, finial: Int = id.accent) {
        m.ellipse(cx, base, rx + 1, ry + 1, id.outline, topHalfOnly = true)
        m.ellipse(cx, base, rx, ry, color, topHalfOnly = true)
        m.ellipse(cx + rx / 3, base, rx * 2 / 3, ry, Pal.shade(color, 0.85f), topHalfOnly = true)
        m.ellipse(cx - rx / 3, base - ry / 2, rx / 4, ry / 5, Pal.tint(color, 0.45f))
        m.rect(cx - rx - 1, base, rx * 2 + 3, 2, id.outline)
        m.rect(cx, base - ry - 6, 1, 6, id.outline)
        m.rect(cx - 1, base - ry - 8, 3, 3, finial)
    }

    private fun flag(m: P, x: Int, top: Int, color: Int, id: CityIdentity, emblem: Int = 0) {
        m.rect(x, top - 14, 1, 14, id.outline)
        m.rect(x + 1, top - 14, 10, 7, id.outline)
        m.rect(x + 1, top - 13, 9, 5, color)
        if (emblem != 0) { m.rect(x + 4, top - 12, 3, 3, emblem) }
    }

    private fun door(m: P, cx: Int, halfW: Int, height: Int, id: CityIdentity) {
        m.arch(cx, ground, halfW + 1, height + 1, id.outline)
        m.arch(cx, ground, halfW, height, Pal.shade(id.window, 0.8f))
        m.rect(cx, ground - height + halfW, 1, height - halfW, Pal.shade(id.window, 0.6f))
    }

    /** The common half-circle body every city castle is built on. */
    private fun body(m: P, cx: Int, r: Int, ry: Int, id: CityIdentity) {
        m.ellipse(cx, ground, r + 1, ry + 1, id.outline, topHalfOnly = true)
        m.ellipse(cx, ground, r, ry, id.wall, topHalfOnly = true)
        // Brick texture and shading on the right side
        for (y in ground - ry until ground) for (x in cx - r until cx + r) {
            val dx = (x - cx).toFloat() / r; val dy = (ground - y).toFloat() / ry
            if (dx * dx + dy * dy > 1f) continue
            val row = (ground - y) / 5
            if ((ground - y) % 5 == 0 || (x + if (row % 2 == 0) 0 else 5) % 10 == 0) m.set(x, y, id.wallShade)
            if (dx > 0.55f) m.set(x, y, Pal.alpha(id.outline, 0.12f))
        }
        // Merlons along the arc
        var a = 0.12
        while (a < Math.PI - 0.1) {
            val x = cx + (cos(a) * r).roundToInt()
            val y = ground - (sin(a) * ry).roundToInt()
            m.rect(x - 3, y - 6, 7, 6, id.outline)
            m.rect(x - 2, y - 5, 5, 5, id.wall)
            a += 0.17
        }
    }

    // ------------------------------------------------------------------ cities

    private fun paintCastle(m: P, city: Int, cx: Int, rnd: Random) {
        val id = ids[city]
        val r = (w * 0.44f).toInt()           // half width of the dome silhouette
        val R = (r * 1.15f).toInt()           // silhouette height scale
        fun hAt(fr: Float) = (sqrt((1f - fr * fr).coerceAtLeast(0f)) * R).toInt()
        val outerX = (r * 0.86f).toInt(); val midX = (r * 0.62f).toInt(); val innerX = (r * 0.36f).toInt()
        val outerTop = ground - hAt(0.86f); val midTop = ground - hAt(0.62f); val innerTop = ground - hAt(0.36f)
        val keepTop = ground - (R * 0.9f).toInt()
        body(m, cx, r, (R * 0.8f).toInt(), id)
        when (city) {
            0 -> toronto(m, cx, id, outerX, midX, innerX, outerTop, midTop, innerTop, keepTop)
            1 -> sydney(m, cx, id, outerX, midX, innerX, outerTop, midTop, innerTop, keepTop)
            2 -> istanbul(m, cx, id, outerX, midX, innerX, outerTop, midTop, innerTop, keepTop)
            3 -> zurich(m, cx, id, outerX, midX, innerX, outerTop, midTop, innerTop, keepTop)
            else -> sanFrancisco(m, cx, id, outerX, midX, innerX, outerTop, midTop, innerTop, keepTop)
        }
        // Little trees / decor at the castle's feet
        val leaf = when (city) { 0 -> id.accent2; 1 -> 0xFF3FAE5A.toInt(); 2 -> 0xFF4E8F4A.toInt(); 3 -> 0xFF2F7A4E.toInt(); else -> 0xFF6BBF7A.toInt() }
        for (tx in listOf(cx - r - 10, cx + r + 10)) {
            if (city == 1) palm(m, tx, id) else if (city == 3) pine(m, tx, id) else {
                m.rect(tx - 2, ground - 12, 5, 12, id.outline); m.rect(tx - 1, ground - 12, 3, 12, 0xFF6B4226.toInt())
                m.circle(tx, ground - 18, 9, id.outline); m.circle(tx, ground - 18, 8, leaf)
                m.circle(tx - 3, ground - 21, 3, Pal.tint(leaf, 0.3f))
            }
        }
    }

    private fun palm(m: P, x: Int, id: CityIdentity) {
        for (y in ground - 30 until ground) m.rect(x - 1 + ((ground - y) / 10), y, 3, 1, if (y % 3 == 0) 0xFF8E5A33.toInt() else 0xFFB07A48.toInt())
        val top = ground - 30
        for ((dx, dy) in listOf(-12 to 4, 12 to 4, -9 to -4, 9 to -4, 0 to -8)) m.line(x + 3, top, x + 3 + dx, top + dy, 0xFF2E9E4F.toInt(), 3)
    }

    private fun pine(m: P, x: Int, id: CityIdentity) {
        m.rect(x - 1, ground - 6, 3, 6, 0xFF6B4226.toInt())
        for (k in 0 until 3) {
            val base = ground - 4 - k * 8
            m.tri(x - 10 + k * 2, base + 1, x + 10 - k * 2, base + 1, x, base - 12, id.outline)
            m.tri(x - 9 + k * 2, base, x + 9 - k * 2, base, x, base - 11, 0xFF2F7A4E.toInt())
        }
        m.rect(x - 1, ground - 30, 3, 2, 0xFFFFFFFF.toInt())
    }

    private fun toronto(m: P, cx: Int, id: CityIdentity, oX: Int, mX: Int, iX: Int, oT: Int, mT: Int, iT: Int, kT: Int) {
        // CN Tower rising from behind the keep
        val towerTop = kT - 120
        for (y in towerTop + 46 until kT + 10) {
            val t = (y - towerTop - 46f) / (kT + 10 - towerTop - 46f)
            val half = (2 + t * 5).roundToInt()
            m.rect(cx - half - 1, y, half * 2 + 3, 1, id.outline)
            m.rect(cx - half, y, half * 2 + 1, 1, 0xFFE9EDF2.toInt())
            m.rect(cx + half - 1, y, 1, 1, 0xFFB8C0CA.toInt())
        }
        m.ellipse(cx, towerTop + 72, 13, 7, id.outline); m.ellipse(cx, towerTop + 72, 12, 6, 0xFF8E98A6.toInt())
        m.rect(cx - 11, towerTop + 71, 23, 2, id.lit)
        m.ellipse(cx, towerTop + 48, 5, 3, id.outline)
        m.rect(cx, towerTop, 1, 46, id.outline)
        m.rect(cx, towerTop - 2, 1, 3, id.roof)
        // Outer: red-roofed turrets with maple flags
        for (s in listOf(-1, 1)) {
            tower(m, cx + s * oX, 18, oT, id, windows = 1, merlons = false)
            cone(m, cx + s * oX, oT, 11, 20, id)
            flag(m, cx + s * oX, oT - 21, id.roof, id, 0xFFFFFFFF.toInt())
        }
        // Mid: teal glass towers
        for (s in listOf(-1, 1)) {
            tower(m, cx + s * mX, 22, mT, id, fill = id.accent, windows = 0)
            for (y in mT + 6 until ground - 8 step 6) for (x in cx + s * mX - 8 until cx + s * mX + 8 step 5) m.rect(x, y, 3, 3, Pal.tint(id.accent, 0.55f))
        }
        // Inner: City Hall's curved towers
        for (s in listOf(-1, 1)) {
            val bx = cx + s * iX - 13
            for (x in 0 until 26) {
                val k = if (s < 0) x else 25 - x
                val curve = (sqrt(1f - (k / 26f) * (k / 26f)) * 16).toInt()
                val top = iT + (16 - curve)
                m.rect(bx + x, top - 1, 1, ground - top + 1, id.outline)
                if (x in 1..24) m.rect(bx + x, top, 1, ground - top, if (x % 4 == 0) id.wallShade else id.wall)
            }
        }
        // Keep: Princes' Gates
        tower(m, cx, 50, kT, id, windows = 0)
        for (colX in listOf(cx - 20, cx - 10, cx + 9, cx + 19)) { m.rect(colX, kT + 8, 3, ground - kT - 8, Pal.tint(id.wall, 0.4f)); m.rect(colX + 2, kT + 8, 1, ground - kT - 8, id.wallShade) }
        m.rect(cx - 25, kT + 6, 50, 3, id.roof)
        door(m, cx, 9, 40, id)
        // Maple leaf crest
        val ly = kT + 24
        m.tri(cx, ly - 9, cx - 4, ly + 4, cx + 4, ly + 4, id.roof)
        m.tri(cx - 9, ly - 1, cx - 1, ly + 6, cx - 3, ly - 1, id.roof)
        m.tri(cx + 9, ly - 1, cx + 1, ly + 6, cx + 3, ly - 1, id.roof)
    }

    private fun sydney(m: P, cx: Int, id: CityIdentity, oX: Int, mX: Int, iX: Int, oT: Int, mT: Int, iT: Int, kT: Int) {
        // Sydney Tower spire behind the keep
        m.rect(cx - 2, kT - 110, 5, 110, id.outline); m.rect(cx - 1, kT - 110, 3, 110, 0xFFDDE4EC.toInt())
        m.ellipse(cx, kT - 92, 9, 11, id.outline); m.ellipse(cx, kT - 92, 8, 10, id.accent2)
        m.rect(cx - 8, kT - 92, 17, 2, Pal.shade(id.accent2, 0.6f))
        m.rect(cx, kT - 126, 1, 16, id.outline)
        // Outer: blue cone turrets
        for (s in listOf(-1, 1)) {
            tower(m, cx + s * oX, 18, oT, id, windows = 1, merlons = false)
            cone(m, cx + s * oX, oT, 11, 18, id)
        }
        // Mid: towers crowned with Opera House sails
        for (s in listOf(-1, 1)) {
            val tx = cx + s * mX
            tower(m, tx, 24, mT + 14, id, windows = 1, merlons = false)
            for ((dx, sh) in listOf(-8 to 16, 0 to 26, 8 to 20)) {
                val sx = tx + s * dx
                m.tri(sx - 7, mT + 15, sx + 7, mT + 15, sx + s * 5, mT + 15 - sh, id.outline)
                m.tri(sx - 6, mT + 14, sx + 6, mT + 14, sx + s * 5, mT + 14 - sh + 1, id.accent)
                m.tri(sx, mT + 14, sx + 6, mT + 14, sx + s * 5, mT + 14 - sh + 1, 0xFFDCE3EA.toInt())
            }
        }
        // Inner: granite pylons with blue flags
        for (s in listOf(-1, 1)) {
            tower(m, cx + s * iX, 22, iT, id, fill = 0xFFE7D3B0.toInt(), windows = 1)
            flag(m, cx + s * iX, iT - 6, id.roof, id, 0xFFFFFFFF.toInt())
        }
        // Keep: Harbour Bridge arch spanning the pylons over a sandstone gate
        val span = iX - 12
        val deckY = kT + 34
        val rise = 44
        for (x in -span..span) {
            val t = x.toFloat() / span
            val yTop = deckY - 10 - (rise * (1 - t * t)).toInt()
            m.rect(cx + x, yTop - 1, 1, 5, id.outline)
            m.rect(cx + x, yTop, 1, 3, 0xFF5E7894.toInt())
            if ((x + span) % 7 == 0) m.rect(cx + x, yTop + 3, 1, deckY - yTop - 3, id.outline)
        }
        tower(m, cx, 46, deckY, id, windows = 0, merlons = false)
        m.rect(cx - span - 2, deckY - 2, span * 2 + 5, 5, id.outline)
        m.rect(cx - span - 1, deckY - 1, span * 2 + 3, 3, 0xFF5E7894.toInt())
        door(m, cx, 10, 34, id)
    }

    private fun istanbul(m: P, cx: Int, id: CityIdentity, oX: Int, mX: Int, iX: Int, oT: Int, mT: Int, iT: Int, kT: Int) {
        // Outer: terracotta Galata-style towers with cone roofs
        for (s in listOf(-1, 1)) {
            tower(m, cx + s * oX, 20, oT, id, fill = id.accent2, windows = 1, merlons = false)
            cone(m, cx + s * oX, oT, 12, 22, id, color = 0xFF4A3B5C.toInt())
        }
        // Mid: small turquoise domes
        for (s in listOf(-1, 1)) {
            tower(m, cx + s * mX, 26, mT + 10, id, windows = 1, merlons = false)
            dome(m, cx + s * mX, mT + 10, 13, 12, id)
        }
        // Inner: slender minarets reaching above the dome line
        for (s in listOf(-1, 1)) {
            val mx = cx + s * iX
            val top = iT - 40
            box(m, mx - 4, top, 9, ground - top, 0xFFFFF4E6.toInt(), 0xFFE0D2BE.toInt(), id.outline)
            for (b in listOf(top + 22, top + 64)) { m.rect(mx - 7, b - 1, 15, 4, id.outline); m.rect(mx - 6, b, 13, 2, id.accent) }
            cone(m, mx, top, 4, 22, id)
            m.rect(mx, top - 28, 1, 4, id.accent)
        }
        // Keep: great dome over an Iznik-tiled portal
        tower(m, cx, 56, kT + 26, id, windows = 0, merlons = false)
        dome(m, cx, kT + 26, 30, 30, id)
        for (i in -2..2) m.rect(cx + i * 10, kT + 30, 3, 6, id.outline)
        val pl = cx - 16; val pt = ground - 52
        m.rect(pl - 1, pt - 1, 34, 53, id.outline)
        for (y in pt until ground) for (x in pl until pl + 32) {
            m.set(x, y, if (((x + y) / 3) % 2 == 0) id.roof else 0xFF1E5F9E.toInt())
        }
        m.pointedArch(cx, ground, 10, 42, id.outline)
        m.pointedArch(cx, ground, 9, 40, id.window)
        m.rect(cx - 1, ground - 26, 3, 4, id.lit)
    }

    private fun zurich(m: P, cx: Int, id: CityIdentity, oX: Int, mX: Int, iX: Int, oT: Int, mT: Int, iT: Int, kT: Int) {
        // Outer: turrets with Swiss flags
        for (s in listOf(-1, 1)) {
            tower(m, cx + s * oX, 18, oT, id, windows = 1)
            flag(m, cx + s * oX - 4, oT - 6, id.accent, id, 0xFFFFFFFF.toInt())
        }
        // Mid: chalet towers with steep slate roofs and red shutters
        for (s in listOf(-1, 1)) {
            val tx = cx + s * mX
            tower(m, tx, 24, mT + 16, id, windows = 0, merlons = false)
            cone(m, tx, mT + 16, 15, 24, id)
            for (wy in listOf(mT + 26, mT + 50)) {
                window(m, tx, wy, id)
                m.rect(tx - 7, wy, 3, 8, id.accent); m.rect(tx + 5, wy, 3, 8, id.accent)
            }
        }
        // Inner: clock towers with green spires
        for (s in listOf(-1, 1)) {
            val tx = cx + s * iX
            tower(m, tx, 22, iT + 20, id, windows = 0, merlons = false)
            cone(m, tx, iT + 20, 12, 32, id, color = id.accent2)
            m.circle(tx, iT + 34, 7, id.outline); m.circle(tx, iT + 34, 6, 0xFFFFFFFF.toInt())
            m.rect(tx, iT + 30, 1, 4, id.outline); m.rect(tx, iT + 34, 3, 1, id.outline)
        }
        // Keep: Grossmünster twin towers
        tower(m, cx, 52, kT + 40, id, windows = 0, merlons = false)
        cone(m, cx, kT + 40, 27, 14, id, color = id.accent)
        for (tx in listOf(cx - 15, cx + 15)) {
            tower(m, tx, 18, kT, id, windows = 2, merlons = false)
            dome(m, tx, kT, 9, 12, id, finial = id.accent2)
        }
        m.circle(cx, kT + 64, 7, id.outline); m.circle(cx, kT + 64, 6, 0xFF9CC4E8.toInt()); m.rect(cx, kT + 58, 1, 12, id.outline)
        door(m, cx, 9, 34, id)
    }

    private fun sanFrancisco(m: P, cx: Int, id: CityIdentity, oX: Int, mX: Int, iX: Int, oT: Int, mT: Int, iT: Int, kT: Int) {
        val top = kT - 70
        val legs = listOf(cx - 18, cx + 10)
        // Cables from the Golden Gate tower to the mid towers
        for (s in listOf(-1, 1)) {
            val tx = if (s < 0) legs[0] + 4 else legs[1] + 4
            val ex = cx + s * mX; val ey = mT + 4
            var px = tx; var py = top + 4
            val n = abs(ex - tx)
            for (i in 0..n) {
                val x = tx + s * i
                val t = i / n.toFloat()
                val y = (top + 4 + (ey - top - 4) * (1 - (1 - t) * (1 - t))).toInt()
                m.line(px, py, x, y, id.roof, 2)
                px = x; py = y
            }
        }
        // Outer: orange cone turrets
        for (s in listOf(-1, 1)) {
            tower(m, cx + s * oX, 18, oT, id, windows = 1, merlons = false)
            cone(m, cx + s * oX, oT, 11, 18, id)
        }
        // Mid: fluted Coit towers
        for (s in listOf(-1, 1)) {
            val tx = cx + s * mX
            tower(m, tx, 20, mT, id, fill = 0xFFF5EEDF.toInt(), windows = 0)
            for (fx in tx - 6..tx + 6 step 4) m.rect(fx, mT + 4, 1, ground - mT - 8, 0xFFD9CCB4.toInt())
        }
        // Inner: Painted Lady turrets in mint and cream
        for ((k, s) in listOf(-1, 1).withIndex()) {
            val tx = cx + s * iX
            val col = if (k == 0) id.accent else id.accent2
            tower(m, tx, 24, iT + 14, id, fill = col, windows = 2, merlons = false)
            cone(m, tx, iT + 14, 13, 20, id, color = 0xFFB98ADB.toInt())
            m.rect(tx - 10, iT + 18, 21, 2, 0xFFFFFFFF.toInt())
        }
        // Keep: Golden Gate tower over a pink gatehouse
        tower(m, cx, 50, kT + 40, id, windows = 0)
        for (lx in legs) box(m, lx, top, 9, kT + 40 - top, id.roof, id.roofShade, id.outline)
        for (py in listOf(top, top + 26, top + 54)) box(m, legs[0], py, legs[1] - legs[0] + 9, 7, id.roof, id.roofShade, id.outline)
        door(m, cx, 10, 34, id)
        // Cable car parked by the gate
        val cc = cx + 22; val cy = ground - 18
        box(m, cc, cy, 26, 17, 0xFFC0392B.toInt(), 0xFF8F2620.toInt(), id.outline)
        m.rect(cc, cy, 26, 2, id.accent2)
        for (i in 0 until 3) m.rect(cc + 3 + i * 7, cy + 5, 5, 5, id.lit)
    }
}
