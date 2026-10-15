package com.topplecorp.game.scene

import android.graphics.Canvas
import com.topplecorp.game.Game
import com.topplecorp.game.audio.Sound
import com.topplecorp.game.gfx.Backdrop
import com.topplecorp.game.gfx.Draw
import com.topplecorp.game.gfx.Sprites
import com.topplecorp.game.gfx.Themes
import com.topplecorp.game.ui.Button
import com.topplecorp.game.ui.Slider
import com.topplecorp.game.ui.Toggle
import com.topplecorp.game.ui.Touch
import com.topplecorp.game.ui.Ui
import com.topplecorp.game.ui.UiKit

/** Sound, vibration, hints and progress reset. */
class SettingsScene(game: Game) : Scene(game) {
    private val backdrop = Backdrop(Themes.of(2), game.vw, 9)
    private val toggles = ArrayList<Pair<String, Toggle>>()
    private lateinit var volume: Slider
    private var confirmReset = 0f
    private var panelY = 60f
    private val rowH = 34f

    override fun layout() {
        buttons.clear()
        toggles.clear()
        buttons += UiKit.backButton { onBack() }
        panelY = UiKit.HEADER_H + 14f
        val tx = w - 22f - 34f
        val save = game.save
        fun rowCenter(i: Int) = panelY + rowH * i + rowH / 2f
        toggles += "Sound effects" to Toggle(tx, rowCenter(0) - 10f, save.soundOn) { on ->
            save.soundOn = on; game.sfx.enabled = on
        }
        volume = Slider(w / 2f - 10f, rowCenter(1), w / 2f - 22f, save.volume) { v ->
            save.volume = v; game.sfx.volume = v; game.play(Sound.COIN, 1f)
        }
        toggles += "Vibration" to Toggle(tx, rowCenter(2) - 10f, save.vibration) { on ->
            save.vibration = on; if (on) game.vibrate(40)
        }
        toggles += "Tutorial hints" to Toggle(tx, rowCenter(3) - 10f, save.showHints) { on -> save.showHints = on }

        val bw = w - 24f
        val by = panelY + rowH * 4 + 18f
        buttons += Button(12f, by, bw, 36f, "Level editor · coming soon", textSize = 10f) {}.apply {
            enabled = false; radius = 12f
        }
        buttons += Button(12f, by + 46f, bw, 36f, "Reset progress", color = Ui.RED, textSize = 11f) { reset() }.apply {
            radius = 12f; primary = true
        }
    }

    private fun reset() {
        if (confirmReset > 0f) {
            game.save.resetProgress()
            confirmReset = 0f
            buttons[2].label = "Progress reset"
            game.play(Sound.EXPLOSION, 0.6f)
            game.vibrate(80, 200)
        } else {
            confirmReset = 3f
            buttons[2].label = "Tap again to confirm"
        }
    }

    override fun onBack() = game.goTo { MenuScene(game) }

    override fun update(dt: Float) {
        super.update(dt)
        backdrop.update(dt)
        toggles.forEach { it.second.update(dt) }
        if (confirmReset > 0f) {
            confirmReset -= dt
            if (confirmReset <= 0f && buttons[2].label.startsWith("Tap")) buttons[2].label = "Reset progress"
        }
    }

    override fun touch(t: Touch) {
        if (volume.touch(t)) return
        for ((_, tg) in toggles) if (tg.touch(t) { s: Sound -> game.play(s) }) return
        handleButtons(t)
    }

    override fun draw(c: Canvas) {
        backdrop.draw(c, h, h * 0.85f, 0f)
        val fy = h - 50f
        Sprites.crew.forEachIndexed { i, m -> Draw.bitmap(c, m.body, w / 2f - 30 + i * 20, fy - 30) }
    }

    override fun drawUi(c: Canvas) {
        UiKit.header(c, w, "Settings")
        Ui.panel(c, 12f, panelY, w - 24f, rowH * 4, 12f)
        val labels = listOf(toggles[0].first, "Volume", toggles[1].first, toggles[2].first)
        for ((i, label) in labels.withIndex()) {
            val cy = panelY + rowH * i + rowH / 2f
            Ui.text(c, label, 24f, cy, 10f, Ui.TEXT)
            if (i > 0) Ui.roundRect(c, 24f, panelY + rowH * i, w - 48f, 0.5f, 0f, 0x26FFFFFF)
        }
        volume.draw(c)
        toggles.forEach { it.second.draw(c) }
        drawButtons(c)

        val fy = h - 50f
        Ui.text(c, "Topple Corp v1.0", w / 2f, fy + 4, 9f, Ui.TEXT_DIM, Ui.Align.CENTER)
        Ui.text(c, "All art and sound made in code", w / 2f, fy + 18, 8f, Ui.TEXT_FAINT, Ui.Align.CENTER)
    }
}
