package com.topplecorp.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin

/**
 * Modern UI kit: rounded translucent-black surfaces, sans-serif text and vector icons.
 *
 * UI is drawn in a separate full-resolution pass (see GameView), on a canvas pre-scaled so that all
 * coordinates here are in the same virtual units as the pixel-art world, but render smooth and sharp.
 */
object Ui {
    // Surfaces
    const val BG = 0x8C000000.toInt()          // 55% black
    const val BG_STRONG = 0xBF000000.toInt()   // 75% black
    const val BG_SOFT = 0x59000000.toInt()     // 35% black
    const val BORDER = 0x22FFFFFF
    const val RADIUS = 8f

    // Text colours
    const val TEXT = 0xFFFFFFFF.toInt()
    const val TEXT_DIM = 0xB3FFFFFF.toInt()
    const val TEXT_FAINT = 0x73FFFFFF

    // Accents
    const val ACCENT = 0xFFFFB23F.toInt()
    const val GOLD = 0xFFFFC93C.toInt()
    const val GREEN = 0xFF4CD964.toInt()
    const val RED = 0xFFFF5A5F.toInt()
    const val BLUE = 0xFF5AB4FF.toInt()
    const val PURPLE = 0xFFA78BFA.toInt()

    enum class Weight { REGULAR, MEDIUM, BOLD }
    enum class Align { LEFT, CENTER, RIGHT }

    private val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rect = RectF()
    private val path = Path()

    private fun typeface(w: Weight) = when (w) {
        Weight.REGULAR -> regular
        Weight.MEDIUM -> medium
        Weight.BOLD -> bold
    }

    fun withAlpha(color: Int, a: Float): Int {
        val base = (color ushr 24) and 0xFF
        return (color and 0x00FFFFFF) or (((base * a.coerceIn(0f, 1f)).toInt()) shl 24)
    }

    // ------------------------------------------------------------------ surfaces

    fun roundRect(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) {
        fill.color = color
        rect.set(x, y, x + w, y + h)
        c.drawRoundRect(rect, r, r, fill)
    }

    fun outline(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, color: Int, width: Float = 1f) {
        stroke.color = color
        stroke.strokeWidth = width
        val half = width / 2f
        rect.set(x + half, y + half, x + w - half, y + h - half)
        c.drawRoundRect(rect, r, r, stroke)
    }

    /** Standard translucent-black surface with a hairline border. */
    fun panel(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float = RADIUS, color: Int = BG, border: Int = BORDER) {
        roundRect(c, x, y, w, h, r, color)
        if (border != 0) outline(c, x, y, w, h, r, border, 0.5f)
    }

    fun circle(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fill.color = color
        c.drawCircle(cx, cy, r, fill)
    }

    fun dim(c: Canvas, w: Float, h: Float, alpha: Float) {
        fill.color = withAlpha(0xFF000000.toInt(), alpha)
        c.drawRect(0f, 0f, w, h, fill)
    }

    // ------------------------------------------------------------------ text

    private fun prep(size: Float, color: Int, weight: Weight, alpha: Float): Paint {
        textPaint.typeface = typeface(weight)
        textPaint.textSize = size
        textPaint.color = withAlpha(color, alpha)
        textPaint.letterSpacing = 0f
        return textPaint
    }

    fun width(s: String, size: Float, weight: Weight = Weight.MEDIUM): Float {
        prep(size, TEXT, weight, 1f)
        return textPaint.measureText(s)
    }

    /** Draws [s] vertically centred on [y]. */
    fun text(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int = TEXT,
        align: Align = Align.LEFT, weight: Weight = Weight.MEDIUM, alpha: Float = 1f, spacing: Float = 0f,
    ) {
        val p = prep(size, color, weight, alpha)
        p.letterSpacing = spacing
        val w = p.measureText(s)
        val sx = when (align) { Align.LEFT -> x; Align.CENTER -> x - w / 2f; Align.RIGHT -> x - w }
        c.drawText(s, sx, y + size * 0.36f, p)
    }

    /** Text with a soft drop shadow, for text drawn directly over the game world. */
    fun shadowText(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int = TEXT,
        align: Align = Align.LEFT, weight: Weight = Weight.BOLD, alpha: Float = 1f,
    ) {
        text(c, s, x + size * 0.06f, y + size * 0.08f, size, 0xFF000000.toInt(), align, weight, alpha * 0.55f)
        text(c, s, x, y, size, color, align, weight, alpha)
    }

