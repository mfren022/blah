package com.topplecorp.game.gfx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF

/** Shared colour palette (ARGB). */
object Pal {
    const val INK = 0xFF1B1726.toInt()
    const val INK_SOFT = 0xFF2E2840.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val CREAM = 0xFFFFF4DC.toInt()
    const val GOLD = 0xFFFFC93C.toInt()
    const val GOLD_DARK = 0xFFC7861C.toInt()
    const val ORANGE = 0xFFFF8A3D.toInt()
    const val ORANGE_DARK = 0xFFC2551E.toInt()
    const val RED = 0xFFE8484F.toInt()
    const val RED_DARK = 0xFF9E2A3A.toInt()
    const val GREEN = 0xFF5BD16B.toInt()
    const val GREEN_DARK = 0xFF2E8F4C.toInt()
    const val BLUE = 0xFF4BA3F2.toInt()
    const val BLUE_DARK = 0xFF2A5FB0.toInt()
    const val TEAL = 0xFF39C9B5.toInt()
    const val PURPLE = 0xFF8A5CF0.toInt()
    const val PURPLE_DARK = 0xFF573399.toInt()
    const val GRAY = 0xFF8C8FA8.toInt()
    const val GRAY_DARK = 0xFF54566E.toInt()
    const val PANEL = 0xFF3A3352.toInt()
    const val PANEL_LIGHT = 0xFF544A75.toInt()
    const val PANEL_DARK = 0xFF231E33.toInt()

    fun shade(c: Int, f: Float): Int {
        val a = Color.alpha(c)
        val r = (Color.red(c) * f).toInt().coerceIn(0, 255)
        val g = (Color.green(c) * f).toInt().coerceIn(0, 255)
        val b = (Color.blue(c) * f).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    fun tint(c: Int, f: Float): Int {
        val a = Color.alpha(c)
        val r = (Color.red(c) + (255 - Color.red(c)) * f).toInt().coerceIn(0, 255)
        val g = (Color.green(c) + (255 - Color.green(c)) * f).toInt().coerceIn(0, 255)
        val b = (Color.blue(c) + (255 - Color.blue(c)) * f).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    fun mix(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * u).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * u).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * u).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u).toInt(),
        )
    }

    fun alpha(c: Int, a: Float): Int = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)
}

