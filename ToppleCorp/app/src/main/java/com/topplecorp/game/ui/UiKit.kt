package com.topplecorp.game.ui

import android.graphics.Canvas
import com.topplecorp.game.gfx.Draw
import com.topplecorp.game.gfx.Pal
import com.topplecorp.game.gfx.PixelFont
import com.topplecorp.game.gfx.Sprites

/** Reusable HUD pieces. */
object UiKit {
    const val HEADER_H = 40f

    /** Standard back button in the top-left corner of sub-screens. */
    fun backButton(onClick: () -> Unit) = Button(5f, 5f, 38f, 26f, icon = Sprites.back, color = Pal.PANEL_LIGHT,
        sound = com.topplecorp.game.audio.Sound.BACK, onClick = onClick).apply { iconScale = 2f }

    /** Gold counter pill, right-aligned at [right]. Returns its left x. */
    fun goldPill(c: Canvas, right: Float, y: Float, gold: Int): Float {
        val text = formatGold(gold)
        val w = PixelFont.width(text) + 22
        val x = right - w
        Draw.panel(c, x, y, w, 15f, Pal.PANEL_DARK, 2f)
        Draw.bitmap(c, Sprites.coin, x + 4, y + 3.5f)
        PixelFont.drawShadow(c, text, x + 16, y + 4, Pal.GOLD)
        return x
    }

    fun formatGold(v: Int): String {
        val s = v.toString()
        val sb = StringBuilder()
        for ((i, ch) in s.withIndex()) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
            sb.append(ch)
        }
        return sb.toString()
    }

    /** Title bar used by sub-menus. */
    fun header(c: Canvas, w: Float, title: String) {
        Draw.rect(c, 0f, 0f, w, HEADER_H, Pal.alpha(Pal.INK, 0.55f))
        Draw.rect(c, 0f, HEADER_H, w, 1f, Pal.alpha(Pal.WHITE, 0.15f))
        PixelFont.drawShadow(c, title, w / 2f, 13f, Pal.CREAM, 2, align = 1)
    }

    fun stars(c: Canvas, cx: Float, y: Float, count: Int, scale: Float = 1f, gap: Float = 1f) {
        val sw = Sprites.star.width * scale
        val total = sw * 3 + gap * 2
        var x = cx - total / 2f
        for (i in 0 until 3) {
            Draw.bitmap(c, if (i < count) Sprites.star else Sprites.starEmpty, x.toInt().toFloat(), y, scale)
            x += sw + gap
        }
    }
}
