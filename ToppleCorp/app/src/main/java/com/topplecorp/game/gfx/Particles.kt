package com.topplecorp.game.gfx

import android.graphics.Canvas
import kotlin.random.Random

/**
 * Lightweight particle system with a fake depth axis: particles flying "towards" the camera grow and
 * cast a shadow on the street, which sells the 3D feel of debris.
 */
class Particles {
    class P {
        var x = 0f; var y = 0f; var z = 0f
        var vx = 0f; var vy = 0f; var vz = 0f
        var color = 0; var size = 2f
        var life = 0f; var maxLife = 1f
        var gravity = 300f
        var bounce = true
        var text: String? = null
        var spin = 0f
    }

    private val list = ArrayList<P>()
    private val rnd = Random(7)

    /** World y (screen space, relative to camera) of the street surface. Set each frame. */
    var groundY = Float.MAX_VALUE

    val isEmpty get() = list.isEmpty()

    fun clear() = list.clear()

    fun debris(x: Float, y: Float, color: Int, count: Int, power: Float = 1f) {
        repeat(count) {
            list += P().apply {
                this.x = x + rnd.nextFloat() * 6 - 3
                this.y = y + rnd.nextFloat() * 6 - 3
                vx = (rnd.nextFloat() - 0.5f) * 140f * power
                vy = (-rnd.nextFloat() * 160f - 40f) * power
                vz = (rnd.nextFloat() - 0.3f) * 40f * power
                this.color = Pal.shade(color, 0.75f + rnd.nextFloat() * 0.4f)
                size = (1 + rnd.nextInt(3)).toFloat()
                maxLife = 0.9f + rnd.nextFloat() * 0.8f
                life = maxLife
            }
        }
    }

    fun sparks(x: Float, y: Float, color: Int, count: Int, speed: Float = 120f) {
        repeat(count) {
            list += P().apply {
                this.x = x; this.y = y
                val a = rnd.nextFloat() * 6.283f
                val s = speed * (0.4f + rnd.nextFloat() * 0.6f)
                vx = kotlin.math.cos(a) * s
                vy = kotlin.math.sin(a) * s
                this.color = color
                size = 1f
                gravity = 60f
                bounce = false
                maxLife = 0.25f + rnd.nextFloat() * 0.3f
                life = maxLife
            }
        }
    }

    fun dust(x: Float, y: Float, w: Float, color: Int) {
        repeat((w / 3).toInt().coerceAtLeast(3)) {
            list += P().apply {
                this.x = x + rnd.nextFloat() * w
                this.y = y
                vx = (rnd.nextFloat() - 0.5f) * 50f
                vy = -rnd.nextFloat() * 20f
                this.color = Pal.alpha(color, 0.8f)
                size = (2 + rnd.nextInt(3)).toFloat()
                gravity = -10f
                bounce = false
                maxLife = 0.5f + rnd.nextFloat() * 0.4f
                life = maxLife
            }
        }
    }

    fun water(x: Float, y: Float, count: Int) {
        repeat(count) {
            list += P().apply {
                this.x = x + rnd.nextFloat() * 8 - 4
                this.y = y
                vx = (rnd.nextFloat() - 0.5f) * 40f
                vy = rnd.nextFloat() * 60f
                color = if (rnd.nextBoolean()) 0xFF6FC8FF.toInt() else 0xFFBFE8FF.toInt()
                size = (1 + rnd.nextInt(2)).toFloat()
                gravity = 420f
                maxLife = 0.8f + rnd.nextFloat() * 0.4f
                life = maxLife
            }
        }
    }

    fun floatText(x: Float, y: Float, text: String, color: Int) {
        list += P().apply {
            this.x = x; this.y = y
            vy = -38f
            gravity = 50f
            bounce = false
            this.color = color
            this.text = text
            maxLife = 0.9f
            life = maxLife
        }
    }

    fun update(dt: Float) {
        val it = list.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.vy += p.gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt
            if (p.bounce && p.y > groundY - p.size) {
                p.y = groundY - p.size
                p.vy *= -0.35f
                p.vx *= 0.6f
                p.vz *= 0.5f
            }
        }
    }

    /** Draw with an extra y [offset] (camera). */
    fun draw(c: Canvas, offset: Float) {
        for (p in list) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            val t = p.text
            if (t != null) {
                PixelFont.drawShadow(c, t, p.x, p.y + offset, p.color, 1, align = 1, alpha = (255 * a.coerceAtMost(1f)).toInt())
                continue
            }
            // Depth: particles closer to the camera (z > 0) look bigger.
            val depth = 1f + (p.z / 60f).coerceIn(-0.5f, 1.5f)
            val s = (p.size * depth).coerceAtLeast(1f)
            if (p.bounce && groundY < 10000f) {
                val shadowY = groundY + 3 + p.z * 0.15f + offset
                Draw.rect(c, p.x - s / 2, shadowY, s, 1f, Pal.alpha(Pal.INK, 0.25f * a))
            }
            Draw.rect(c, p.x - s / 2, p.y - s / 2 + offset, s, s, Pal.alpha(p.color, (a * 2f).coerceAtMost(1f)))
        }
    }
}
