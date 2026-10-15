package com.topplecorp.game.scene

import android.graphics.Canvas
import com.topplecorp.core.Campaign
import com.topplecorp.game.Game
import com.topplecorp.game.audio.Sound
import com.topplecorp.game.gfx.CityPanorama
import com.topplecorp.game.ui.Button
import com.topplecorp.game.ui.Ease
import com.topplecorp.game.ui.Icon
import com.topplecorp.game.ui.Icons
import com.topplecorp.game.ui.Touch
import com.topplecorp.game.ui.TouchAction
import com.topplecorp.game.ui.Ui
import com.topplecorp.game.ui.UiKit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * City select: each city fills the screen with its own pixel-art scene. Swiping (or the side
 * arrows) plays an ease-in-out slide to the neighbouring city.
 */
class CityScene(game: Game) : Scene(game) {
    private val count = Campaign.cityCount

    /** Current position in cities (fractional during a transition). */
    private var pos = game.save.lastCity.toFloat()
    private var current = game.save.lastCity

    // Transition tween
    private var tweenFrom = pos
    private var tweenTo = pos
    private var tweenT = 1f
    private var tweenDur = TRANSITION

    // Gesture
    private var touching = false
    private var downX = 0f
    private var downY = 0f
    private var downPos = 0f
    private var moved = false

    private var panorama: CityPanorama? = null
    private var play: Button? = null

    private val transitioning get() = tweenT < 1f

    override fun layout() {
        buttons.clear()
        panorama = CityPanorama(game.vw, game.vh, count)
        buttons += UiKit.backButton { onBack() }
        val midY = h * 0.42f
        buttons += Button(8f, midY - 18, 28f, 36f, icon = Icon.BACK) { goTo(current - 1) }
            .apply { radius = 14f; iconSize = 13f }
        buttons += Button(w - 36f, midY - 18, 28f, 36f, icon = Icon.PLAY) { goTo(current + 1) }
            .apply { radius = 14f; iconSize = 11f }
        play = Button(w / 2f - 64f, h - 122f, 128f, 34f, "Play", Icon.PLAY, Ui.ACCENT, textSize = 13f) { enter(current) }
            .apply { primary = true; radius = 17f }
        buttons += play!!
    }

    override fun onBack() = game.goTo { MenuScene(game) }

    /** Starts an ease-in-out slide to [target]. */
    private fun goTo(target: Int) {
        val t = target.coerceIn(0, count - 1)
        if (t == current && !transitioning && pos == t.toFloat()) return
        tweenFrom = pos
        tweenTo = t.toFloat()
        tweenDur = TRANSITION * abs(tweenTo - tweenFrom).coerceIn(0.5f, 1f)
        tweenT = 0f
        if (t != current) game.play(Sound.WHOOSH, 0.5f, 1f + t * 0.05f)
        current = t
        game.save.lastCity = t
    }

    private fun enter(city: Int) {
        if (transitioning) return
        if (game.save.isCityUnlocked(city)) {
            game.play(Sound.SELECT)
            game.goTo { LevelSelectScene(game, city) }
        } else game.play(Sound.DENY)
    }

    override fun update(dt: Float) {
        super.update(dt)
        if (transitioning) {
            tweenT = (tweenT + dt / tweenDur).coerceAtMost(1f)
            pos = tweenFrom + (tweenTo - tweenFrom) * Ease.inOutCubic(tweenT)
        }
        buttons[1].visible = current > 0
        buttons[2].visible = current < count - 1
        play?.enabled = game.save.isCityUnlocked(current)
    }

    override fun touch(t: Touch) {
        if (!moved && handleButtons(t)) return
        when (t.action) {
            TouchAction.DOWN -> {
                if (transitioning) return
                touching = true; moved = false
                downX = t.x; downY = t.y; downPos = pos
            }
            TouchAction.MOVE -> if (touching) {
                val dx = t.x - downX
                if (abs(dx) > 6f) moved = true
                // Light rubber-band feedback while dragging; the real move is the eased transition.
                if (moved) pos = (downPos - dx / w * 0.25f).coerceIn(-0.15f, count - 0.85f)
                if (abs(dx) > w * 0.18f) {
                    touching = false
                    goTo(current + if (dx < 0) 1 else -1)
                }
            }
            TouchAction.UP -> if (touching) {
                touching = false
                val dx = t.x - downX
                when {
                    abs(dx) > 30f -> goTo(current + if (dx < 0) 1 else -1)
                    moved -> goTo(current)
                    t.y in UiKit.HEADER_H..(h - 130f) -> enter(current)
                }
                moved = false
            }
            TouchAction.CANCEL -> { touching = false; moved = false; goTo(current) }
        }
    }

