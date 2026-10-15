package com.topplecorp.game.scene

import android.graphics.Canvas
import com.topplecorp.game.Game
import com.topplecorp.game.audio.Sound
import com.topplecorp.game.data.StoreCatalog
import com.topplecorp.game.data.StoreItem
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

/** Store with tileset and crew skin placeholders. */
class StoreScene(game: Game) : Scene(game) {
    private val backdrop = Backdrop(Themes.of(1), game.vw, 5)
    private var category = StoreItem.Category.TILESETS
    private var tabAnim = 0f
    private var cardW = 110f
    private var cardH = 110f
    private var gridY = 72f
    private var pressed = -1
    private var toast = ""
    private var toastTime = 0f
    private var switchTime = 0f

    override fun layout() {
        buttons.clear()
        buttons += Button(6f, 6f, 26f, 18f, icon = Sprites.back, color = Pal.PANEL_LIGHT, sound = Sound.BACK) { onBack() }
        val tw = (w - 24f) / 2f
        buttons += Button(8f, 40f, tw, 20f, "TILESETS", color = Pal.BLUE, sound = Sound.SELECT) { setCategory(StoreItem.Category.TILESETS) }
        buttons += Button(16f + tw, 40f, tw, 20f, "CREW SKINS", color = Pal.BLUE, sound = Sound.SELECT) { setCategory(StoreItem.Category.CREW) }
        cardW = (w - 24f) / 2f
        cardH = ((h - gridY - 40f) / 3f - 8f).coerceAtMost(130f)
        refreshTabs()
    }

    private fun setCategory(c: StoreItem.Category) {
        if (c == category) return
        category = c
        switchTime = 0f
        refreshTabs()
    }

    private fun refreshTabs() {
        buttons[1].selected = category == StoreItem.Category.TILESETS
        buttons[2].selected = category == StoreItem.Category.CREW
        buttons[1].color = if (buttons[1].selected) Pal.ORANGE else Pal.PANEL_LIGHT
        buttons[2].color = if (buttons[2].selected) Pal.ORANGE else Pal.PANEL_LIGHT
    }

    override fun onBack() = game.goTo { MenuScene(game) }

    private fun cardPos(i: Int) = floatArrayOf(8f + (i % 2) * (cardW + 8f), gridY + (i / 2) * (cardH + 8f))

    private fun hit(x: Float, y: Float): Int {
        val items = StoreCatalog.of(category)
        for (i in items.indices) {
            val p = cardPos(i)
            if (x in p[0]..p[0] + cardW && y in p[1]..p[1] + cardH) return i
        }
        return -1
    }

    override fun update(dt: Float) {
        super.update(dt)
        backdrop.update(dt)
        switchTime += dt
        toastTime -= dt
        tabAnim = Ease.approach(tabAnim, if (pressed >= 0) 1f else 0f, 30f, dt)
    }

    override fun touch(t: Touch) {
        if (handleButtons(t)) return
        when (t.action) {
            TouchAction.DOWN -> pressed = hit(t.x, t.y)
            TouchAction.MOVE -> if (hit(t.x, t.y) != pressed) pressed = -1
            TouchAction.UP -> {
                val i = pressed
                pressed = -1
                if (i < 0 || hit(t.x, t.y) != i) return
                val item = StoreCatalog.of(category)[i]
                if (item.available) {
                    game.play(Sound.SELECT)
                    showToast("${item.name.uppercase()} EQUIPPED")
                } else {
                    game.play(Sound.DENY)
                    showToast("COMING SOON!")
                }
            }
            TouchAction.CANCEL -> pressed = -1
        }
    }

    private fun showToast(s: String) { toast = s; toastTime = 1.6f }