    fun wrap(text: String, maxWidth: Float, size: Float, weight: Weight = Weight.REGULAR): List<String> {
        val lines = ArrayList<String>()
        for (para in text.split('\n')) {
            var line = ""
            for (word in para.split(' ')) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (width(candidate, size, weight) > maxWidth && line.isNotEmpty()) {
                    lines += line
                    line = word
                } else line = candidate
            }
            lines += line
        }
        return lines
    }

    // ------------------------------------------------------------------ small graphics

    fun star(c: Canvas, cx: Float, cy: Float, r: Float, filled: Boolean) {
        path.reset()
        for (i in 0 until 10) {
            val a = -Math.PI / 2 + i * Math.PI / 5
            val rr = if (i % 2 == 0) r else r * 0.45f
            val x = cx + (cos(a) * rr).toFloat()
            val y = cy + (sin(a) * rr).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        fill.color = if (filled) GOLD else 0x40FFFFFF
        c.drawPath(path, fill)
    }

    fun stars(c: Canvas, cx: Float, cy: Float, count: Int, r: Float, gap: Float = r * 0.5f) {
        val step = r * 2 + gap
        for (i in 0 until 3) star(c, cx + (i - 1) * step, cy, r, i < count)
    }

    fun coin(c: Canvas, cx: Float, cy: Float, r: Float) {
        circle(c, cx, cy, r, 0xFFE0A11B.toInt())
        circle(c, cx, cy, r * 0.78f, GOLD)
        circle(c, cx - r * 0.2f, cy - r * 0.2f, r * 0.22f, 0xFFFFF0B0.toInt())
    }
}

enum class Icon { BACK, PLAY, PAUSE, GEAR, CART, RETRY, LIST, SOUND, MUTE, LOCK, CHECK, CLOSE }

/** Vector icons drawn with paths, sized by [size] (bounding box) around a centre point. */
object Icons {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val path2 = Path()
    private val rect = RectF()

