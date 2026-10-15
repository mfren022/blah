package com.topplecorp.game.gfx

import android.graphics.Canvas
import com.topplecorp.core.ToolType
import kotlin.math.abs
import kotlin.math.sin

enum class CrewMood { IDLE, WORK, CHEER, SCARED, SAD }

/** Draws the three-person demolition crew standing on the street. */
class CrewRenderer {
    var mood = CrewMood.IDLE
    private var moodTime = 0f
    /** Index of the crew member currently using a tool (for WORK). */
    var worker = 0
    var workerTool: ToolType? = null

    fun set(m: CrewMood, workerIndex: Int = worker) {
        if (m != mood || workerIndex != worker) moodTime = 0f
        mood = m
        worker = workerIndex
    }

    fun update(dt: Float) { moodTime += dt }

    /** Index of the crew member that operates [tool]. */
    fun workerFor(tool: ToolType): Int = when (tool) {
        ToolType.HAMMER, ToolType.WRECKING_BALL -> 0
        ToolType.DYNAMITE -> 1
        ToolType.DRILL, ToolType.LASER -> 2
    }

    fun draw(c: Canvas, centerX: Float, feetY: Float, time: Float, scale: Float = 1f) {
        val crew = Sprites.crew
        val spacing = 19f * scale
        crew.forEachIndexed { i, member ->
            val x = centerX + (i - 1) * spacing - 7 * scale
            val phase = i * 1.7f
            var bob = 0f
            var shakeX = 0f
            var armsUp = false
            var raiseRight = false
            when (mood) {
                CrewMood.IDLE -> bob = if (sin(time * 3f + phase) > 0.6f) -1f else 0f
                CrewMood.CHEER -> { bob = -abs(sin(time * 9f + phase)) * 6f; armsUp = true }
                CrewMood.SCARED -> { shakeX = sin(time * 50f + phase); armsUp = true }
                CrewMood.SAD -> bob = 1f
                CrewMood.WORK -> if (i == worker) { raiseRight = true; bob = if (moodTime % 0.3f < 0.15f) -1f else 0f }
            }
            val sx = (x + shakeX * scale).toInt().toFloat()
            val sy = (feetY - member.body.height * scale + bob * scale).toInt().toFloat()
            // Shadow
            Draw.rect(c, x + 2 * scale, feetY - scale, 10 * scale, 2f * scale, Pal.alpha(Pal.INK, 0.3f))
            val blink = ((time + phase * 3f) % 4f) < 0.12f
            Draw.bitmap(c, if (blink) member.blink else member.body, sx, sy, scale)
            drawArms(c, member.look, sx, sy, scale, armsUp, raiseRight, i == worker && mood == CrewMood.WORK)
        }
    }

    private fun drawArms(
        c: Canvas, look: Sprites.CrewLook, x: Float, y: Float, s: Float,
        up: Boolean, raiseRight: Boolean, holdTool: Boolean,
    ) {
        val sleeve = look.shirt
        val skin = look.skin
        fun arm(ax: Float, upArm: Boolean) {
            if (upArm) {
                Draw.rect(c, x + ax * s, y + 6 * s, 2 * s, 4 * s, sleeve)
                Draw.rect(c, x + ax * s, y + 4 * s, 2 * s, 2 * s, skin)
            } else {
                Draw.rect(c, x + ax * s, y + 10 * s, 2 * s, 3 * s, sleeve)
                Draw.rect(c, x + ax * s, y + 13 * s, 2 * s, 1 * s, skin)
            }
        }
        arm(1f, up)
        arm(11f, up || raiseRight)
        if (holdTool) {
            workerTool?.let { tool ->
                val icon = Sprites.tools[tool] ?: return
                val swing = if (moodTime % 0.3f < 0.15f) -2f else 0f
                Draw.bitmap(c, icon, x + 9 * s, y + (-6 + swing) * s, (s - 1f).coerceAtLeast(1f))
            }
        }
    }
}