    override fun draw(c: Canvas) {
        backdrop.draw(c, h, h * 0.85f, 0f)
        Draw.dim(c, w, h, 0.55f)
        UiKit.header(c, w, "STORE")
        UiKit.goldPill(c, w - 6, 8f, game.save.gold)
        val items = StoreCatalog.of(category)
        items.forEachIndexed { i, item ->
            val p = cardPos(i)
            val appear = Ease.outBack(((switchTime - i * 0.04f) / 0.3f).coerceIn(0f, 1f))
            if (appear <= 0f) return@forEachIndexed
            c.save()
            val s = appear * (if (i == pressed) 1f - tabAnim * 0.06f else 1f)
            c.scale(s, s, p[0] + cardW / 2, p[1] + cardH / 2)
            drawCard(c, item, p[0], p[1])
            c.restore()
        }
        drawButtons(c)
        if (toastTime > 0f) {
            val a = (toastTime / 0.3f).coerceAtMost(1f)
            val tw = PixelFont.width(toast) + 20
            val ty = h - 36f + (1f - a) * 10f
            Draw.panel(c, (w - tw) / 2, ty, tw, 18f, Pal.INK_SOFT, 2f)
            PixelFont.drawShadow(c, toast, w / 2f, ty + 6, Pal.GOLD, align = 1, alpha = (255 * a).toInt())
        }
    }

    private fun drawCard(c: Canvas, item: StoreItem, x: Float, y: Float) {
        Draw.panel(c, x, y, cardW, cardH, Pal.PANEL, 3f)
        // Preview area
        val pvH = cardH - 34f
        Draw.rect(c, x + 4, y + 4, cardW - 8, pvH, Pal.PANEL_DARK)
        val cx = x + cardW / 2
        val cy = y + 4 + pvH / 2
        if (item.category == StoreItem.Category.TILESETS) {
            // Mini 3D block stack in the tileset's colours.
            val size = 14f
            val d = 4f
            val layout = listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 1 to 1, 1 to 2)
            for ((i, pos) in layout.withIndex()) {
                val col = item.colors[i % item.colors.size]
                val bx = cx - 24 + pos.first * size
                val by = cy + 14 - (pos.second + 1) * size + sin(switchTime * 2f + i) * 0.5f
                Draw.rect(c, bx + d, by - d, size, d, Pal.tint(col, 0.3f))
                Draw.rect(c, bx + size, by - d + 1, d, size, Pal.shade(col, 0.6f))
                Draw.rect(c, bx, by, size, size, col)
                Draw.rect(c, bx, by, size, 1f, Pal.shade(col, 0.4f))
                Draw.rect(c, bx, by + size - 1, size, 1f, Pal.shade(col, 0.4f))
                Draw.rect(c, bx, by, 1f, size, Pal.shade(col, 0.4f))
                Draw.rect(c, bx + size - 1, by, 1f, size, Pal.shade(col, 0.4f))
                Draw.rect(c, bx + 1, by + 1, size - 2, 1f, Pal.tint(col, 0.4f))
            }
        } else {
            // Crew silhouettes tinted with the skin colours.
            Sprites.crew.forEachIndexed { i, m ->
                val bx = cx - 26 + i * 18
                val bob = if ((switchTime * 3f + i).toInt() % 2 == 0) 0f else -1f
                if (item.available) Draw.bitmap(c, m.body, bx, cy - 10 + bob)
                else Draw.silhouette(c, m.body, bx, cy - 10 + bob, 1f, item.colors[i % item.colors.size])
            }
        }
        if (!item.available) {
            Draw.panel(c, x + cardW - 52, y + 2, 50f, 11f, Pal.PURPLE, 1f)
            PixelFont.draw(c, "SOON", x + cardW - 27 - PixelFont.width("SOON") / 2, y + 4, Pal.WHITE)
        }
        PixelFont.drawShadow(c, item.name.uppercase(), cx, y + cardH - 27, Pal.CREAM, align = 1)
        if (item.available) {
            PixelFont.drawShadow(c, "EQUIPPED", cx, y + cardH - 14, Pal.GREEN, align = 1)
        } else {
            val price = UiKit.formatGold(item.price)
            val pw = PixelFont.width(price) + 12
            Draw.bitmap(c, Sprites.coin, cx - pw / 2, y + cardH - 15)
            PixelFont.drawShadow(c, price, cx - pw / 2 + 12, y + cardH - 14, Pal.alpha(Pal.GOLD, 0.6f))
        }
    }
}