    // ------------------------------------------------------------------ world pass

    override fun draw(c: Canvas) {
        panorama?.draw(c, pos, time)
    }

    // ------------------------------------------------------------------ UI pass

    override fun drawUi(c: Canvas) {
        // Locked overlays, per city slot
        for (i in 0 until count) {
            val off = i - pos
            if (abs(off) >= 1f || game.save.isCityUnlocked(i)) continue
            val x = off * w
            c.save()
            val label = "Clear ${Campaign.cities[i - 1].name} to unlock"
            val pw = Ui.width(label, 9f) + 34f
            val py = UiKit.HEADER_H + 12f
            Ui.panel(c, x + w / 2f - pw / 2f, py, pw, 24f, 12f, Ui.BG_STRONG)
            Icons.draw(c, Icon.LOCK, x + w / 2f - pw / 2f + 14f, py + 12f, 11f, Ui.TEXT)
            Ui.text(c, label, x + w / 2f - pw / 2f + 24f, py + 12f, 9f, Ui.TEXT)
            c.restore()
        }

        UiKit.header(c, w, "Cities")
        UiKit.goldPill(c, w - 10f, 23f, game.save.gold)

        // Page dots
        for (i in 0 until count) {
            val active = 1f - abs(i - pos).coerceAtMost(1f)
            val dw = 6f + active * 10f
            val x = w / 2f + (i - (count - 1) / 2f) * 14f - dw / 2f
            Ui.roundRect(c, x, h - 82f, dw, 5f, 2.5f, Ui.withAlpha(if (active > 0.5f) Ui.ACCENT else Ui.TEXT, 0.4f + active * 0.6f))
        }
        drawInfoBar(c)
        drawButtons(c)
    }

    /** City name in the middle, completion % on the left, stars and gold blocks on the right. */
    private fun drawInfoBar(c: Canvas) {
        val shown = pos.roundToInt().coerceIn(0, count - 1)
        val frac = abs(pos - shown) * 2f // 0 at rest, 1 at the midpoint of a transition
        val a = (1f - frac).coerceIn(0f, 1f)
        val slide = (pos - shown) * -24f

        val bx = 10f
        val bw = w - 20f
        val by = h - 70f
        val bh = 58f
        Ui.panel(c, bx, by, bw, bh, 16f, Ui.BG_STRONG)
        val cy = by + bh / 2f

        val save = game.save
        val city = Campaign.cities[shown]
        val cleared = save.cityCleared(shown)
        val percent = cleared * 100 / Campaign.LEVELS_PER_CITY
        val stars = save.cityStars(shown)
        val gold = save.cityGoldBlocks(shown)

        c.save()
        c.translate(slide, 0f)
        // Centre: name + tagline
        Ui.text(c, city.name, w / 2f, cy - 7f, if (city.name.length > 10) 14f else 16f, Ui.TEXT, Ui.Align.CENTER, Ui.Weight.BOLD, a)
        Ui.text(c, city.tagline, w / 2f, cy + 11f, 7f, Ui.TEXT_DIM, Ui.Align.CENTER, alpha = a)

        // Left: completion
        Ui.text(c, "$percent%", bx + 30f, cy - 6f, 14f, if (percent == 100) Ui.GREEN else Ui.TEXT, Ui.Align.CENTER, Ui.Weight.BOLD, a)
        Ui.text(c, "complete", bx + 30f, cy + 9f, 7f, Ui.TEXT_DIM, Ui.Align.CENTER, alpha = a)

        // Right: stars and gold blocks
        val rx = bx + bw - 14f
        Ui.text(c, "$stars", rx, cy - 8f, 10f, Ui.TEXT, Ui.Align.RIGHT, Ui.Weight.BOLD, a)
        Ui.star(c, rx - Ui.width("$stars", 10f, Ui.Weight.BOLD) - 9f, cy - 8.5f, 5.5f, true)
        Ui.text(c, "$gold", rx, cy + 9f, 10f, Ui.TEXT, Ui.Align.RIGHT, Ui.Weight.BOLD, a)
        val gx = rx - Ui.width("$gold", 10f, Ui.Weight.BOLD) - 16f
        Ui.roundRect(c, gx, cy + 5f, 11f, 8f, 1.5f, Ui.withAlpha(Ui.GOLD, a))
        Ui.roundRect(c, gx + 2f, cy + 6f, 5f, 2f, 1f, Ui.withAlpha(0xFFFFF0B0.toInt(), a))
        c.restore()
    }

    companion object {
        const val TRANSITION = 0.4f
    }
}
