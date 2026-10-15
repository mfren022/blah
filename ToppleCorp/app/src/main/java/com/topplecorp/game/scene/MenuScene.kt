package com.topplecorp.game.scene

import android.graphics.Canvas
import com.topplecorp.core.Cell
import com.topplecorp.core.LevelGenerator
import com.topplecorp.core.Material
import com.topplecorp.game.Game
import com.topplecorp.game.audio.Sound
import com.topplecorp.game.gfx.Backdrop
import com.topplecorp.game.gfx.CrewMood
import com.topplecorp.game.gfx.CrewRenderer
import com.topplecorp.game.gfx.Draw
import com.topplecorp.game.gfx.Pal
import com.topplecorp.game.gfx.PixelFont
import com.topplecorp.game.gfx.Sprites
import com.topplecorp.game.gfx.Themes
import com.topplecorp.game.gfx.TowerRenderer
import com.topplecorp.game.gfx.VisualPiece
import com.topplecorp.game.ui.Button
import com.topplecorp.game.ui.Ease
import com.topplecorp.game.ui.Touch
import com.topplecorp.game.ui.TouchAction
import com.topplecorp.game.ui.UiKit
import kotlin.math.sin

/** Title screen: animated tower, crew, and Campaign / Store / Settings. */
class MenuScene(game: Game) : Scene(game) {
    private val theme = Themes.of(game.save.lastCity)
    private var backdrop = Backdrop(theme, game.vw, 11 + game.save.lastCity)
    private val renderer = TowerRenderer(16)
    private val crew = CrewRenderer()
    private val tower = ArrayList<VisualPiece>()
    private var intro = 0f
    private var tapped = 0f

    init {
        // A small decorative tower.
        val shapes = listOf(
            Material.STONE to LevelGenerator.cells("XXXX"),
            Material.WOOD to LevelGenerator.cells("XX", "XX"),
            Material.BRICK to LevelGenerator.cells(".XX", "XX."),
            Material.GLASS to LevelGenerator.cells("X", "X"),
            Material.METAL to LevelGenerator.cells("XXX", ".X."),
            Material.GENERATOR to LevelGenerator.cells("XX", "XX"),
            Material.PLASTIC to LevelGenerator.cells("X..", "XXX"),
            Material.GOLD to LevelGenerator.cells("X"),
        )
        val offsets = listOf(0 to 0, 0 to 1, 2 to 1, 2 to 2, 0 to 3, 3 to 3, 1 to 5, 2 to 6)
        shapes.forEachIndexed { i, (m, cells) ->
            val (ox, oy) = offsets[i]
            tower += VisualPiece(i + 1, m, cells.map { Cell(it.x + ox, it.y + oy) }, m.hp, m.hp)
        }
        tower.forEach { it.lift = 200f + it.minY * 30f }
    }

    override fun layout() {
        buttons.clear()
        backdrop = Backdrop(theme, game.vw, 11 + game.save.lastCity)
        val bw = 150f
        val bx = (w - bw) / 2f
        val by = h * 0.70f
        buttons += Button(bx, by, bw, 30f, "CAMPAIGN", Sprites.play, Pal.ORANGE, textScale = 2) {
            game.goTo { CityScene(game) }
        }
        buttons += Button(bx, by + 42, bw / 2 - 4, 26f, "STORE", Sprites.cart, Pal.BLUE) {
            game.goTo { StoreScene(game) }
        }
        buttons += Button(bx + bw / 2 + 4, by + 42, bw / 2 - 4, 26f, "SETTINGS", Sprites.gear, Pal.PURPLE) {
            game.goTo { SettingsScene(game) }
        }
    }

    override fun enter() {
        crew.set(CrewMood.CHEER)
    }

    override fun onBack() {
        game.exitHandler()
    }

    override fun update(dt: Float) {
        super.update(dt)
        intro += dt
        backdrop.update(dt)
        crew.update(dt)
        if (intro > 1.6f && crew.mood == CrewMood.CHEER && tapped <= 0f) crew.set(CrewMood.IDLE)
        tapped -= dt
        // Drop-in animation for the tower blocks.
        for (p in tower) {
            if (p.lift > 0f) {
                p.fallVelocity += 900f * dt
                p.lift = (p.lift - p.fallVelocity * dt).coerceAtLeast(0f)
                if (p.lift == 0f) {
                    p.fallVelocity = 0f
                    p.shake = 0.6f
                    game.play(Sound.THUD, 0.25f, 1.2f)
                }
            }
            p.shake = (p.shake - dt * 3f).coerceAtLeast(0f)
        }
    }

    override fun touch(t: Touch) {
        if (handleButtons(t)) return
        if (t.action == TouchAction.UP) {
            crew.set(CrewMood.CHEER)
            tapped = 1.2f
            game.play(Sound.COIN, 0.5f, 1.3f)
        }
    }

    override fun draw(c: Canvas) {
        val groundY = h * 0.70f - 64f
        backdrop.draw(c, h, groundY, 0f)
        backdrop.drawGround(c, groundY, h)
        val towerW = 5 * renderer.cell
        val ox = w * 0.5f - towerW / 2f + 52f
        renderer.drawShadow(c, tower, ox, groundY)
        renderer.draw(c, tower, ox, groundY, time)
        crew.draw(c, w * 0.5f - 56f, groundY + 40f, time, 2f)

        // Title
        val t = Ease.outBack((intro / 0.7f).coerceIn(0f, 1f))
        val titleY = 34f + (1f - t) * -80f + sin(time * 1.6f) * 2f
        PixelFont.draw3D(c, "TOPPLE", w / 2f, titleY, Pal.GOLD, Pal.ORANGE_DARK, 4, 4)
        PixelFont.draw3D(c, "CORP", w / 2f, titleY + 36f, Pal.CREAM, Pal.GRAY_DARK, 4, 4)
        PixelFont.drawShadow(c, "DEMOLITION CO. EST. 1989", w / 2f, titleY + 72f, Pal.WHITE, 1, align = 1)

        // Bottom panel behind buttons
        val panelY = h * 0.70f - 14
        Draw.rect(c, 0f, panelY, w, h - panelY, Pal.alpha(Pal.INK, 0.35f))
        Draw.rect(c, 0f, panelY, w, 1f, Pal.alpha(Pal.WHITE, 0.15f))
        val bt = Ease.outCubic(((intro - 0.25f) / 0.5f).coerceIn(0f, 1f))
        c.save()
        c.translate(0f, (1f - bt) * 120f)
        drawButtons(c)
        c.restore()

        UiKit.goldPill(c, w - 6, 6f, game.save.gold)
        PixelFont.drawShadow(c, "V1.0", 6f, h - 12f, Pal.alpha(Pal.WHITE, 0.6f))
    }
}
