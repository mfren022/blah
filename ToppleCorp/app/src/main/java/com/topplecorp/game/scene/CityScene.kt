package com.topplecorp.game.scene

import android.graphics.Canvas
import android.graphics.Matrix
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
import kotlin.math.abs
import kotlin.math.roundToInt

/** Horizontally scrolling, snapping carousel of the five cities, with a 3D card tilt. */
class CityScene(game: Game) : Scene(game) {
    private val count = Campaign.cityCount
    private var scroll = game.save.lastCity.toFloat() // in pages
    private var target = scroll
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var downScroll = 0f
    private var lastX = 0f
    private var velocity = 0f // pages per second
    private var moved = false
    private var lastPage = scroll.roundToInt()

    private val matrix = Matrix()
    private val srcPts = FloatArray(8)
    private val dstPts = FloatArray(8)
    private var cardW = 200f
    private var cardH = 300f
    private var cardY = 50f
    private var backdrops: List<Backdrop> = emptyList()
    private val bg = Backdrop(Themes.of(0), game.vw, 3)

    override fun layout() {
        buttons.clear()
        cardW = (w - 56f).coerceAtMost(240f)
        cardH = (h - 120f).coerceAtMost(380f)
        cardY = 50f + ((h - 120f) - cardH) / 2f
        backdrops = (0 until count).map { Backdrop(Themes.of(it), cardW.toInt(), 50 + it) }
        buttons += UiKit.backButton { onBack() }
        buttons += Button(2f, cardY + cardH / 2 - 20, 24f, 36f, "<", color = Pal.PANEL_LIGHT, textScale = 2) { snapTo(target.roundToInt() - 1) }
        buttons += Button(w - 26f, cardY + cardH / 2 - 20, 24f, 36f, ">", color = Pal.PANEL_LIGHT, textScale = 2) { snapTo(target.roundToInt() + 1) }
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
        val pageW = cardW + 24f
        when (t.action) {
            TouchAction.DOWN -> {
                dragging = true; moved = false
                downX = t.x; downY = t.y; lastX = t.x
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
                    val base = scroll.roundToInt()
                    val page = when {
                        velocity > 1.2f -> kotlin.math.floor(scroll).toInt() + 1
                        velocity < -1.2f -> kotlin.math.ceil(scroll).toInt() - 1
                        else -> base
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

    override fun draw(c: Canvas) {
        bg.draw(c, h, h, 0f)
        Draw.dim(c, w, h, 0.55f)
        UiKit.header(c, w, "CITIES")
        UiKit.goldPill(c, w - 6, 12f, game.save.gold)

        val pageW = cardW + 24f
        // Draw far cards first so the centred one is on top.
        val order = (0 until count).sortedByDescending { abs(it - scroll) }
        for (i in order) {
            val off = i - scroll
            if (abs(off) > 1.6f) continue
            val cx = w / 2f + off * pageW
            drawCard(c, i, cx, off)
        }
        // Page dots
        val dotsW = count * 10f
        for (i in 0 until count) {
            val active = 1f - abs(i - scroll).coerceAtMost(1f)
            val dw = 4f + active * 6f
            val x = w / 2f - dotsW / 2f + i * 10f + 5f - dw / 2f
            Draw.rect(c, x, h - 22f, dw, 4f, Pal.mix(Pal.GRAY_DARK, Pal.GOLD, active))
        }
        drawButtons(c)
    }

    private fun drawCard(c: Canvas, i: Int, centerX: Float, off: Float) {
        val city = Campaign.cities[i]
        val theme = Themes.of(i)
        val unlocked = game.save.isCityUnlocked(i)
        val left = centerX - cardW / 2f

        // Rotate the card around its vertical axis with a simple perspective projection.
        val pivotY = cardY + cardH / 2f
        val theta = Math.toRadians((off.coerceIn(-1.5f, 1.5f) * -38f).toDouble())
        val cos = kotlin.math.cos(theta).toFloat()
        val sin = kotlin.math.sin(theta).toFloat()
        val sc = 1f - abs(off).coerceAtMost(1f) * 0.12f
        val focal = 420f
        val half = cardW / 2f + 3f
        val top = cardY - 3f
        val bottom = cardY + cardH + 8f
        fun project(dx: Float, y: Float, out: Int) {
            val z = dx * sin
            val f = focal / (focal + z) * sc
            dstPts[out] = centerX + dx * cos * f
            dstPts[out + 1] = pivotY + (y - pivotY) * f
        }
        srcPts[0] = centerX - half; srcPts[1] = top
        srcPts[2] = centerX + half; srcPts[3] = top
        srcPts[4] = centerX + half; srcPts[5] = bottom
        srcPts[6] = centerX - half; srcPts[7] = bottom
        project(-half, top, 0); project(half, top, 2); project(half, bottom, 4); project(-half, bottom, 6)
        matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

        c.save()
        c.concat(matrix)
        // Card body
        Draw.panel(c, left - 3, cardY - 3, cardW + 6, cardH + 6, Pal.INK_SOFT, 5f)
        c.save()
        c.clipRect(left, cardY, left + cardW, cardY + cardH)
        c.translate(left, cardY)
        val groundY = cardH * 0.62f
        backdrops[i].draw(c, cardH, groundY, 0f)
        backdrops[i].drawGround(c, groundY, cardH)
        c.restore()

        // City number badge
        Draw.panel(c, left + 8, cardY + 8, 22f, 18f, theme.accent, 2f)
        PixelFont.drawShadow(c, "${i + 1}", left + 19, cardY + 11, Pal.WHITE, 2, align = 1)

        // Name & tagline
        val nameY = cardY + cardH * 0.66f
        PixelFont.drawShadow(c, city.name.uppercase(), centerX, nameY, Pal.WHITE, 2, align = 1)
        PixelFont.drawShadow(c, city.tagline, centerX, nameY + 19, Pal.CREAM, 1, align = 1)

        // Progress
        val stars = game.save.cityStars(i)
        val cleared = game.save.cityCleared(i)
        val py = nameY + 34
        Draw.panel(c, centerX - 70, py, 140f, 30f, Pal.alpha(Pal.INK, 0.75f), 0f, Pal.alpha(Pal.INK, 0.9f))
        Draw.bitmap(c, Sprites.star, centerX - 62, py + 4)
        PixelFont.drawShadow(c, "$stars/${Campaign.LEVELS_PER_CITY * 3}", centerX - 50, py + 6, Pal.GOLD)
        PixelFont.drawShadow(c, "LEVELS $cleared/${Campaign.LEVELS_PER_CITY}", centerX + 64, py + 6, Pal.WHITE, align = 2)
        val barW = 128f
        Draw.rect(c, centerX - barW / 2, py + 19, barW, 5f, Pal.INK)
        Draw.rect(c, centerX - barW / 2 + 1, py + 20, (barW - 2) * cleared / Campaign.LEVELS_PER_CITY, 3f, Pal.GREEN)

        if (unlocked) {
            val bob = if (abs(off) < 0.1f) kotlin.math.sin(time * 4f) * 1.5f else 0f
            val by = cardY + cardH - 46f + bob
            Draw.block(c, centerX - 56, by, 112f, 32f, Pal.ORANGE, 6f)
            Draw.bitmap(c, Sprites.play, centerX - 38, by + 9, 2f)
            PixelFont.drawShadow(c, "PLAY", centerX + 8, by + 9, Pal.WHITE, 2, align = 1, shadow = Pal.ORANGE_DARK)
        } else {
            Draw.rect(c, left, cardY, cardW, cardH, Pal.alpha(Pal.INK, 0.6f))
            Draw.bitmap(c, Sprites.lock, centerX - 8, cardY + cardH * 0.3f, 2f)
            val prev = Campaign.cities[i - 1].name
            PixelFont.drawShadow(c, "CLEAR ${prev.uppercase()}", centerX, cardY + cardH - 40, Pal.CREAM, align = 1)
            PixelFont.drawShadow(c, "TO UNLOCK", centerX, cardY + cardH - 30, Pal.CREAM, align = 1)
        }
        // Darken cards that are off to the side for depth.
        val fade = abs(off).coerceAtMost(1f) * 0.45f
        if (fade > 0.01f) Draw.rect(c, left - 3, cardY - 3, cardW + 6, cardH + 11, Pal.alpha(Pal.INK, fade))
        c.restore()
    }
}
