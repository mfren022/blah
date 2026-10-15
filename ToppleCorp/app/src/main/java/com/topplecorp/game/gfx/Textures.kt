package com.topplecorp.game.gfx

import android.graphics.Bitmap
import android.graphics.Color
import com.topplecorp.core.Material

/**
 * Procedurally painted pixel textures for block materials, generated at the exact cell size so
 * pixels stay square. Cached per size.
 */
class Textures(val size: Int) {

    /** Base colour of each material, also used for 3D faces and debris. */
    companion object {
        fun baseColor(m: Material, variant: Int = 0): Int = when (m) {
            Material.GLASS -> 0xFF9FE3F2.toInt()
            Material.WOOD -> 0xFFC07A3E.toInt()
            Material.PLASTIC -> PLASTIC_COLORS[variant % PLASTIC_COLORS.size]
            Material.BRICK -> 0xFFC4513C.toInt()
            Material.STONE -> 0xFF9A98A6.toInt()
            Material.METAL -> 0xFF7D9BB8.toInt()
            Material.STEEL -> 0xFF59657A.toInt()
            Material.GOLD -> 0xFFFFC93C.toInt()
            Material.GENERATOR -> 0xFFF2C230.toInt()
            Material.AC_UNIT -> 0xFFDCE3EE.toInt()
            Material.WATER_TANK -> 0xFF4BA3F2.toInt()
            Material.TNT -> 0xFFE8484F.toInt()
            Material.VAULT -> 0xFF4A5468.toInt()
        }

        /** Slight per-piece brightness change so neighbouring pieces of one material read apart. */
        fun shadeVariant(m: Material, variant: Int): Int {
            val c = baseColor(m, variant)
            if (m.isSpecial || m == Material.PLASTIC) return c
            return when (Math.floorMod(variant, 3)) { 1 -> Pal.shade(c, 0.9f); 2 -> Pal.tint(c, 0.08f); else -> c }
        }

        val PLASTIC_COLORS = intArrayOf(0xFFFF6FAE.toInt(), 0xFF39C9B5.toInt(), 0xFFA77BFF.toInt())

        private fun hash(x: Int, y: Int, s: Int): Int {
            var h = x * 374761393 + y * 668265263 + s * 2147483647
            h = (h xor (h ushr 13)) * 1274126177
            return h xor (h ushr 16)
        }

        fun noise(x: Int, y: Int, s: Int): Float = ((hash(x, y, s) and 0xFFFF) / 65535f)
    }

    private val cache = HashMap<Int, Bitmap>()

    fun get(m: Material, variant: Int = 0): Bitmap {
        val v = Math.floorMod(variant, 3)
        val key = m.ordinal * 8 + v
        return cache.getOrPut(key) { paint(m, v) }
    }