    fun draw(c: Canvas, icon: Icon, cx: Float, cy: Float, size: Float, color: Int) {
        val s = size
        fill.color = color
        stroke.color = color
        stroke.strokeWidth = s * 0.13f
        path.reset()
        when (icon) {
            Icon.BACK -> {
                path.moveTo(cx + s * 0.12f, cy - s * 0.32f)
                path.lineTo(cx - s * 0.2f, cy)
                path.lineTo(cx + s * 0.12f, cy + s * 0.32f)
                c.drawPath(path, stroke)
            }
            Icon.PLAY -> {
                path.moveTo(cx - s * 0.22f, cy - s * 0.32f)
                path.lineTo(cx + s * 0.32f, cy)
                path.lineTo(cx - s * 0.22f, cy + s * 0.32f)
                path.close()
                c.drawPath(path, fill)
            }
            Icon.PAUSE -> {
                val bw = s * 0.16f; val bh = s * 0.6f
                rect.set(cx - s * 0.22f, cy - bh / 2, cx - s * 0.22f + bw, cy + bh / 2)
                c.drawRoundRect(rect, bw / 3, bw / 3, fill)
                rect.set(cx + s * 0.06f, cy - bh / 2, cx + s * 0.06f + bw, cy + bh / 2)
                c.drawRoundRect(rect, bw / 3, bw / 3, fill)
            }
            Icon.GEAR -> {
                path.addCircle(cx, cy, s * 0.3f, Path.Direction.CW)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4
                    val tx = cx + (cos(a) * s * 0.36f).toFloat()
                    val ty = cy + (sin(a) * s * 0.36f).toFloat()
                    path2.reset()
                    rect.set(tx - s * 0.09f, ty - s * 0.09f, tx + s * 0.09f, ty + s * 0.09f)
                    path2.addRoundRect(rect, s * 0.03f, s * 0.03f, Path.Direction.CW)
                    path.op(path2, Path.Op.UNION)
                }
                path2.reset()
                path2.addCircle(cx, cy, s * 0.13f, Path.Direction.CW)
                path.op(path2, Path.Op.DIFFERENCE)
                c.drawPath(path, fill)
            }
            Icon.CART -> {
                stroke.strokeWidth = s * 0.1f
                path.moveTo(cx - s * 0.42f, cy - s * 0.3f)
                path.lineTo(cx - s * 0.28f, cy - s * 0.3f)
                path.lineTo(cx - s * 0.16f, cy + s * 0.14f)
                path.lineTo(cx + s * 0.3f, cy + s * 0.14f)
                path.lineTo(cx + s * 0.4f, cy - s * 0.16f)
                path.lineTo(cx - s * 0.23f, cy - s * 0.16f)
                c.drawPath(path, stroke)
                c.drawCircle(cx - s * 0.1f, cy + s * 0.32f, s * 0.07f, fill)
                c.drawCircle(cx + s * 0.24f, cy + s * 0.32f, s * 0.07f, fill)
            }
            Icon.RETRY -> {
                rect.set(cx - s * 0.3f, cy - s * 0.3f, cx + s * 0.3f, cy + s * 0.3f)
                path.addArc(rect, -60f, 300f)
                c.drawPath(path, stroke)
                path.reset()
                val ax = cx + s * 0.15f; val ay = cy - s * 0.26f
                path.moveTo(ax + s * 0.2f, ay - s * 0.14f)
                path.lineTo(ax + s * 0.2f, ay + s * 0.12f)
                path.lineTo(ax - s * 0.06f, ay + s * 0.12f)
                path.close()
                c.drawPath(path, fill)
            }
            Icon.LIST -> {
                for (i in -1..1) {
                    val y = cy + i * s * 0.25f
                    c.drawCircle(cx - s * 0.3f, y, s * 0.06f, fill)
                    c.drawLine(cx - s * 0.12f, y, cx + s * 0.34f, y, stroke)
                }
            }
            Icon.SOUND, Icon.MUTE -> {
                path.moveTo(cx - s * 0.38f, cy - s * 0.12f)
                path.lineTo(cx - s * 0.2f, cy - s * 0.12f)
                path.lineTo(cx + s * 0.02f, cy - s * 0.32f)
                path.lineTo(cx + s * 0.02f, cy + s * 0.32f)
                path.lineTo(cx - s * 0.2f, cy + s * 0.12f)
                path.lineTo(cx - s * 0.38f, cy + s * 0.12f)
                path.close()
                c.drawPath(path, fill)
                stroke.strokeWidth = s * 0.08f
                if (icon == Icon.SOUND) {
                    rect.set(cx - s * 0.1f, cy - s * 0.2f, cx + s * 0.28f, cy + s * 0.2f)
                    c.drawArc(rect, -50f, 100f, false, stroke)
                    rect.set(cx - s * 0.12f, cy - s * 0.36f, cx + s * 0.44f, cy + s * 0.36f)
                    c.drawArc(rect, -50f, 100f, false, stroke)
                } else {
                    c.drawLine(cx + s * 0.16f, cy - s * 0.14f, cx + s * 0.42f, cy + s * 0.14f, stroke)
                    c.drawLine(cx + s * 0.16f, cy + s * 0.14f, cx + s * 0.42f, cy - s * 0.14f, stroke)
                }
            }
            Icon.LOCK -> {
                stroke.strokeWidth = s * 0.1f
                rect.set(cx - s * 0.18f, cy - s * 0.4f, cx + s * 0.18f, cy + s * 0.05f)
                c.drawArc(rect, 180f, 180f, false, stroke)
                c.drawLine(cx - s * 0.18f, cy - s * 0.18f, cx - s * 0.18f, cy, stroke)
                c.drawLine(cx + s * 0.18f, cy - s * 0.18f, cx + s * 0.18f, cy, stroke)
                rect.set(cx - s * 0.3f, cy - s * 0.04f, cx + s * 0.3f, cy + s * 0.38f)
                c.drawRoundRect(rect, s * 0.07f, s * 0.07f, fill)
            }
            Icon.CHECK -> {
                path.moveTo(cx - s * 0.3f, cy)
                path.lineTo(cx - s * 0.08f, cy + s * 0.22f)
                path.lineTo(cx + s * 0.32f, cy - s * 0.22f)
                c.drawPath(path, stroke)
            }
            Icon.CLOSE -> {
                c.drawLine(cx - s * 0.25f, cy - s * 0.25f, cx + s * 0.25f, cy + s * 0.25f, stroke)
                c.drawLine(cx - s * 0.25f, cy + s * 0.25f, cx + s * 0.25f, cy - s * 0.25f, stroke)
            }
        }
    }
}
