package com.topplecorp.game.scene

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import com.topplecorp.core.Campaign
import com.topplecorp.game.Game
import com.topplecorp.game.audio.Sound
import com.topplecorp.game.gfx.Backdrop
import com.topplecorp.game.gfx.Draw
import com.topplecorp.game.gfx.Themes
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

/** Horizontally scrolling, snapping carousel of the five cities, with a 3D card tilt. */
class CityScene(game: Game) : Scene(game) {
    private val count = Campaign.cityCount
    private var scroll = game.save.lastCity.toFloat() // in pages
    private var target = scroll
    private var dragging = false
    private var downX = 0f
    private var downScroll = 0f
    private var lastX = 0f
    private var velocity = 0f // pages per second
    private var moved = false
    private var lastPage = scroll.roundToInt()

    private val matrix = Matrix()
    private val srcPts = FloatArray(8)
    private val dstPts = FloatArray(8)
    private val clip = Path()
    private val clipRect = RectF()
    private var cardW = 200f
    private var cardH = 300f
    private var cardY = 50f
    private var backdrops: List<Backdrop> = emptyList()
    private val bg = Backdrop(Themes.of(0), game.vw, 3)

    private val pageW get() = cardW + 24f

    override fun layout() {
        buttons.clear()
        cardW = (w - 64f).coerceAtMost(240f)
        cardH = (h - 120f).coerceAtMost(390f)
        cardY = UiKit.HEADER_H + 14f + ((h - 120f) - cardH) / 2f
        backdrops = (0 until count).map { Backdrop(Themes.of(it), cardW.toInt(), 50 + it) }
        buttons += UiKit.backButton { onBack() }
        buttons += Button(6f, cardY + cardH / 2 - 16, 24f, 32f, icon = Icon.BACK) { snapTo(target.roundToInt() - 1) }
            .apply { radius = 12f; iconSize = 12f }
        buttons += Button(w - 30f, cardY + cardH / 2 - 16, 24f, 32f, icon = Icon.PLAY) { snapTo(target.roundToInt() + 1) }
            .apply { radius = 12f; iconSize = 10f }
    }

    override fun onBack() = game.goTo { MenuScene(game) }

    private fun snapTo(page: Int) {
        target = page.coerceIn(0, count - 1).toFloat()
    }

    override fun update(dt: Float) {
        super.update(dt)
        bg.update(dt)
        if (!dragging) scroll = Ease.approach(scroll, target, 12f, dt)
        val page = scroll.roundToInt()
        if (page != lastPage) {
            lastPage = page
            game.play(Sound.AIM, 0.4f, 0.9f + page * 0.05f)
        }
        buttons[1].visible = target > 0.5f
        buttons[2].visible = target < count - 1.5f
    }

    override fun touch(t: Touch) {
        if (!dragging && handleButtons(t)) return
        when (t.action) {
            TouchAction.DOWN -> {
                dragging = true; moved = false
                downX = t.x; lastX = t.x
                downScroll = scroll; velocity = 0f
            }
            TouchAction.MOVE -> if (dragging) {
                if (abs(t.x - downX) > 4f) moved = true
                val d = (t.x - lastX) / pageW
                velocity = velocity * 0.6f + (-d * 60f) * 0.4f
                lastX = t.x
                scroll = (downScroll - (t.x - downX) / pageW).coerceIn(-0.3f, count - 0.7f)
            }
            TouchAction.UP -> if (dragging) {
                dragging = false
                if (!moved) {
                    tapCard(t)
                } else {
                    val page = when {
                        velocity > 1.2f -> kotlin.math.floor(scroll).toInt() + 1
                        velocity < -1.2f -> kotlin.math.ceil(scroll).toInt() - 1
                        else -> scroll.roundToInt()
                    }
                    snapTo(page)
                }
            }
            TouchAction.CANCEL -> { dragging = false; snapTo(scroll.roundToInt()) }
        }
    }

    private fun tapCard(t: Touch) {
        val cx = (w - cardW) / 2f
        if (t.x !in cx..cx + cardW || t.y !in cardY..cardY + cardH) return
        val city = target.roundToInt()
        if (game.save.isCityUnlocked(city)) {
            game.save.lastCity = city
            game.play(Sound.SELECT)
            game.goTo { LevelSelectScene(game, city) }
        } else {
            game.play(Sound.DENY)
        }
    }

    /** Cards ordered far-to-near, so the centred card is drawn last. */
    private fun visibleCards() = (0 until count).sortedByDescending { abs(it - scroll) }.filter { abs(it - scroll) <= 1.6f }

