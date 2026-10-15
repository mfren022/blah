package com.topplecorp.game.scene

import android.graphics.Canvas
import com.topplecorp.core.Campaign
import com.topplecorp.game.Game
import com.topplecorp.game.audio.Sound
import com.topplecorp.game.gfx.Backdrop
import com.topplecorp.game.gfx.Draw
import com.topplecorp.game.gfx.Pal
import com.topplecorp.game.gfx.PixelFont
import com.topplecorp.game.gfx.Sprites
import com.topplecorp.game.gfx.Themes
import com.topplecorp.game.ui.Button
import com.topplecorp.game.ui.Ease
import com.topplecorp.game.ui.Touch
import com.topplecorp.game.ui.TouchAction
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
        buttons += Button(6f, 6f, 26f, 18f, icon = Sprites.back, color = Pal.PANEL_LIGHT, sound = Sound.BACK) { onBack() }
        val availH = h - 80f
        tile = ((w - 24f - gap * (cols - 1)) / cols).coerceAtMost((availH - gap * (rows - 1)) / rows - 10f).coerceAtMost(48f)
        val gridW = cols * tile + (cols - 1) * gap
        val gridH = rows * (tile + 10f) + (rows - 1) * gap
        gridX = (w - gridW) / 2f
        gridY = 44f + ((availH - gridH) / 2f).coerceAtLeast(0f)
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
        Draw.dim(c, w, h, 0.35f)
        UiKit.header(c, w, Campaign.cities[city].name.uppercase())
        UiKit.goldPill(c, w - 6, 8f, game.save.gold)

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
            val cx = r[0] + tile / 2
            val cy = r[1] + tile / 2
            c.save()
            val s = appear * (if (i == pressed) 1f - pressAnim * 0.08f else 1f) * (if (isNext) 1f + sin(time * 5f) * 0.03f else 1f)
            c.scale(s, s, cx, cy)
            val face = when {
                !unlocked -> Pal.GRAY_DARK
                isNext -> Pal.ORANGE
                stars == 3 -> Pal.GOLD_DARK
                else -> theme.accent
            }
            Draw.panel(c, r[0], r[1], tile, tile, face, 4f)
            if (unlocked) {
                PixelFont.drawShadow(c, "${i + 1}", cx, r[1] + tile / 2 - 9, Pal.WHITE, 2, align = 1, shadow = Pal.shade(face, 0.4f))
                UiKit.stars(c, cx, r[1] + tile - 13, stars, 1f, 0f)
                if (save.goldBlock(city, i)) Draw.bitmap(c, Sprites.goldBar, r[0] + tile - 14, r[1] - 3)
            } else {
                Draw.bitmap(c, Sprites.lock, cx - 4, cy - 5)
            }
            c.restore()
        }
        drawButtons(c)
        // Footer: total stars
        val total = save.cityStars(city)
        Draw.bitmap(c, Sprites.star, w / 2 - 28, h - 22f)
        PixelFont.drawShadow(c, "$total / ${Campaign.LEVELS_PER_CITY * 3}", w / 2 - 15, h - 20f, Pal.GOLD)
    }
}
