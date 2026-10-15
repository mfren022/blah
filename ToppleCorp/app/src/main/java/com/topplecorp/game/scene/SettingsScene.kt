package com.topplecorp.game.scene

import android.graphics.Canvas
import com.topplecorp.game.Game
import com.topplecorp.game.audio.Sound
import com.topplecorp.game.gfx.Backdrop
import com.topplecorp.game.gfx.Draw
import com.topplecorp.game.gfx.Pal
import com.topplecorp.game.gfx.PixelFont
import com.topplecorp.game.gfx.Sprites
import com.topplecorp.game.gfx.Themes
import com.topplecorp.game.ui.Button
import com.topplecorp.game.ui.Slider
import com.topplecorp.game.ui.Toggle
import com.topplecorp.game.ui.Touch
import com.topplecorp.game.ui.UiKit

/** Sound, vibration, hints and progress reset. */
class SettingsScene(game: Game) : Scene(game) {
    private val backdrop = Backdrop(Themes.of(2), game.vw, 9)
    private val toggles = ArrayList<Pair<String, Toggle>>()
    private lateinit var volume: Slider
    private var confirmReset = 0f
    private var rowX = 20f
    private var rowY = 60f

    override fun layout() {
        buttons.clear()
        toggles.clear()
        buttons += UiKit.backButton { onBack() }
        rowX = 20f
        rowY = 66f
        val tx = w - 22f - 40f
        val save = game.save
        toggles += "SOUND EFFECTS" to Toggle(tx, rowY + 8, save.soundOn) { on ->
            save.soundOn = on; game.sfx.enabled = on
        }
        volume = Slider(rowX + 10, rowY + 54, w - 60f, save.volume) { v ->
            save.volume = v; game.sfx.volume = v; game.play(Sound.COIN, 1f)
        }
        toggles += "VIBRATION" to Toggle(tx, rowY + 80, save.vibration) { on ->
            save.vibration = on; if (on) game.vibrate(40)
        }
        toggles += "TUTORIAL HINTS" to Toggle(tx, rowY + 112, save.showHints) { on -> save.showHints = on }

        val bw = w - 40f
        buttons += Button(20f, rowY + 156, bw, 32f, "LEVEL EDITOR - COMING SOON", color = Pal.PANEL_LIGHT) {}.apply {
            enabled = false
        }
        buttons += Button(20f, rowY + 202, bw, 32f, "RESET PROGRESS", color = Pal.RED, textScale = 2) { reset() }
    }

    private fun reset() {
        if (confirmReset > 0f) {
            game.save.resetProgress()
            confirmReset = 0f
            buttons[2].label = "RESET DONE"
            game.play(Sound.EXPLOSION, 0.6f)
            game.vibrate(80, 200)
        } else {
            confirmReset = 3f
            buttons[2].label = "TAP TO CONFIRM"
        }
    }

    override fun onBack() = game.goTo { MenuScene(game) }

    override fun update(dt: Float) {
        super.update(dt)
        backdrop.update(dt)
        toggles.forEach { it.second.update(dt) }
        if (confirmReset > 0f) {
            confirmReset -= dt
            if (confirmReset <= 0f && buttons[2].label.startsWith("TAP")) buttons[2].label = "RESET PROGRESS"
        }
    }

    override fun touch(t: Touch) {
        if (volume.touch(t)) return
        for ((_, tg) in toggles) if (tg.touch(t) { s: Sound -> game.play(s) }) return
        handleButtons(t)
    }

    override fun draw(c: Canvas) {
        backdrop.draw(c, h, h * 0.85f, 0f)
        Draw.dim(c, w, h, 0.5f)
        UiKit.header(c, w, "SETTINGS")
        Draw.panel(c, 10f, rowY - 8, w - 20f, 146f, Pal.PANEL, 3f)
        val labels = toggles.map { it.first }
        PixelFont.drawShadow(c, labels[0], rowX + 8, rowY + 12, Pal.CREAM)
        PixelFont.drawShadow(c, "VOLUME", rowX + 8, rowY + 38, Pal.CREAM)
        PixelFont.drawShadow(c, "${(volume.value * 100).toInt()}%", w - 30f, rowY + 38, Pal.GOLD, align = 2)
        PixelFont.drawShadow(c, labels[1], rowX + 8, rowY + 84, Pal.CREAM)
        PixelFont.drawShadow(c, labels[2], rowX + 8, rowY + 116, Pal.CREAM)
        volume.draw(c)
        toggles.forEach { it.second.draw(c) }
        drawButtons(c)

        val fy = h - 50f
        PixelFont.drawShadow(c, "TOPPLE CORP V1.0", w / 2f, fy, Pal.alpha(Pal.WHITE, 0.7f), align = 1)
        PixelFont.drawShadow(c, "ALL ART AND SOUND MADE IN CODE", w / 2f, fy + 12, Pal.alpha(Pal.WHITE, 0.5f), align = 1)
        Sprites.crew.forEachIndexed { i, m -> Draw.bitmap(c, m.body, w / 2f - 30 + i * 20, fy - 32) }
    }
}
