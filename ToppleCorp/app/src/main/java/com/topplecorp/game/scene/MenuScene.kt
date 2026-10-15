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
import com.topplecorp.game.gfx.Themes
import com.topplecorp.game.gfx.TowerRenderer
import com.topplecorp.game.gfx.VisualPiece
import com.topplecorp.game.ui.Button
import com.topplecorp.game.ui.Ease
import com.topplecorp.game.ui.Icon
import com.topplecorp.game.ui.Touch
import com.topplecorp.game.ui.TouchAction
import com.topplecorp.game.ui.Ui
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
        val bw = (w - 48f).coerceAtMost(220f)
        val bx = (w - bw) / 2f
        val by = h * 0.69f
        buttons += Button(bx, by, bw, 44f, "Campaign", Icon.PLAY, Ui.ACCENT, textSize = 15f) {
            game.goTo { CityScene(game) }
        }.apply { primary = true; radius = 12f; iconSize = 16f }
        val gearW = 44f
        buttons += Button(bx, by + 54, bw - gearW - 10, 40f, "Store", Icon.CART, Ui.BLUE, textSize = 13f) {
            game.goTo { StoreScene(game) }
        }.apply { radius = 12f; iconSize = 16f }
        buttons += Button(bx + bw - gearW, by + 54, gearW, 40f, icon = Icon.GEAR, color = Ui.TEXT) {
            game.goTo { SettingsScene(game) }
        }.apply { radius = 12f; iconSize = 20f }
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
        val groundY = h * 0.69f - 56f
        backdrop.draw(c, h, groundY, 0f)
        backdrop.drawGround(c, groundY, h)
        val towerW = 5 * renderer.cell
        val ox = w * 0.5f - towerW / 2f + 52f
        renderer.drawShadow(c, tower, ox, groundY)
        renderer.draw(c, tower, ox, groundY, time)
        crew.draw(c, w * 0.5f - 56f, groundY + 40f, time, 2f)
    }

    override fun drawUi(c: Canvas) {
        // Title with a soft 3D extrusion
        val t = Ease.outBack((intro / 0.7f).coerceIn(0f, 1f))
        val titleY = 70f + (1f - t) * -90f + sin(time * 1.6f) * 1.5f
        for (d in 4 downTo 1) {
            Ui.text(c, "TOPPLE", w / 2f + d * 0.5f, titleY + d * 0.8f, 40f, 0xFFC2551E.toInt(), Ui.Align.CENTER, Ui.Weight.BOLD, spacing = 0.04f)
        }
        Ui.text(c, "TOPPLE", w / 2f, titleY, 40f, Ui.GOLD, Ui.Align.CENTER, Ui.Weight.BOLD, spacing = 0.04f)
        Ui.text(c, "CORP", w / 2f, titleY + 36f, 22f, Ui.TEXT, Ui.Align.CENTER, Ui.Weight.BOLD, spacing = 0.5f)
        Ui.panel(c, w / 2f - 68f, titleY + 56f, 136f, 16f, 8f)
        Ui.text(c, "Demolition Co. · Est. 1989", w / 2f, titleY + 64f, 7.5f, Ui.TEXT_DIM, Ui.Align.CENTER)

        val bt = Ease.outCubic(((intro - 0.25f) / 0.5f).coerceIn(0f, 1f))
        c.save()
        c.translate(0f, (1f - bt) * 140f)
        drawButtons(c)
        c.restore()

        UiKit.goldPill(c, w - 10f, 20f, game.save.gold)
        Ui.text(c, "v1.0", 10f, h - 10f, 7f, Ui.TEXT_FAINT)
    }
}
