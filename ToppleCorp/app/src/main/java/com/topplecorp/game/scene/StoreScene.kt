package com.topplecorp.game.scene

import android.graphics.Canvas
import com.topplecorp.game.Game
import com.topplecorp.game.audio.Sound
import com.topplecorp.game.data.StoreCatalog
import com.topplecorp.game.data.StoreItem
import com.topplecorp.game.gfx.Backdrop
import com.topplecorp.game.gfx.Draw
import com.topplecorp.game.gfx.Pal
import com.topplecorp.game.gfx.Sprites
import com.topplecorp.game.gfx.Themes
import com.topplecorp.game.ui.Button
import com.topplecorp.game.ui.Ease
import com.topplecorp.game.ui.Icon
import com.topplecorp.game.ui.Icons
import com.topplecorp.game.ui.Touch
import com.topplecorp.game.ui.TouchAction
import com.topplecorp.game.ui.Ui
import com.topplecorp.game.ui.UiKit
import kotlin.math.sin

/** Store with tileset and crew skin placeholders. */
class StoreScene(game: Game) : Scene(game) {
    private val backdrop = Backdrop(Themes.of(1), game.vw, 5)
    private var category = StoreItem.Category.TILESETS
    private var tabAnim = 0f
    private var cardW = 110f
    private var cardH = 110f
    private var gridY = 90f
    private var pressed = -1
    private var toast = ""
    private var toastTime = 0f
    private var switchTime = 0f

    override fun layout() {
        buttons.clear()
        buttons += UiKit.backButton { onBack() }
        val tw = (w - 28f) / 2f
        buttons += Button(10f, UiKit.HEADER_H + 8f, tw, 30f, "Tilesets", color = Ui.ACCENT, textSize = 11f, sound = Sound.SELECT) {
            setCategory(StoreItem.Category.TILESETS)
        }.apply { radius = 15f }
        buttons += Button(18f + tw, UiKit.HEADER_H + 8f, tw, 30f, "Crew Skins", color = Ui.ACCENT, textSize = 11f, sound = Sound.SELECT) {
            setCategory(StoreItem.Category.CREW)
        }.apply { radius = 15f }
        gridY = UiKit.HEADER_H + 48f
        cardW = (w - 28f) / 2f
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
    }

    override fun onBack() = game.goTo { MenuScene(game) }

    private fun cardPos(i: Int) = floatArrayOf(10f + (i % 2) * (cardW + 8f), gridY + (i / 2) * (cardH + 8f))

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
                    showToast("${item.name} equipped")
                } else {
                    game.play(Sound.DENY)
                    showToast("Coming soon!")
                }
            }
            TouchAction.CANCEL -> pressed = -1
        }
    }

    private fun showToast(s: String) { toast = s; toastTime = 1.6f }

    override fun draw(c: Canvas) {
        backdrop.draw(c, h, h * 0.85f, 0f)
        Draw.dim(c, w, h, 0.25f)
    }

    override fun drawUi(c: Canvas) {
        UiKit.header(c, w, "Store")
        UiKit.goldPill(c, w - 10f, 23f, game.save.gold)
        val items = StoreCatalog.of(category)
        items.forEachIndexed { i, item ->
            val p = cardPos(i)
            val appear = Ease.outBack(((switchTime - i * 0.04f) / 0.3f).coerceIn(0f, 1f))
            if (appear <= 0f) return@forEachIndexed
            c.save()
            val s = appear * (if (i == pressed) 1f - tabAnim * 0.05f else 1f)
            c.scale(s, s, p[0] + cardW / 2, p[1] + cardH / 2)
            drawCard(c, item, p[0], p[1])
            c.restore()
        }
        drawButtons(c)
        if (toastTime > 0f) {
            val a = (toastTime / 0.3f).coerceAtMost(1f)
            val tw = Ui.width(toast, 10f) + 28
            val ty = h - 40f + (1f - a) * 10f
            Ui.panel(c, (w - tw) / 2, ty, tw, 24f, 12f, Ui.withAlpha(Ui.BG_STRONG, a))
            Ui.text(c, toast, w / 2f, ty + 12, 10f, Ui.TEXT, Ui.Align.CENTER, alpha = a)
        }
    }

    private fun drawCard(c: Canvas, item: StoreItem, x: Float, y: Float) {
        Ui.panel(c, x, y, cardW, cardH, 12f)
        val pvH = cardH - 38f
        val cx = x + cardW / 2
        val cy = y + 4 + pvH / 2
        if (item.category == StoreItem.Category.TILESETS) {
            // Mini 3D block stack in the tileset's colours (pixel art).
            val size = 14f
            val d = 4f
            val layout = listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 1 to 1, 1 to 2)
            for ((i, pos) in layout.withIndex()) {
                val col = item.colors[i % item.colors.size]
                val bx = (cx - 24 + pos.first * size).toInt().toFloat()
                val by = (cy + 16 - (pos.second + 1) * size).toInt().toFloat()
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
            Sprites.crew.forEachIndexed { i, m ->
                val bx = cx - 30 + i * 20
                val bob = if ((switchTime * 3f + i).toInt() % 2 == 0) 0f else -1f
                if (item.available) Draw.bitmap(c, m.body, bx, cy - 12 + bob)
                else Draw.silhouette(c, m.body, bx, cy - 12 + bob, 1f, item.colors[i % item.colors.size])
            }
        }
        if (!item.available) {
            val tw = Ui.width("Soon", 7f, Ui.Weight.BOLD) + 12f
            Ui.roundRect(c, x + cardW - tw - 6, y + 6, tw, 13f, 6.5f, Ui.withAlpha(Ui.PURPLE, 0.9f))
            Ui.text(c, "Soon", x + cardW - 6 - tw / 2, y + 12.5f, 7f, Ui.TEXT, Ui.Align.CENTER, Ui.Weight.BOLD)
        }
        Ui.text(c, item.name, cx, y + cardH - 26, 9.5f, Ui.TEXT, Ui.Align.CENTER, Ui.Weight.BOLD)
        if (item.available) {
            Icons.draw(c, Icon.CHECK, cx - Ui.width("Equipped", 8f) / 2 - 7, y + cardH - 12, 9f, Ui.GREEN)
            Ui.text(c, "Equipped", cx + 4, y + cardH - 12, 8f, Ui.GREEN, Ui.Align.CENTER)
        } else {
            val price = UiKit.formatGold(item.price)
            val pw = Ui.width(price, 8f) + 12
            Ui.coin(c, cx - pw / 2 + 4, y + cardH - 12, 4f)
            Ui.text(c, price, cx - pw / 2 + 12, y + cardH - 12, 8f, Ui.TEXT_DIM)
        }
    }
}
