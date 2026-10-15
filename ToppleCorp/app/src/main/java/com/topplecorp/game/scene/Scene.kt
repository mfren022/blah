package com.topplecorp.game.scene

import android.graphics.Canvas
import com.topplecorp.game.Game
import com.topplecorp.game.audio.Sound
import com.topplecorp.game.ui.Button
import com.topplecorp.game.ui.Touch

/** Base class for screens. Coordinates are in virtual pixels. */
abstract class Scene(val game: Game) {
    protected val buttons = ArrayList<Button>()
    protected var time = 0f
    val w get() = game.vw.toFloat()
    val h get() = game.vh.toFloat()

    /** Called on creation and whenever the virtual screen size changes. */
    open fun layout() {}
    open fun enter() {}
    open fun exit() {}
    open fun onBack() {}

    open fun update(dt: Float) {
        time += dt
        buttons.forEach { it.update(dt) }
    }

    abstract fun draw(c: Canvas)

    open fun touch(t: Touch) {
        handleButtons(t)
    }

    protected fun handleButtons(t: Touch): Boolean {
        for (b in buttons.toList()) if (b.touch(t) { s: Sound -> game.play(s) }) return true
        return false
    }

    protected fun drawButtons(c: Canvas) = buttons.forEach { it.draw(c) }
}
