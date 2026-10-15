package com.topplecorp.game.gfx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter

/** Proportional 5x7 bitmap font rendered from a glyph atlas. */
object PixelFont {
    const val GLYPH_H = 7
    private const val CELL = 6

    private val GLYPHS: Map<Char, String> = mapOf(
        'A' to ".###.|#...#|#...#|#####|#...#|#...#|#...#",
        'B' to "####.|#...#|#...#|####.|#...#|#...#|####.",
        'C' to ".###.|#...#|#....|#....|#....|#...#|.###.",
        'D' to "####.|#...#|#...#|#...#|#...#|#...#|####.",
        'E' to "#####|#....|#....|####.|#....|#....|#####",
        'F' to "#####|#....|#....|####.|#....|#....|#....",
        'G' to ".###.|#...#|#....|#.###|#...#|#...#|.####",
        'H' to "#...#|#...#|#...#|#####|#...#|#...#|#...#",
        'I' to "###|.#.|.#.|.#.|.#.|.#.|###",
        'J' to "..###|...#.|...#.|...#.|#..#.|#..#.|.##..",
        'K' to "#...#|#..#.|#.#..|##...|#.#..|#..#.|#...#",
        'L' to "#....|#....|#....|#....|#....|#....|#####",
        'M' to "#...#|##.##|#.#.#|#.#.#|#...#|#...#|#...#",
        'N' to "#...#|##..#|#.#.#|#..##|#...#|#...#|#...#",
        'O' to ".###.|#...#|#...#|#...#|#...#|#...#|.###.",
        'P' to "####.|#...#|#...#|####.|#....|#....|#....",
        'Q' to ".###.|#...#|#...#|#...#|#.#.#|#..#.|.##.#",
        'R' to "####.|#...#|#...#|####.|#.#..|#..#.|#...#",
        'S' to ".####|#....|#....|.###.|....#|....#|####.",
        'T' to "#####|..#..|..#..|..#..|..#..|..#..|..#..",
        'U' to "#...#|#...#|#...#|#...#|#...#|#...#|.###.",
        'V' to "#...#|#...#|#...#|#...#|#...#|.#.#.|..#..",
        'W' to "#...#|#...#|#...#|#.#.#|#.#.#|#.#.#|.#.#.",
        'X' to "#...#|#...#|.#.#.|..#..|.#.#.|#...#|#...#",
        'Y' to "#...#|#...#|.#.#.|..#..|..#..|..#..|..#..",
        'Z' to "#####|....#|...#.|..#..|.#...|#....|#####",
        '0' to ".###.|#...#|#..##|#.#.#|##..#|#...#|.###.",
        '1' to ".#.|##.|.#.|.#.|.#.|.#.|###",
        '2' to ".###.|#...#|....#|...#.|..#..|.#...|#####",
        '3' to "#####|...#.|..#..|...#.|....#|#...#|.###.",
        '4' to "...#.|..##.|.#.#.|#..#.|#####|...#.|...#.",
        '5' to "#####|#....|####.|....#|....#|#...#|.###.",
        '6' to "..##.|.#...|#....|####.|#...#|#...#|.###.",
        '7' to "#####|....#|...#.|..#..|.#...|.#...|.#...",
        '8' to ".###.|#...#|#...#|.###.|#...#|#...#|.###.",
        '9' to ".###.|#...#|#...#|.####|....#|...#.|.##..",
        '.' to ".|.|.|.|.|.|#",
        ',' to "..|..|..|..|..|.#|#.",
        '!' to "#|#|#|#|#|.|#",
        '?' to ".###.|#...#|....#|...#.|..#..|.....|..#..",
        ':' to ".|#|.|.|.|#|.",
        '-' to "....|....|....|####|....|....|....",
        '+' to ".....|..#..|..#..|#####|..#..|..#..|.....",
        '/' to "....#|....#|...#.|..#..|.#...|#....|#....",
        '\'' to "#|#|.|.|.|.|.",
        '%' to "##..#|##..#|...#.|..#..|.#...|#..##|#..##",
        '(' to "..#|.#.|#..|#..|#..|.#.|..#",
        ')' to "#..|.#.|..#|..#|..#|.#.|#..",
        '*' to ".....|#.#.#|.###.|#####|.###.|#.#.#|.....",
        '#' to ".#.#.|#####|.#.#.|.#.#.|.#.#.|#####|.#.#.",
        '<' to "...#|..#.|.#..|#...|.#..|..#.|...#",
        '>' to "#...|.#..|..#.|...#|..#.|.#..|#...",
        '=' to "....|....|####|....|####|....|....",
        '_' to ".....|.....|.....|.....|.....|.....|#####",
        '&' to ".##..|#..#.|#.#..|.#...|#.#.#|#..#.|.##.#",
        '$' to "..#..|.####|#.#..|.###.|..#.#|####.|..#..",
        // Multiplication sign
        '×' to ".....|.....|#...#|.#.#.|..#..|.#.#.|#...#",
        // Infinity
        '∞' to ".......|.......|.##.##.|#..#..#|.##.##.|.......|.......",
    )

