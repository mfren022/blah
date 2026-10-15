package com.topplecorp.game.scene

import android.graphics.Canvas
import com.topplecorp.core.Campaign
import com.topplecorp.game.Game
import com.topplecorp.game.audio.Sound
import com.topplecorp.game.gfx.Backdrop
import com.topplecorp.game.gfx.Themes
import com.topplecorp.game.ui.Ease
import com.topplecorp.game.ui.Icon
import com.topplecorp.game.ui.Icons
import com.topplecorp.game.ui.Touch
import com.topplecorp.game.ui.TouchAction
import com.topplecorp.game.ui.Ui
import com.topplecorp.game.ui.UiKit
import kotlin.math.sin

/** Grid of the 30 levels in a city. */
class LevelSelectScene(game: Game, private val city: Int) : Scene(game) {
    private val theme = Themes.of(city)
    private val backdrop = Backdrop(theme, game.vw, 70 + city)
    private val cols = 5
    private val rows = Campaign.LEVELS_PER_CITY / cols
    private var tile = 44f
    private var gap = 6f
    private var gridX = 0f
    private var gridY = 0f
    private var pressed = -1
    private var pressAnim = 0f

    override fun layout() {
        buttons.clear()
        buttons += UiKit.backButton { onBack() }
        val availH = h - 80f
        tile = ((w - 24f - gap * (cols - 1)) / cols).coerceAtMost((availH - gap * (rows - 1)) / rows - 10f).coerceAtMost(48f)
        val gridW = cols * tile + (cols - 1) * gap
        val gridH = rows * (tile + 10f) + (rows - 1) * gap
        gridX = (w - gridW) / 2f
        gridY = UiKit.HEADER_H + 12f + ((availH - gridH) / 2f).coerceAtLeast(0f)
    }

    override fun onBack() = game.goTo { CityScene(game) }

    private fun tileRect(i: Int): FloatArray {
        val cx = i % cols
        val cy = i / cols
        return floatArrayOf(gridX + cx * (tile + gap), gridY + cy * (tile + 10f + gap))
    }

    private fun hit(x: Float, y: Float): Int {
        for (i in 0 until Campaign.LEVELS_PER_CITY) {
            val (tx, ty) = tileRect(i).let { it[0] to it[1] }
            if (x in tx..tx + tile && y in ty..ty + tile + 10f) return i
        }
        return -1
    }

    override fun update(dt: Float) {
        super.update(dt)
        backdrop.update(dt)
        pressAnim = Ease.approach(pressAnim, if (pressed >= 0) 1f else 0f, 30f, dt)
    }

    override fun touch(t: Touch) {
        if (handleButtons(t)) return
        when (t.action) {
            TouchAction.DOWN -> pressed = hit(t.x, t.y)
            TouchAction.MOVE -> if (pressed >= 0 && hit(t.x, t.y) != pressed) pressed = -1
            TouchAction.UP -> {
                val i = pressed
                pressed = -1
                if (i >= 0 && hit(t.x, t.y) == i) {
                    if (game.save.isLevelUnlocked(city, i)) {
                        game.play(Sound.SELECT)
                        game.goTo { GameScene(game, city, i) }
                    } else game.play(Sound.DENY)
                }
            }
            TouchAction.CANCEL -> pressed = -1
        }
    }

    override fun draw(c: Canvas) {
        backdrop.draw(c, h, h * 0.8f, 0f)
        backdrop.drawGround(c, h * 0.8f, h)
    }

    override fun drawUi(c: Canvas) {
        UiKit.header(c, w, Campaign.cities[city].name)
        UiKit.goldPill(c, w - 10f, 23f, game.save.gold)

        val save = game.save
        var nextFound = false
        for (i in 0 until Campaign.LEVELS_PER_CITY) {
            val r = tileRect(i)
            // Staggered pop-in
            val appear = Ease.outBack(((time - i * 0.018f) / 0.35f).coerceIn(0f, 1f))
            if (appear <= 0f) continue
            val unlocked = save.isLevelUnlocked(city, i)
            val stars = save.stars(city, i)
            val isNext = unlocked && stars == 0 && !nextFound
            if (isNext) nextFound = true
            val th = tile + 10f
            val cx = r[0] + tile / 2
            val cy = r[1] + th / 2
            c.save()
            val s = appear * (if (i == pressed) 1f - pressAnim * 0.06f else 1f) * (if (isNext) 1f + sin(time * 5f) * 0.025f else 1f)
            c.scale(s, s, cx, cy)
            Ui.panel(c, r[0], r[1], tile, th, 10f, if (unlocked) Ui.BG else Ui.BG_SOFT)
            when {
                isNext -> Ui.outline(c, r[0], r[1], tile, th, 10f, Ui.ACCENT, 1.5f)
                stars == 3 -> Ui.outline(c, r[0], r[1], tile, th, 10f, Ui.withAlpha(Ui.GOLD, 0.7f), 1f)
            }
            if (unlocked) {
                Ui.text(c, "${i + 1}", cx, r[1] + th * 0.4f, 15f, Ui.TEXT, Ui.Align.CENTER, Ui.Weight.BOLD)
                Ui.stars(c, cx, r[1] + th - 10f, stars, 3.6f, 1.5f)
                if (save.goldBlock(city, i)) {
                    Ui.roundRect(c, r[0] + tile - 11, r[1] + 4, 8f, 5f, 1.5f, Ui.GOLD)
                }
            } else {
                Icons.draw(c, Icon.LOCK, cx, cy, 14f, Ui.TEXT_FAINT)
            }
            c.restore()
        }
        drawButtons(c)
        // Footer: total stars
        val total = save.cityStars(city)
        val label = "$total / ${Campaign.LEVELS_PER_CITY * 3}"
        val pw = Ui.width(label, 9f) + 30f
        Ui.panel(c, w / 2 - pw / 2, h - 30f, pw, 20f, 10f)
        Ui.star(c, w / 2 - pw / 2 + 12, h - 20f, 5f, true)
        Ui.text(c, label, w / 2 - pw / 2 + 21, h - 20f, 9f, Ui.TEXT)
    }
}