    private fun paint(m: Material, variant: Int): Bitmap {
        val s = size
        val px = IntArray(s * s)
        val base = shadeVariant(m, variant)
        fun set(x: Int, y: Int, c: Int) { if (x in 0 until s && y in 0 until s) px[y * s + x] = c }
        for (y in 0 until s) for (x in 0 until s) {
            val n = noise(x, y, m.ordinal)
            px[y * s + x] = when (m) {
                Material.WOOD -> {
                    val plank = y / (s / 3).coerceAtLeast(1)
                    val grain = noise(x / 3 + plank * 17, y, 7)
                    var c = Pal.shade(base, 0.9f + grain * 0.18f)
                    if (y % (s / 3).coerceAtLeast(1) == 0) c = Pal.shade(base, 0.62f)
                    if ((x + plank * 7) % s == s - 2 && y % (s / 3) != 0) c = Pal.shade(base, 0.7f)
                    c
                }
                Material.GLASS -> {
                    val shine = (x + y) in (s / 3)..(s / 3 + 2) || (x + y) == s - 1
                    if (shine) Pal.alpha(0xFFFFFFFF.toInt(), 0.9f) else Pal.alpha(Pal.shade(base, 0.92f + n * 0.1f), 0.72f)
                }
                Material.PLASTIC -> {
                    var c = base
                    if (x < s / 2 && y < s / 3) c = Pal.tint(base, 0.25f)
                    if (y > s * 2 / 3) c = Pal.shade(base, 0.9f)
                    c
                }
                Material.BRICK -> {
                    val bh = (s / 4).coerceAtLeast(3)
                    val row = y / bh
                    val bw = s / 2
                    val off = if (row % 2 == 0) 0 else bw / 2
                    val mortar = y % bh == bh - 1 || (x + off) % bw == bw - 1
                    if (mortar) 0xFFD8C7B0.toInt() else Pal.shade(base, 0.86f + n * 0.22f)
                }
                Material.STONE -> {
                    val block = noise(x / (s / 2), y / (s / 2), 3)
                    val edge = x % (s / 2) == 0 || y % (s / 2) == 0
                    if (edge) Pal.shade(base, 0.7f) else Pal.shade(base, 0.85f + block * 0.15f + n * 0.12f)
                }
                Material.METAL -> {
                    var c = Pal.shade(base, 0.92f + (y.toFloat() / s) * -0.12f + n * 0.05f)
                    if (y == s / 4) c = Pal.tint(base, 0.4f)
                    c
                }
                Material.STEEL -> {
                    val d1 = x == y || x == y + 1
                    val d2 = x == s - 1 - y || x == s - y
                    if (d1 || d2) Pal.shade(base, 0.7f) else Pal.shade(base, 0.95f + n * 0.08f)
                }
                Material.GOLD -> {
                    var c = Pal.shade(base, 0.95f + n * 0.08f)
                    if (x + y < s / 2) c = Pal.tint(base, 0.35f)
                    if (x + y > s + s / 2) c = Pal.shade(base, 0.8f)
                    c
                }
                Material.GENERATOR -> {
                    val stripe = ((x + y) / (s / 4).coerceAtLeast(2)) % 2 == 0
                    if (y < 3 || y >= s - 3) (if (stripe) 0xFF2B2B33.toInt() else base) else Pal.shade(0xFF8C93A6.toInt(), 0.9f + n * 0.1f)
                }
                Material.AC_UNIT -> {
                    if (y % 3 == 0) Pal.shade(base, 0.82f) else Pal.shade(base, 0.97f + n * 0.04f)
                }
                Material.WATER_TANK -> {
                    if (x % (s / 2) == 1) Pal.shade(base, 0.7f) else Pal.shade(base, 0.92f + (x.toFloat() / s) * 0.15f)
                }
                Material.TNT -> {
                    val plank = x / (s / 3).coerceAtLeast(1)
                    var c = Pal.shade(0xFFB8733B.toInt(), 0.9f + noise(plank, y / 4, 9) * 0.15f)
                    if (x % (s / 3) == 0) c = Pal.shade(0xFFB8733B.toInt(), 0.65f)
                    if (y in s / 3 until s * 2 / 3) c = Pal.shade(base, 0.95f + n * 0.05f)
                    c
                }
                Material.VAULT -> Pal.shade(base, 0.9f + n * 0.12f)
            }
        }
        // Rivets / bolts on metal-ish materials.
        if (m == Material.METAL || m == Material.STEEL || m == Material.VAULT) {
            val r = 2
            for ((bx, by) in listOf(r to r, s - 1 - r to r, r to s - 1 - r, s - 1 - r to s - 1 - r)) {
                set(bx, by, Pal.tint(base, 0.6f)); set(bx + 1, by + 1, Pal.shade(base, 0.5f))
            }
        }
        if (m == Material.AC_UNIT) {
            for (y in 2 until s - 2) { set(s - 3, y, Pal.shade(base, 0.7f)) }
        }
        return Sprite.fromPixels(px, s, s)
    }

    /** Crack overlays for damage stages 1..3 (more cracks as HP drops). */
    private val crackCache = HashMap<Int, Bitmap>()

    fun cracks(stage: Int, variant: Int): Bitmap {
        val key = stage * 16 + (variant and 7)
        return crackCache.getOrPut(key) {
            val s = size
            val px = IntArray(s * s)
            val dark = Color.argb(200, 20, 16, 30)
            val light = Color.argb(120, 255, 255, 255)
            val lines = stage * 2
            for (l in 0 until lines) {
                var x = (noise(l, variant, 41) * s).toInt()
                var y = (noise(variant, l, 42) * s).toInt()
                val len = s / 2 + (noise(l, l, variant) * s / 2).toInt()
                for (i in 0 until len) {
                    if (x in 0 until s && y in 0 until s) {
                        px[y * s + x] = dark
                        if (x + 1 < s) px[y * s + x + 1] = if (px[y * s + x + 1] == 0) light else px[y * s + x + 1]
                    }
                    val r = noise(x, y, l + variant * 13)
                    when {
                        r < 0.4f -> y++
                        r < 0.7f -> { x++; y++ }
                        else -> { x--; y++ }
                    }
                }
            }
            Sprite.fromPixels(px, s, s)
        }
    }
}
