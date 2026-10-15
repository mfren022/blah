package com.topplecorp.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import com.topplecorp.game.audio.Sound
import com.topplecorp.game.gfx.Draw
import com.topplecorp.game.gfx.Pal
import com.topplecorp.game.gfx.PixelFont
import kotlin.math.pow
import kotlin.math.sin

enum class TouchAction { DOWN, MOVE, UP, CANCEL }

data class Touch(val action: TouchAction, val x: Float, val y: Float)

object Ease {
    fun outCubic(t: Float) = 1f - (1f - t.coerceIn(0f, 1f)).pow(3)
    fun inOutCubic(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    }
    fun outBack(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        val c1 = 1.70158f; val c3 = c1 + 1
        return 1 + c3 * (x - 1).pow(3) + c1 * (x - 1).pow(2)
    }
    fun outElastic(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        if (x == 0f || x == 1f) return x
        return (2f.pow(-10 * x) * sin((x * 10 - 0.75f) * (2 * Math.PI / 3)).toFloat() + 1)
    }
    /** Frame-rate independent exponential approach. */
    fun approach(current: Float, target: Float, speed: Float, dt: Float): Float =
        current + (target - current) * (1f - kotlin.math.exp(-speed * dt))
}

/**
 * Chunky pixel button with a 3D lip that compresses when pressed.
 */
class Button(
    var x: Float, var y: Float, var w: Float, var h: Float,
    var label: String = "",
    var icon: Bitmap? = null,
    var color: Int = Pal.ORANGE,
    var textScale: Int = 1,
    var sound: Sound = Sound.CLICK,
    val onClick: () -> Unit,
) {
    var enabled = true
    var visible = true
    var selected = false
    var pressed = false
        private set
    private var press = 0f
    private var hover = 0f
    var badge: String? = null
    var iconScale = 1f
    var textColor = Pal.WHITE

    fun contains(px: Float, py: Float) = px >= x && px <= x + w && py >= y - 2 && py <= y + h + 4

    /** Returns true if the touch was consumed. */
    fun touch(t: Touch, play: (Sound) -> Unit): Boolean {
        if (!visible) return false
        when (t.action) {
            TouchAction.DOWN -> if (contains(t.x, t.y)) {
                if (!enabled) { play(Sound.DENY); return true }
                pressed = true; return true
            }
            TouchAction.MOVE -> if (pressed && !contains(t.x, t.y)) pressed = false
            TouchAction.UP -> if (pressed) {
                pressed = false
                if (contains(t.x, t.y)) { play(sound); onClick(); return true }
            }
            TouchAction.CANCEL -> pressed = false
        }
        return pressed
    }

    fun update(dt: Float) {
        press = Ease.approach(press, if (pressed) 1f else 0f, 30f, dt)
        hover = Ease.approach(hover, if (selected) 1f else 0f, 14f, dt)
    }

    fun draw(c: Canvas, alpha: Float = 1f) {
        if (!visible) return
        val depth = 4f
        val sink = press * (depth - 1) - hover * 2f
        val face = when {
            !enabled -> Pal.GRAY_DARK
            selected -> Pal.tint(color, 0.25f)
            else -> color
        }
        // Drop shadow on the floor
        Draw.rect(c, x + 2, y + h + depth - 1, w - 2, 2f, Pal.alpha(Pal.INK, 0.25f * alpha))
        Draw.panel(c, x, y + sink, w, h, face, depth - press * (depth - 1) + hover * 2f)
        val cy = y + sink
        var contentW = 0f
        val ic = icon
        val iconW = (ic?.width ?: 0) * iconScale
        val textW = if (label.isNotEmpty()) PixelFont.width(label, textScale) else 0f
        contentW = iconW + textW + if (ic != null && label.isNotEmpty()) 4f else 0f
        var cx = x + (w - contentW) / 2f
        if (ic != null) {
            Draw.bitmap(c, ic, cx.toInt().toFloat(), (cy + (h - ic.height * iconScale) / 2f).toInt().toFloat(), iconScale)
            cx += iconW + 4f
        }
        if (label.isNotEmpty()) {
            val tc = if (enabled) textColor else Pal.GRAY
            PixelFont.drawShadow(c, label, cx, cy + (h - PixelFont.height(textScale)) / 2f, tc, textScale,
                shadow = Pal.shade(face, 0.45f))
        }
        badge?.let {
            val bw = PixelFont.width(it) + 6
            Draw.panel(c, x + w - bw + 3, cy - 5, bw, 11f, Pal.RED, 1f)
            PixelFont.draw(c, it, x + w - bw + 6, cy - 3, Pal.WHITE)
        }
    }
}

/** Simple on/off toggle switch. */
class Toggle(var x: Float, var y: Float, var on: Boolean, val onChange: (Boolean) -> Unit) {
    val w = 30f; val h = 14f
    private var knob = if (on) 1f else 0f

    fun touch(t: Touch, play: (Sound) -> Unit): Boolean {
        if (t.action == TouchAction.UP && t.x in x - 4..x + w + 4 && t.y in y - 6..y + h + 6) {
            on = !on; play(Sound.SELECT); onChange(on); return true
        }
        return t.x in x - 4..x + w + 4 && t.y in y - 6..y + h + 6
    }

    fun update(dt: Float) { knob = Ease.approach(knob, if (on) 1f else 0f, 18f, dt) }

    fun draw(c: Canvas) {
        Draw.panel(c, x, y, w, h, Pal.mix(Pal.GRAY_DARK, Pal.GREEN, knob), 1f)
        val kx = x + 2 + knob * (w - h)
        Draw.panel(c, kx, y + 1, h - 3, h - 3, Pal.CREAM, 1f)
    }
}

/** Horizontal slider 0..1. */
class Slider(var x: Float, var y: Float, var w: Float, var value: Float, val onChange: (Float) -> Unit) {
    private var dragging = false

    fun touch(t: Touch): Boolean {
        val inside = t.x in x - 6..x + w + 6 && t.y in y - 10..y + 14
        when (t.action) {
            TouchAction.DOWN -> if (inside) dragging = true
            TouchAction.UP, TouchAction.CANCEL -> if (dragging) { dragging = false; onChange(value); return true }
            else -> {}
        }
        if (dragging) {
            value = ((t.x - x) / w).coerceIn(0f, 1f)
            return true
        }
        return false
    }

    fun draw(c: Canvas) {
        Draw.rect(c, x, y + 2, w, 4f, Pal.INK)
        Draw.rect(c, x + 1, y + 3, (w - 2) * value, 2f, Pal.GOLD)
        Draw.panel(c, x + w * value - 4, y - 2, 9f, 10f, Pal.CREAM, 2f)
    }
}
