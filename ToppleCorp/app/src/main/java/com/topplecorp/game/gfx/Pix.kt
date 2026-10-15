package com.topplecorp.game.gfx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
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
