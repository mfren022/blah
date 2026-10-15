package com.topplecorp.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import com.topplecorp.game.audio.Sound
import com.topplecorp.game.gfx.Draw
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
 * Rounded translucent-black button with a sans-serif label and an optional vector [icon]
 * (tinted with [color]) or pixel-art [sprite].
 */
class Button(
    var x: Float, var y: Float, var w: Float, var h: Float,
    var label: String = "",
    var icon: Icon? = null,
    var color: Int = Ui.TEXT,
    var textSize: Float = 10f,
    var sound: Sound = Sound.CLICK,
    val onClick: () -> Unit,
) {
    var enabled = true
    var visible = true
    var selected = false
    /** Primary buttons get an accent outline. */
    var primary = false
    var pressed = false
        private set
    private var press = 0f
    private var hover = 0f
    var badge: String? = null
    var sprite: Bitmap? = null
    var spriteScale = 2f
    var iconSize = 0f // 0 = automatic
    var radius = 0f   // 0 = automatic

    fun contains(px: Float, py: Float) = px >= x - 2 && px <= x + w + 2 && py >= y - 2 && py <= y + h + 2

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
        val cx = x + w / 2f
        val cy = y + h / 2f
        val r = if (radius > 0f) radius else minOf(h / 2f, 9f)
        c.save()
        val s = 1f - press * 0.05f
        c.scale(s, s, cx, cy)

        val bg = when {
            !enabled -> Ui.withAlpha(Ui.BG_SOFT, alpha)
            else -> Ui.withAlpha(if (selected) Ui.BG_STRONG else Ui.BG, alpha)
        }
        Ui.roundRect(c, x, y, w, h, r, bg)
        if (press > 0.01f) Ui.roundRect(c, x, y, w, h, r, Ui.withAlpha(0x26FFFFFF, press))
        when {
            selected -> Ui.outline(c, x, y, w, h, r, Ui.withAlpha(color, hover * alpha), 1.5f)
            primary -> Ui.outline(c, x, y, w, h, r, Ui.withAlpha(color, 0.9f * alpha), 1.5f)
            else -> Ui.outline(c, x, y, w, h, r, Ui.withAlpha(Ui.BORDER, alpha), 0.5f)
        }

        val contentAlpha = alpha * (if (enabled) 1f else 0.4f)
        val ic = icon
        val sp = sprite
        val iconW = when {
            ic != null -> if (iconSize > 0f) iconSize else minOf(h * 0.5f, 16f)
            sp != null -> sp.width * spriteScale
            else -> 0f
        }
        val textW = if (label.isNotEmpty()) Ui.width(label, textSize) else 0f
        val gap = if (iconW > 0f && textW > 0f) textSize * 0.5f else 0f
        var lx = cx - (iconW + gap + textW) / 2f
        if (ic != null) {
            val iconColor = if (label.isEmpty() || primary) color else Ui.TEXT
            Icons.draw(c, ic, lx + iconW / 2f, cy, iconW, Ui.withAlpha(iconColor, contentAlpha))
            lx += iconW + gap
        } else if (sp != null) {
            Draw.bitmap(c, sp, lx, cy - sp.height * spriteScale / 2f, spriteScale, (255 * contentAlpha).toInt())
            lx += iconW + gap
        }
        if (label.isNotEmpty()) Ui.text(c, label, lx, cy, textSize, Ui.TEXT, alpha = contentAlpha)

        badge?.let {
            val bw = maxOf(Ui.width(it, 7f, Ui.Weight.BOLD) + 7f, 12f)
            Ui.roundRect(c, x + w - bw + 3, y - 4, bw, 12f, 6f, Ui.withAlpha(0xFF000000.toInt(), 0.85f * alpha))
            Ui.outline(c, x + w - bw + 3, y - 4, bw, 12f, 6f, Ui.withAlpha(color, alpha), 1f)
            Ui.text(c, it, x + w - bw / 2f + 3, y + 2, 7f, Ui.TEXT, Ui.Align.CENTER, Ui.Weight.BOLD, alpha)
        }
        c.restore()
    }
}

/** iOS-style on/off switch. */
class Toggle(var x: Float, var y: Float, var on: Boolean, val onChange: (Boolean) -> Unit) {
    val w = 34f; val h = 20f
    private var knob = if (on) 1f else 0f

    fun touch(t: Touch, play: (Sound) -> Unit): Boolean {
        val inside = t.x in x - 6..x + w + 6 && t.y in y - 6..y + h + 6
        if (t.action == TouchAction.UP && inside) {
            on = !on; play(Sound.SELECT); onChange(on); return true
        }
        return inside
    }

    fun update(dt: Float) { knob = Ease.approach(knob, if (on) 1f else 0f, 18f, dt) }

    fun draw(c: Canvas) {
        val track = if (knob > 0.5f) Ui.withAlpha(Ui.GREEN, 0.4f + knob * 0.6f) else Ui.withAlpha(0x59FFFFFF, 1f - knob)
        Ui.roundRect(c, x, y, w, h, h / 2f, track)
        val kr = h / 2f - 2f
        Ui.circle(c, x + h / 2f + knob * (w - h), y + h / 2f + 0.5f, kr, 0x33000000)
        Ui.circle(c, x + h / 2f + knob * (w - h), y + h / 2f, kr, Ui.TEXT)
    }
}

/** Horizontal slider 0..1 with a round knob. */
class Slider(var x: Float, var y: Float, var w: Float, var value: Float, val onChange: (Float) -> Unit) {
    private var dragging = false

    fun touch(t: Touch): Boolean {
        val inside = t.x in x - 8..x + w + 8 && t.y in y - 12..y + 12
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
        Ui.roundRect(c, x, y - 2f, w, 4f, 2f, 0x40FFFFFF)
        Ui.roundRect(c, x, y - 2f, w * value, 4f, 2f, Ui.ACCENT)
        Ui.circle(c, x + w * value, y + 0.5f, 7f, 0x33000000)
        Ui.circle(c, x + w * value, y, 7f, Ui.TEXT)
    }
}