/** Builds pixel-art bitmaps from string art. '.' and ' ' are transparent. */
object Sprite {
    fun build(rows: List<String>, palette: Map<Char, Int>): Bitmap {
        val w = rows.maxOf { it.length }
        val h = rows.size
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in rows[y].indices) {
            val ch = rows[y][x]
            px[y * w + x] = palette[ch] ?: 0
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    fun fromPixels(px: IntArray, w: Int, h: Int): Bitmap = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

/** Drawing helpers that keep everything crisp in the low-resolution frame buffer. */
object Draw {
    val fill = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    val bmp = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val tinted = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val src = Rect()
    private val dst = RectF()

    fun rect(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        fill.color = color
        c.drawRect(x, y, x + w, y + h, fill)
    }

    fun bitmap(c: Canvas, b: Bitmap, x: Float, y: Float, scale: Float = 1f, alpha: Int = 255, flip: Boolean = false) {
        bmp.alpha = alpha
        dst.set(x, y, x + b.width * scale, y + b.height * scale)
        if (flip) {
            c.save()
            c.scale(-1f, 1f, x + b.width * scale / 2f, y)
            c.drawBitmap(b, null, dst, bmp)
            c.restore()
        } else c.drawBitmap(b, null, dst, bmp)
        bmp.alpha = 255
    }

    fun bitmapRect(c: Canvas, b: Bitmap, x: Float, y: Float, w: Float, h: Float, alpha: Int = 255) {
        bmp.alpha = alpha
        dst.set(x, y, x + w, y + h)
        c.drawBitmap(b, null, dst, bmp)
        bmp.alpha = 255
    }

    /** Draws [b] recoloured to a single [color] (used for silhouettes and shadows). */
    fun silhouette(c: Canvas, b: Bitmap, x: Float, y: Float, scale: Float, color: Int) {
        tinted.colorFilter = PorterDuffColorFilter(color or 0xFF000000.toInt(), PorterDuff.Mode.SRC_IN)
        tinted.alpha = Color.alpha(color)
        dst.set(x, y, x + b.width * scale, y + b.height * scale)
        c.drawBitmap(b, null, dst, tinted)
    }

    fun region(c: Canvas, b: Bitmap, sx: Int, sy: Int, sw: Int, sh: Int, x: Float, y: Float, scale: Float, paint: Paint) {
        src.set(sx, sy, sx + sw, sy + sh)
        dst.set(x, y, x + sw * scale, y + sh * scale)
        c.drawBitmap(b, src, dst, paint)
    }

    /**
     * A chunky pixel panel with cut corners, a light top edge and a 3D "depth" lip at the bottom.
     */
    fun panel(c: Canvas, x: Float, y: Float, w: Float, h: Float, face: Int, depth: Float = 3f, border: Int = Pal.INK) {
        val x0 = x.toInt().toFloat(); val y0 = y.toInt().toFloat()
        val ww = w.toInt().toFloat(); val hh = h.toInt().toFloat()
        // Outline
        rect(c, x0 + 1, y0, ww - 2, hh + depth, border)
        rect(c, x0, y0 + 1, ww, hh + depth - 2, border)
        // Depth lip
        if (depth > 0) rect(c, x0 + 1, y0 + 1, ww - 2, hh + depth - 2, Pal.shade(face, 0.6f))
        // Face
        rect(c, x0 + 1, y0 + 1, ww - 2, hh - 2, face)
        // Highlight and inner shade
        rect(c, x0 + 2, y0 + 1, ww - 4, 1f, Pal.tint(face, 0.35f))
        rect(c, x0 + 1, y0 + 2, 1f, hh - 4, Pal.tint(face, 0.15f))
        rect(c, x0 + 2, y0 + hh - 2, ww - 4, 1f, Pal.shade(face, 0.85f))
    }

    /**
     * Chunky square block used for buttons: thick outline, deep 3D lip under the face, and
     * two-pixel bevels. [depth] is the visible lip height (shrinks when the button is pressed).
     */
    fun block(c: Canvas, x: Float, y: Float, w: Float, h: Float, face: Int, depth: Float = 6f) {
        val x0 = x.toInt().toFloat(); val y0 = y.toInt().toFloat()
        val ww = w.toInt().toFloat(); val hh = h.toInt().toFloat()
        val d = depth.toInt().coerceAtLeast(0).toFloat()
        // Outline around face + lip
        rect(c, x0, y0, ww, hh + d, Pal.INK)
        // Lip (the block's front side)
        if (d > 0) {
            rect(c, x0 + 2, y0 + hh - 2, ww - 4, d, Pal.shade(face, 0.55f))
            rect(c, x0 + 2, y0 + hh - 2, ww - 4, 1f, Pal.shade(face, 0.4f))
            // Vertical seams on the lip sell the "block" look
            rect(c, x0 + 2, y0 + hh - 1, 2f, d - 1, Pal.shade(face, 0.68f))
        }
        // Face
        rect(c, x0 + 2, y0 + 2, ww - 4, hh - 4, face)
        // Bevels
        rect(c, x0 + 2, y0 + 2, ww - 4, 2f, Pal.tint(face, 0.4f))
        rect(c, x0 + 2, y0 + 4, 2f, hh - 8, Pal.tint(face, 0.2f))
        rect(c, x0 + ww - 4, y0 + 4, 2f, hh - 8, Pal.shade(face, 0.85f))
        rect(c, x0 + 2, y0 + hh - 4, ww - 4, 2f, Pal.shade(face, 0.78f))
        // Corner pixels on the face for a chiselled look
        rect(c, x0 + 2, y0 + 2, 1f, 1f, Pal.INK)
        rect(c, x0 + ww - 3, y0 + 2, 1f, 1f, Pal.INK)
    }

    /** Vertical gradient using horizontal bands (dithered look). */
    fun gradient(c: Canvas, x: Float, y: Float, w: Float, h: Float, top: Int, bottom: Int, bands: Int = 24) {
        val bh = h / bands
        for (i in 0 until bands) {
            rect(c, x, y + i * bh, w, bh + 1, Pal.mix(top, bottom, i / (bands - 1f)))
        }
    }

    fun dim(c: Canvas, w: Float, h: Float, alpha: Float) {
        rect(c, 0f, 0f, w, h, Pal.alpha(Pal.INK, alpha))
    }
}