    private val order = GLYPHS.keys.toList()
    private val widths = HashMap<Char, Int>()
    private lateinit var atlas: Bitmap
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private var lastColor = 0

    private const val ATLAS_CELL_W = 8

    private fun ensure() {
        if (::atlas.isInitialized) return
        val px = IntArray(order.size * ATLAS_CELL_W * GLYPH_H)
        val aw = order.size * ATLAS_CELL_W
        order.forEachIndexed { i, ch ->
            val rows = GLYPHS[ch]!!.split('|')
            widths[ch] = rows.maxOf { it.length }
            rows.forEachIndexed { y, row ->
                row.forEachIndexed { x, c ->
                    if (c == '#') px[y * aw + i * ATLAS_CELL_W + x] = -1
                }
            }
        }
        atlas = Bitmap.createBitmap(px, aw, GLYPH_H, Bitmap.Config.ARGB_8888)
    }

    private fun norm(ch: Char) = if (ch in 'a'..'z') ch.uppercaseChar() else ch

    fun charWidth(ch: Char): Int {
        ensure()
        if (ch == ' ') return 3
        return widths[norm(ch)] ?: 5
    }

    fun width(text: String, scale: Int = 1): Float {
        if (text.isEmpty()) return 0f
        var w = 0
        for (ch in text) w += charWidth(ch) + 1
        return (w - 1) * scale.toFloat()
    }

    fun height(scale: Int = 1) = (GLYPH_H * scale).toFloat()

    /** Draws text at (x, y) = top-left. */
    fun draw(c: Canvas, text: String, x: Float, y: Float, color: Int, scale: Int = 1, alpha: Int = 255) {
        ensure()
        if (color != lastColor) {
            paint.colorFilter = PorterDuffColorFilter(color or 0xFF000000.toInt(), PorterDuff.Mode.SRC_IN)
            lastColor = color
        }
        paint.alpha = alpha * ((color ushr 24) and 0xFF) / 255
        var cx = x.toInt().toFloat()
        val cy = y.toInt().toFloat()
        for (raw in text) {
            if (raw == ' ') { cx += 4 * scale; continue }
            val ch = norm(raw)
            val idx = order.indexOf(ch)
            if (idx < 0) { cx += 6 * scale; continue }
            val w = widths[ch]!!
            Draw.region(c, atlas, idx * ATLAS_CELL_W, 0, w, GLYPH_H, cx, cy, scale.toFloat(), paint)
            cx += (w + 1) * scale
        }
    }

    /** Text with a 1px drop shadow, aligned by [align]: 0 = left, 1 = centre, 2 = right. */
    fun drawShadow(
        c: Canvas, text: String, x: Float, y: Float, color: Int, scale: Int = 1,
        align: Int = 0, shadow: Int = Pal.INK, alpha: Int = 255,
    ) {
        val w = width(text, scale)
        val sx = when (align) { 1 -> x - w / 2f; 2 -> x - w; else -> x }
        draw(c, text, sx + scale, y + scale, shadow, scale, alpha)
        draw(c, text, sx, y, color, scale, alpha)
    }

    /** Big title text extruded in pseudo-3D. */
    fun draw3D(c: Canvas, text: String, cx: Float, y: Float, face: Int, side: Int, scale: Int, depth: Int) {
        val w = width(text, scale)
        val x = cx - w / 2f
        for (d in depth downTo 1) draw(c, text, x + d, y + d, if (d == depth) Pal.INK else side, scale)
        draw(c, text, x - 1, y, Pal.INK, scale)
        draw(c, text, x, y - 1, Pal.INK, scale)
        draw(c, text, x, y, face, scale)
    }

    /** Word-wraps [text] into lines no wider than [maxWidth]. */
    fun wrap(text: String, maxWidth: Float, scale: Int = 1): List<String> {
        val lines = ArrayList<String>()
        for (para in text.split('\n')) {
            var line = ""
            for (word in para.split(' ')) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (width(candidate, scale) > maxWidth && line.isNotEmpty()) {
                    lines += line
                    line = word
                } else line = candidate
            }
            lines += line
        }
        return lines
    }
}