    /** Rotates a card around its vertical axis with a simple perspective projection. */
    private fun cardMatrix(i: Int): Matrix {
        val off = i - scroll
        val centerX = w / 2f + off * pageW
        val pivotY = cardY + cardH / 2f
        val theta = Math.toRadians((off.coerceIn(-1.5f, 1.5f) * -38f).toDouble())
        val cos = kotlin.math.cos(theta).toFloat()
        val sin = kotlin.math.sin(theta).toFloat()
        val sc = 1f - abs(off).coerceAtMost(1f) * 0.12f
        val focal = 420f
        val half = cardW / 2f
        val top = cardY
        val bottom = cardY + cardH
        fun project(dx: Float, y: Float, out: Int) {
            val f = focal / (focal + dx * sin) * sc
            dstPts[out] = centerX + dx * cos * f
            dstPts[out + 1] = pivotY + (y - pivotY) * f
        }
        srcPts[0] = centerX - half; srcPts[1] = top
        srcPts[2] = centerX + half; srcPts[3] = top
        srcPts[4] = centerX + half; srcPts[5] = bottom
        srcPts[6] = centerX - half; srcPts[7] = bottom
        project(-half, top, 0); project(half, top, 2); project(half, bottom, 4); project(-half, bottom, 6)
        matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)
        return matrix
    }

    // ------------------------------------------------------------------ world pass

    override fun draw(c: Canvas) {
        bg.draw(c, h, h, 0f)
        Draw.dim(c, w, h, 0.45f)
        for (i in visibleCards()) {
            val left = w / 2f + (i - scroll) * pageW - cardW / 2f
            c.save()
            c.concat(cardMatrix(i))
            clip.reset()
            clipRect.set(left, cardY, left + cardW, cardY + cardH)
            clip.addRoundRect(clipRect, 14f, 14f, Path.Direction.CW)
            c.clipPath(clip)
            c.translate(left, cardY)
            val groundY = cardH * 0.55f
            backdrops[i].draw(c, cardH, groundY, 0f)
            backdrops[i].drawGround(c, groundY, cardH)
            c.restore()
        }
    }

    // ------------------------------------------------------------------ UI pass

    override fun drawUi(c: Canvas) {
        UiKit.header(c, w, "Cities")
        UiKit.goldPill(c, w - 10f, 23f, game.save.gold)
        for (i in visibleCards()) {
            c.save()
            c.concat(cardMatrix(i))
            drawCardUi(c, i, i - scroll)
            c.restore()
        }
        // Page dots
        for (i in 0 until count) {
            val active = 1f - abs(i - scroll).coerceAtMost(1f)
            val dw = 6f + active * 10f
            val x = w / 2f + (i - (count - 1) / 2f) * 14f - dw / 2f
            Ui.roundRect(c, x, h - 24f, dw, 6f, 3f, Ui.withAlpha(if (active > 0.5f) Ui.ACCENT else Ui.TEXT, 0.35f + active * 0.65f))
        }
        drawButtons(c)
    }

    private fun drawCardUi(c: Canvas, i: Int, off: Float) {
        val city = Campaign.cities[i]
        val unlocked = game.save.isCityUnlocked(i)
        val centerX = w / 2f + off * pageW
        val left = centerX - cardW / 2f
        val r = 14f

        Ui.outline(c, left, cardY, cardW, cardH, r, 0x33FFFFFF, 1f)

        // City number chip
        Ui.panel(c, left + 10, cardY + 10, 44f, 18f, 9f)
        Ui.text(c, "City ${i + 1}", left + 32, cardY + 19, 8f, Ui.TEXT, Ui.Align.CENTER, Ui.Weight.BOLD)

        // Info panel
        val infoY = cardY + cardH * 0.58f
        val infoH = cardH - (infoY - cardY) - 10f
        Ui.panel(c, left + 10, infoY, cardW - 20, infoH, 12f, Ui.BG_STRONG)
        Ui.text(c, city.name, centerX, infoY + 18, 16f, Ui.TEXT, Ui.Align.CENTER, Ui.Weight.BOLD)
        Ui.text(c, city.tagline, centerX, infoY + 34, 8f, Ui.TEXT_DIM, Ui.Align.CENTER)

        val stars = game.save.cityStars(i)
        val cleared = game.save.cityCleared(i)
        val py = infoY + 52
        Ui.star(c, left + 26, py, 5f, true)
        Ui.text(c, "$stars / ${Campaign.LEVELS_PER_CITY * 3}", left + 35, py, 8f, Ui.TEXT)
        Ui.text(c, "$cleared / ${Campaign.LEVELS_PER_CITY} levels", left + cardW - 20, py, 8f, Ui.TEXT_DIM, Ui.Align.RIGHT)
        val barW = cardW - 40f
        Ui.roundRect(c, left + 20, py + 9, barW, 4f, 2f, 0x33FFFFFF)
        if (cleared > 0) Ui.roundRect(c, left + 20, py + 9, barW * cleared / Campaign.LEVELS_PER_CITY, 4f, 2f, Ui.GREEN)

        if (unlocked) {
            val bob = if (abs(off) < 0.1f) kotlin.math.sin(time * 4f) * 1f else 0f
            val bw = cardW - 40f
            val by = cardY + cardH - 46f + bob
            Ui.roundRect(c, centerX - bw / 2, by, bw, 28f, 14f, Ui.withAlpha(0xFF000000.toInt(), 0.6f))
            Ui.outline(c, centerX - bw / 2, by, bw, 28f, 14f, Ui.ACCENT, 1.5f)
            Icons.draw(c, Icon.PLAY, centerX - 20, by + 14, 12f, Ui.ACCENT)
            Ui.text(c, "Play", centerX - 10, by + 14, 12f, Ui.TEXT, weight = Ui.Weight.BOLD)
        } else {
            Ui.roundRect(c, left, cardY, cardW, infoY - cardY - 4, r, 0x80000000.toInt())
            Ui.circle(c, centerX, cardY + (infoY - cardY) / 2f, 22f, Ui.BG_STRONG)
            Icons.draw(c, Icon.LOCK, centerX, cardY + (infoY - cardY) / 2f, 24f, Ui.TEXT)
            val prev = Campaign.cities[i - 1].name
            Ui.text(c, "Clear $prev to unlock", centerX, cardY + cardH - 32, 8.5f, Ui.TEXT_DIM, Ui.Align.CENTER)
        }
        // Darken side cards for depth.
        val fade = abs(off).coerceAtMost(1f) * 0.5f
        if (fade > 0.01f) Ui.roundRect(c, left, cardY, cardW, cardH, r, Ui.withAlpha(0xFF000000.toInt(), fade))
    }
}
