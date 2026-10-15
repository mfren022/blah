package com.topplecorp.game.scene

import android.graphics.Canvas
import com.topplecorp.core.Board
import com.topplecorp.core.Campaign
import com.topplecorp.core.Cell
import com.topplecorp.core.DamageCause
import com.topplecorp.core.GameEvent
import com.topplecorp.core.LevelDef
import com.topplecorp.core.Material
import com.topplecorp.core.Outcome
import com.topplecorp.core.SpecialEffect
import com.topplecorp.core.ToolType
import com.topplecorp.game.Game
import com.topplecorp.game.audio.Sound
import com.topplecorp.game.data.SaveData
import com.topplecorp.game.gfx.Backdrop
import com.topplecorp.game.gfx.CrewMood
import com.topplecorp.game.gfx.CrewRenderer
import com.topplecorp.game.gfx.Draw
import com.topplecorp.game.gfx.Pal
import com.topplecorp.game.gfx.Particles
import com.topplecorp.game.gfx.PixelFont
import com.topplecorp.game.gfx.Sprites
import com.topplecorp.game.gfx.Textures
import com.topplecorp.game.gfx.Themes
import com.topplecorp.game.gfx.TowerRenderer
import com.topplecorp.game.gfx.VisualPiece
import com.topplecorp.game.ui.Button
import com.topplecorp.game.ui.Ease
import com.topplecorp.game.ui.Touch
import com.topplecorp.game.ui.TouchAction
import com.topplecorp.game.ui.UiKit
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/** The demolition gameplay screen. */
class GameScene(game: Game, private val city: Int, private val index: Int) : Scene(game) {

    private enum class State { INTRO, PLAYING, ANIMATING, WON, LOST }

    private val level: LevelDef = game.levels.load(city, index)
    private var board: Board = Board.fromLevel(level)
    private val visuals = LinkedHashMap<Int, VisualPiece>()
    private val theme = Themes.of(city)
    private val backdrop = Backdrop(theme, game.vw, 31 + city * 7 + index)
    private val particles = Particles()
    private val crew = CrewRenderer()
    private val rnd = Random(index)

    private var renderer = TowerRenderer(20)
    private var originX = 0f
    private var groundY = 0f
    private val hudH = 40f
    private val toolbarH = 74f

    // Camera
    private var cam = 0f
    private var camVel = 0f
    private var maxCam = 0f
    private var dragging = false
    private var touchDown = false
    private var downX = 0f
    private var downY = 0f
    private var downCam = 0f
    private var lastY = 0f
    private var shake = 0f

    // Play state
    private var state = State.INTRO
    private var selected: ToolType = ToolType.HAMMER
    private var aim: Cell? = null
    private var aimCells: List<Cell> = emptyList()
    private val toolButtons = LinkedHashMap<ToolType, Button>()
    private var tip = ""
    private var tipFlash = 0f

    // Phase playback
    private val phases = ArrayDeque<List<GameEvent>>()
    private var phaseTimer = 0f
    private var pendingEvents: List<GameEvent>? = null
    private var falling = false
    private var toolFx: ToolType? = null
    private var toolFxTarget = Cell(0, 0)
    private var toolFxTime = 0f
    private var toolFxDur = 0f
    private val beams = ArrayList<FloatArray>() // x, y, w, h, life, color

    // Gold block
    private val hasGold = level.pieces.any { it.material == Material.GOLD }
    private var goldLost = false
    private var goldCollected = false

    // Overlays
    private var paused = false
    private var pauseAnim = 0f
    private val pauseButtons = ArrayList<Button>()
    private var hintVisible = false
    private var hintAnim = 0f
    private var introSweep = 0f
    private var resultTime = -1f
    private var reward: SaveData.Reward? = null
    private var shownGold = 0f
    private var starsShown = 0
    private val resultButtons = ArrayList<Button>()
    private var endDelay = 0f
    private var lostReason = ""

    init {
        syncVisuals()
        selected = level.tools.keys.firstOrNull() ?: ToolType.HAMMER
    }

    // ------------------------------------------------------------------ layout

    override fun layout() {
        buttons.clear()
        val margin = 16
        val maxCell = ((w - margin * 2) / (level.width + 0.3f)).toInt()
        val cell = (maxCell / 2 * 2).coerceIn(14, 34)
        renderer = TowerRenderer(cell)
        originX = floor((w - level.width * cell - renderer.depth) / 2f)
        groundY = h - toolbarH - 46f
        val towerPx = level.height * cell + renderer.depth + 30f
        maxCam = (towerPx - (groundY - hudH)).coerceAtLeast(0f)
        particles.groundY = groundY + 20f

        buttons += Button(5f, 5f, 30f, 26f, icon = Sprites.pause, color = Pal.PANEL_LIGHT) { setPaused(true) }.apply { iconScale = 2f }

        // Tool bar
        toolButtons.clear()
        val tools = level.tools.keys.toList()
        val n = tools.size.coerceAtLeast(1)
        val slotW = ((w - 16f - (n - 1) * 6f) / n).coerceAtMost(52f)
        val total = n * slotW + (n - 1) * 6f
        var x = (w - total) / 2f
        for (t in tools) {
            val b = Button(x, h - 58f, slotW, 44f, icon = Sprites.tools[t], color = Pal.PANEL_LIGHT, sound = Sound.SELECT) {
                selectTool(t)
            }
            b.iconScale = if (slotW >= 30f) 2f else 1f
            toolButtons[t] = b
            buttons += b
            x += slotW + 6f
        }
        refreshToolButtons()

        // Pause menu
        pauseButtons.clear()
        val pw = 176f
        val px = (w - pw) / 2f
        val py = h / 2f - 68f
        pauseButtons += Button(px, py, pw, 32f, "RESUME", Sprites.play, Pal.GREEN, textScale = 2) { setPaused(false) }
        pauseButtons += Button(px, py + 44, pw, 32f, "RESTART", Sprites.retry, Pal.ORANGE, textScale = 2) { restart() }
        pauseButtons += Button(px, py + 88, pw, 32f, "LEVELS", Sprites.list, Pal.BLUE, textScale = 2) { toLevels() }
        pauseButtons += Button(px, py + 132, pw, 32f, if (game.save.soundOn) "SOUND ON" else "SOUND OFF",
            if (game.save.soundOn) Sprites.sound else Sprites.mute, Pal.PURPLE, textScale = 2) { toggleSound() }
        pauseButtons.forEach { it.iconScale = 2f }
    }

    override fun enter() {
        hintVisible = level.hint != null && game.save.showHints
        introSweep = if (maxCam > 0f) 1f else 0f
        cam = maxCam * introSweep
        state = State.INTRO
        crew.set(CrewMood.IDLE)
        updateTip()
    }

    private fun toggleSound() {
        game.save.soundOn = !game.save.soundOn
        game.sfx.enabled = game.save.soundOn
        pauseButtons[3].label = if (game.save.soundOn) "SOUND ON" else "SOUND OFF"
        pauseButtons[3].icon = if (game.save.soundOn) Sprites.sound else Sprites.mute
    }

    private fun setPaused(p: Boolean) {
        if (state == State.WON || state == State.LOST) return
        paused = p
    }

    private fun restart() = game.goTo { GameScene(game, city, index) }
    private fun toLevels() = game.goTo { LevelSelectScene(game, city) }

    override fun onBack() {
        when {
            state == State.WON || state == State.LOST -> toLevels()
            hintVisible -> dismissHint()
            else -> { setPaused(!paused); game.play(Sound.CLICK) }
        }
    }

    // ------------------------------------------------------------------ visuals

    private fun syncVisuals() {
        val alive = HashSet<Int>()
        for (p in board.pieces) {
            alive += p.id
            val v = visuals[p.id]
            if (v == null) {
                visuals[p.id] = VisualPiece(p.id, p.material, p.cells.toList(), p.hp, p.maxHp)
            } else {
                if (v.cells != p.cells) { v.cells = p.cells.toList(); v.rebuild() }
                v.hp = p.hp
                v.lift = 0f
            }
        }
        visuals.keys.retainAll(alive)
    }

    private fun cellCenterX(x: Float) = originX + (x + 0.5f) * renderer.cell
    private fun cellCenterY(y: Float) = groundY - (y + 0.5f) * renderer.cell
    private fun pieceCenter(v: VisualPiece) =
        cellCenterX((v.minX + v.maxX) / 2f) to cellCenterY((v.minY + v.maxY) / 2f)

    // ------------------------------------------------------------------ tools & aiming

    private fun selectTool(t: ToolType) {
        if (state != State.PLAYING && state != State.INTRO) return
        if (!board.canUse(t)) { game.play(Sound.DENY); tip = "No ${t.displayName}s left!"; tipFlash = 1f; return }
        selected = t
        aim = null
        aimCells = emptyList()
        refreshToolButtons()
        updateTip()
    }

    private fun refreshToolButtons() {
        for ((t, b) in toolButtons) {
            val c = board.charges[t] ?: 0
            b.selected = t == selected
            b.enabled = c != 0
            b.badge = if (c < 0) null else "$c"
            b.color = if (t == selected) Pal.ORANGE else Pal.PANEL_LIGHT
        }
    }

    private fun updateTip() {
        tip = when {
            aim != null -> "TAP AGAIN TO STRIKE!"
            else -> selected.displayName.uppercase() + ": " + selected.description
        }
    }

    private fun screenToCell(x: Float, y: Float): Cell {
        val worldY = y - cam
        val cx = floor((x - originX) / renderer.cell).toInt()
        val cy = floor((groundY - worldY) / renderer.cell).toInt()
        return Cell(cx, cy)
    }

    private fun sameTarget(a: Cell, b: Cell): Boolean = when (selected) {
        ToolType.LASER -> a.x == b.x
        ToolType.WRECKING_BALL -> a.y == b.y
        else -> board.pieceAt(a.x, a.y)?.id?.let { it == board.pieceAt(b.x, b.y)?.id } ?: false
    }

    private fun tapBoard(x: Float, y: Float) {
        if (state != State.PLAYING) return
        val cell = screenToCell(x, y)
        val target = when (selected) {
            ToolType.LASER -> Cell(cell.x, 0)
            ToolType.WRECKING_BALL -> Cell(0, cell.y)
            else -> cell
        }
        if (!board.isValidTarget(selected, target)) {
            if (aim != null) { aim = null; aimCells = emptyList(); updateTip() }
            return
        }
        val current = aim
        if (current != null && sameTarget(current, target)) {
            strike(current)
            return
        }
        aim = target
        aimCells = board.affectedCells(selected, target)
        game.play(Sound.AIM, 0.6f)
        if (selected == ToolType.HAMMER && board.pieceAt(target.x, target.y)?.material?.hammerImmune == true) {
            tip = "HAMMERS CAN'T DENT THAT!"; tipFlash = 1f
        } else updateTip()
    }

    private fun strike(target: Cell) {
        val tool = selected
        val result = board.applyTool(tool, target)
        aim = null
        aimCells = emptyList()
        updateTip()
        if (result.isEmpty()) return
        phases.clear()
        phases.addAll(result)
        state = State.ANIMATING
        crew.workerTool = tool
        crew.set(CrewMood.WORK, crew.workerFor(tool))
        refreshToolButtons()
        phaseTimer = 0f
        nextPhase()
    }

    // ------------------------------------------------------------------ phase playback

    private fun toolDuration(t: ToolType) = when (t) {
        ToolType.HAMMER -> 0.22f
        ToolType.DRILL -> 0.42f
        ToolType.DYNAMITE -> 0.5f
        ToolType.LASER -> 0.36f
        ToolType.WRECKING_BALL -> 0.5f
    }

    private fun nextPhase() {
        val events = phases.removeFirstOrNull()
        if (events == null) { finishTurn(); return }
        val tool = events.filterIsInstance<GameEvent.ToolUsed>().firstOrNull()
        if (tool != null) {
            toolFx = tool.tool
            toolFxTarget = tool.target
            toolFxTime = 0f
            toolFxDur = toolDuration(tool.tool)
            phaseTimer = toolFxDur
            pendingEvents = events
            game.play(when (tool.tool) {
                ToolType.HAMMER -> Sound.WHOOSH
                ToolType.DRILL -> Sound.DRILL
                ToolType.DYNAMITE -> Sound.DYNAMITE
                ToolType.LASER -> Sound.LASER
                ToolType.WRECKING_BALL -> Sound.WRECK
            }, if (tool.tool == ToolType.HAMMER) 0.4f else 0.9f)
            return
        }
        applyEvents(events)
    }

    private fun applyEvents(events: List<GameEvent>) {
        var duration = 0.12f
        falling = false
        var crushSound = false
        var thudSound = false
        for (e in events) when (e) {
            is GameEvent.ToolUsed -> onToolImpact(e)
            is GameEvent.Damaged -> {
                val v = visuals[e.pieceId] ?: continue
                v.hp = e.hpLeft
                v.flash = 1f
                v.shake = 1f
                val (cx, cy) = pieceCenter(v)
                if (e.amount <= 0) {
                    particles.floatText(cx, cy - 6, "CLANK!", Pal.GRAY)
                    particles.sparks(cx, cy, Pal.WHITE, 6)
                    game.play(Sound.CLANK, 0.8f)
                } else {
                    particles.floatText(cx, cy - 6, "-${e.amount}", if (e.cause == DamageCause.TOOL) Pal.CREAM else Pal.ORANGE)
                    particles.debris(cx, cy, Textures.baseColor(v.material, v.variant), 3, 0.6f)
                    when (e.cause) {
                        DamageCause.TOOL -> game.play(Sound.HAMMER, 0.7f, 0.9f + rnd.nextFloat() * 0.2f)
                        DamageCause.CRUSH -> crushSound = true
                        DamageCause.FALL -> thudSound = true
                        else -> {}
                    }
                }
                duration = maxOf(duration, 0.2f)
            }
            is GameEvent.Destroyed -> {
                val v = visuals.remove(e.pieceId)
                val base = Textures.baseColor(e.material, v?.variant ?: 0)
                for (c in e.cells) particles.debris(cellCenterX(c.x.toFloat()), cellCenterY(c.y.toFloat()), base, 5)
                particles.dust(cellCenterX(e.cells.minOf { it.x }.toFloat()) - renderer.cell / 2f,
                    cellCenterY(e.cells.minOf { it.y }.toFloat()) + renderer.cell / 2f,
                    (e.cells.maxOf { it.x } - e.cells.minOf { it.x } + 1f) * renderer.cell, Pal.CREAM)
                game.play(breakSound(e.material), 0.9f, 0.9f + rnd.nextFloat() * 0.2f)
                if (e.material == Material.GOLD) {
                    goldLost = true
                    val (cx, cy) = e.cells.first().let { cellCenterX(it.x.toFloat()) to cellCenterY(it.y.toFloat()) }
                    particles.floatText(cx, cy - 10, "GOLD LOST!", Pal.RED)
                    game.play(Sound.DENY)
                }
                shake = maxOf(shake, 1.5f)
                game.vibrate(15, 60)
                duration = maxOf(duration, 0.24f)
            }
            is GameEvent.Effect -> {
                onEffect(e)
                duration = maxOf(duration, 0.4f)
            }
            is GameEvent.Fell -> {
                val v = visuals[e.pieceId] ?: continue
                v.cells = v.cells.map { Cell(it.x, it.y - e.distance) }
                v.rebuild()
                v.lift += e.distance * renderer.cell
                v.fallVelocity = 0f
                falling = true
            }
        }
        if (crushSound) { game.play(Sound.CRUSH, 0.9f); shake = maxOf(shake, 3f); game.vibrate(30, 140) }
        if (thudSound) game.play(Sound.THUD, 0.8f)
        if (falling) {
            crew.set(CrewMood.SCARED)
            duration = 0.05f
        }
        phaseTimer = duration
        refreshToolButtons()
    }

    private fun onToolImpact(e: GameEvent.ToolUsed) {
        val cs = renderer.cell
        when (e.tool) {
            ToolType.LASER -> {
                beams += floatArrayOf(originX + e.target.x * cs, -cam - 40f, cs.toFloat(), groundY + cam + 40f, 0.35f, Pal.PURPLE.toFloat())
                for (y in 0 until level.height) particles.sparks(cellCenterX(e.target.x.toFloat()), cellCenterY(y.toFloat()), 0xFFE0D0FF.toInt(), 2)
                shake = maxOf(shake, 2f)
            }
            ToolType.WRECKING_BALL -> {
                shake = maxOf(shake, 4f)
                game.vibrate(40, 180)
            }
            ToolType.DYNAMITE -> {
                val cx = cellCenterX(e.target.x.toFloat()); val cy = cellCenterY(e.target.y.toFloat())
                particles.sparks(cx, cy, Pal.GOLD, 30, 220f)
                particles.sparks(cx, cy, Pal.ORANGE, 20, 160f)
                shake = maxOf(shake, 5f)
                game.vibrate(60, 200)
            }
            else -> {
                val cx = cellCenterX(e.target.x.toFloat()); val cy = cellCenterY(e.target.y.toFloat())
                particles.sparks(cx, cy, Pal.CREAM, 8)
                shake = maxOf(shake, 1.2f)
            }
        }
    }

    private fun onEffect(e: GameEvent.Effect) {
        val o = e.origin.firstOrNull() ?: return
        val ox = cellCenterX(o.x.toFloat()); val oy = cellCenterY(o.y.toFloat())
        when (e.effect) {
            SpecialEffect.SHOCK -> {
                game.play(Sound.SHOCK)
                for (c in e.area) particles.sparks(cellCenterX(c.x.toFloat()), cellCenterY(c.y.toFloat()), 0xFFFFF27A.toInt(), 5, 90f)
                particles.floatText(ox, oy - 8, "ZAP!", Pal.GOLD)
                shake = maxOf(shake, 3f)
            }
            SpecialEffect.EXPLODE -> {
                game.play(Sound.EXPLOSION)
                particles.sparks(ox, oy, Pal.GOLD, 40, 260f)
                particles.sparks(ox, oy, Pal.ORANGE, 30, 200f)
                particles.debris(ox, oy, Pal.GRAY_DARK, 16, 1.5f)
                particles.floatText(ox, oy - 8, "BOOM!", Pal.ORANGE)
                shake = maxOf(shake, 7f)
                game.vibrate(80, 255)
            }
            SpecialEffect.FLOOD -> {
                game.play(Sound.SPLASH)
                for (c in e.area) particles.water(cellCenterX(c.x.toFloat()), cellCenterY(c.y.toFloat()), 4)
                particles.floatText(ox, oy - 8, "SPLASH!", Pal.BLUE)
            }
            SpecialEffect.BONUS_TURN -> {
                game.play(Sound.BONUS)
                particles.floatText(ox, oy - 8, "+1 TURN!", Pal.TEAL)
                particles.sparks(ox, oy, 0xFFBFF6FF.toInt(), 16)
            }
            SpecialEffect.BONUS_GOLD -> {
                game.play(Sound.COIN)
                particles.floatText(ox, oy - 8, "+100 GOLD", Pal.GOLD)
                particles.sparks(ox, oy, Pal.GOLD, 24, 150f)
            }
            SpecialEffect.GOLD -> {
                goldCollected = true
                // The crew grabs it: remove the gold block visual.
                visuals.values.removeAll { v -> v.material == Material.GOLD && v.cells.any { it in e.origin } }
                game.play(Sound.GOLD)
                particles.floatText(ox, oy - 10, "GOLD BLOCK!", Pal.GOLD)
                particles.sparks(ox, oy, Pal.GOLD, 30, 180f)
                crew.set(CrewMood.CHEER)
                game.vibrate(40, 120)
            }
            SpecialEffect.NONE -> {}
        }
    }

    private fun breakSound(m: Material) = when (m) {
        Material.GLASS -> Sound.BREAK_GLASS
        Material.WOOD, Material.TNT -> Sound.BREAK_WOOD
        Material.PLASTIC -> Sound.BREAK_PLASTIC
        Material.BRICK, Material.STONE -> Sound.BREAK_STONE
        Material.METAL, Material.STEEL, Material.VAULT, Material.GENERATOR, Material.AC_UNIT, Material.WATER_TANK -> Sound.BREAK_METAL
        Material.GOLD -> Sound.BREAK_GLASS
    }

    private fun finishTurn() {
        syncVisuals()
        when (board.outcome) {
            Outcome.WON -> { state = State.WON; endDelay = 0.7f; crew.set(CrewMood.CHEER) }
            Outcome.LOST -> {
                state = State.LOST
                endDelay = 0.7f
                crew.set(CrewMood.SAD)
                lostReason = if (board.turnsLeft <= 0) "OUT OF TURNS!" else "NO TOOL CAN DAMAGE WHAT'S LEFT!"
            }
            Outcome.PLAYING -> {
                state = State.PLAYING
                if (crew.mood != CrewMood.CHEER) crew.set(CrewMood.IDLE)
                if (!board.canUse(selected)) selected = level.tools.keys.first { board.canUse(it) }
                refreshToolButtons()
                updateTip()
            }
        }
    }

    // ------------------------------------------------------------------ results

    private fun showResults() {
        resultTime = 0f
        resultButtons.clear()
        val pw = 200f
        val px = (w - pw) / 2f
        val by = h / 2f + 64f
        if (state == State.WON) {
            reward = game.save.completeLevel(city, index, board.starsEarned, board.goldCollected, board.bonusGold)
            game.play(Sound.WIN)
            val hasNext = index + 1 < Campaign.LEVELS_PER_CITY
            resultButtons += Button(px, by, 56f, 34f, icon = Sprites.list, color = Pal.BLUE) { toLevels() }
            resultButtons += Button(px + 62, by, 56f, 34f, icon = Sprites.retry, color = Pal.PURPLE) { restart() }
            resultButtons += Button(px + 124, by, 96f, 34f, if (hasNext) "NEXT" else "CITIES", Sprites.play, Pal.GREEN,
                textScale = if (hasNext) 2 else 1) {
                if (hasNext) game.goTo { GameScene(game, city, index + 1) } else game.goTo { CityScene(game) }
            }
        } else {
            game.play(Sound.LOSE)
            resultButtons += Button(px, by, 104f, 34f, "LEVELS", Sprites.list, Pal.BLUE, textScale = 2) { toLevels() }
            resultButtons += Button(px + 112, by, 108f, 34f, "RETRY", Sprites.retry, Pal.ORANGE, textScale = 2) { restart() }
        }
        resultButtons.forEach { it.iconScale = 2f }
    }

    // ------------------------------------------------------------------ update

    override fun update(dt: Float) {
        super.update(dt)
        backdrop.update(dt)
        crew.update(dt)
        particles.update(dt)
        pauseButtons.forEach { it.update(dt) }
        resultButtons.forEach { it.update(dt) }
        pauseAnim = Ease.approach(pauseAnim, if (paused) 1f else 0f, 16f, dt)
        hintAnim = Ease.approach(hintAnim, if (hintVisible) 1f else 0f, 14f, dt)
        tipFlash = (tipFlash - dt * 1.5f).coerceAtLeast(0f)
        shake = (shake - dt * 14f).coerceAtLeast(0f)
        if (paused) return

        // Intro camera sweep from the top of the tower down to the street.
        if (state == State.INTRO) {
            if (introSweep > 0f && time > 0.4f) {
                introSweep = (introSweep - dt / 1.3f).coerceAtLeast(0f)
                cam = maxCam * Ease.inOutCubic(introSweep)
            }
            if (introSweep <= 0f && !hintVisible) state = State.PLAYING
        } else if (!dragging) {
            cam += camVel * dt
            camVel *= (1f - dt * 5f).coerceAtLeast(0f)
            if (cam < 0f) { cam = Ease.approach(cam, 0f, 18f, dt); camVel = 0f }
            if (cam > maxCam) { cam = Ease.approach(cam, maxCam, 18f, dt); camVel = 0f }
        }

        for (v in visuals.values) {
            v.flash = (v.flash - dt * 5f).coerceAtLeast(0f)
            v.shake = (v.shake - dt * 4f).coerceAtLeast(0f)
        }

        // Tool animation
        if (toolFx != null) {
            toolFxTime += dt
            if (toolFxTime >= toolFxDur) toolFx = null
        }
        val bi = beams.iterator()
        while (bi.hasNext()) { val b = bi.next(); b[4] -= dt; if (b[4] <= 0f) bi.remove() }

        if (state == State.ANIMATING) {
            if (falling) {
                var any = false
                var landed = 0f
                for (v in visuals.values) {
                    if (v.lift <= 0f) continue
                    any = true
                    v.fallVelocity += 1400f * dt
                    v.lift -= v.fallVelocity * dt
                    if (v.lift <= 0f) {
                        v.lift = 0f
                        landed = maxOf(landed, v.fallVelocity)
                        val (cx, _) = pieceCenter(v)
                        particles.dust(cx - renderer.cell / 2f, groundY - v.minY * renderer.cell.toFloat(), renderer.cell.toFloat(), Pal.CREAM)
                        v.fallVelocity = 0f
                    }
                }
                if (landed > 0f) {
                    game.play(Sound.THUD, (landed / 600f).coerceIn(0.3f, 1f))
                    shake = maxOf(shake, (landed / 160f).coerceAtMost(4f))
                }
                if (!any) { falling = false; phaseTimer = 0.06f }
            } else {
                phaseTimer -= dt
                if (phaseTimer <= 0f) {
                    val pending = pendingEvents
                    if (pending != null) {
                        pendingEvents = null
                        applyEvents(pending)
                    } else nextPhase()
                }
            }
        }

        if ((state == State.WON || state == State.LOST) && resultTime < 0f) {
            endDelay -= dt
            if (endDelay <= 0f) showResults()
        }
        if (resultTime >= 0f) {
            resultTime += dt
            val r = reward
            if (state == State.WON && r != null) {
                val stars = board.starsEarned
                val due = ((resultTime - 0.45f) / 0.32f).toInt().coerceIn(0, stars)
                if (due > starsShown) {
                    starsShown = due
                    game.play(Sound.STAR, 1f, 1f + starsShown * 0.12f)
                }
                if (resultTime > 0.45f + stars * 0.32f && shownGold < r.total) {
                    val before = shownGold.toInt()
                    shownGold = (shownGold + dt * (r.total * 1.4f + 200f)).coerceAtMost(r.total.toFloat())
                    if (shownGold.toInt() / 100 != before / 100) game.play(Sound.COIN, 0.5f, 1.2f)
                }
            }
        }
    }

    // ------------------------------------------------------------------ input

    override fun touch(t: Touch) {
        if (resultTime >= 0f) {
            if (resultTime > 0.5f) for (b in resultButtons) if (b.touch(t) { s: Sound -> game.play(s) }) return
            return
        }
        if (paused) {
            for (b in pauseButtons) if (b.touch(t) { s: Sound -> game.play(s) }) return
            return
        }
        if (hintVisible) {
            if (t.action == TouchAction.UP) dismissHint()
            return
        }
        if (handleButtons(t)) return
        val inPlay = t.y in hudH..(h - toolbarH)
        when (t.action) {
            TouchAction.DOWN -> if (inPlay) {
                touchDown = true
                dragging = false
                downX = t.x; downY = t.y; lastY = t.y
                downCam = cam
                camVel = 0f
            }
            TouchAction.MOVE -> if (touchDown) {
                if (!dragging && (abs(t.y - downY) > 6f || abs(t.x - downX) > 10f)) dragging = true
                if (dragging && maxCam > 0f) {
                    cam = (downCam + (t.y - downY)).coerceIn(-30f, maxCam + 30f)
                    camVel = camVel * 0.5f + ((t.y - lastY) * 60f) * 0.5f
                    if (state == State.INTRO) { introSweep = 0f }
                }
                lastY = t.y
            }
            TouchAction.UP -> {
                if (touchDown && !dragging) tapBoard(t.x, t.y)
                touchDown = false
                dragging = false
            }
            TouchAction.CANCEL -> { touchDown = false; dragging = false }
        }
    }

    private fun dismissHint() {
        hintVisible = false
        game.play(Sound.CLICK)
    }

    // ------------------------------------------------------------------ draw

    override fun draw(c: Canvas) {
        val sx = if (shake > 0f) (rnd.nextFloat() - 0.5f) * shake * 2f else 0f
        val sy = if (shake > 0f) (rnd.nextFloat() - 0.5f) * shake * 2f else 0f
        backdrop.draw(c, h, groundY + cam + 8, cam)

        c.save()
        c.translate(sx, cam + sy)
        backdrop.drawGround(c, groundY, groundY + toolbarH + 40f)
        renderer.drawShadow(c, visuals.values, originX, groundY)
        drawAim(c, before = true)
        renderer.draw(c, visuals.values, originX, groundY, time)
        drawAim(c, before = false)
        drawToolFx(c)
        crew.draw(c, w / 2f, groundY + 42f, time, 2f)
        c.restore()
        particles.draw(c, cam + sy)

        drawHud(c)
        drawToolbar(c)
        if (hintAnim > 0.01f) drawHint(c)
        if (pauseAnim > 0.01f) drawPause(c)
        if (resultTime >= 0f) drawResults(c)
    }

    private fun drawAim(c: Canvas, before: Boolean) {
        if (aimCells.isEmpty() || state != State.PLAYING) return
        val cs = renderer.cell.toFloat()
        val pulse = 0.35f + 0.25f * sin(time * 10f)
        if (before) {
            // Column / row guide for area tools.
            if (selected == ToolType.LASER) {
                val x = originX + aimCells.first().x * cs
                Draw.rect(c, x, -cam, cs, groundY + cam, Pal.alpha(Pal.PURPLE, 0.18f))
            } else if (selected == ToolType.WRECKING_BALL) {
                val y = groundY - (aimCells.first().y + 1) * cs
                Draw.rect(c, 0f, y, w, cs, Pal.alpha(Pal.ORANGE, 0.18f))
            }
            return
        }
        for (cell in aimCells) {
            val x = originX + cell.x * cs
            val y = groundY - (cell.y + 1) * cs
            if (board.pieceAt(cell.x, cell.y) == null) continue
            Draw.rect(c, x, y, cs, cs, Pal.alpha(Pal.GOLD, pulse * 0.6f))
            Draw.rect(c, x, y, cs, 1f, Pal.GOLD); Draw.rect(c, x, y + cs - 1, cs, 1f, Pal.GOLD)
            Draw.rect(c, x, y, 1f, cs, Pal.GOLD); Draw.rect(c, x + cs - 1, y, 1f, cs, Pal.GOLD)
        }
        // Projected damage labels
        val ids = aimCells.mapNotNull { board.pieceAt(it.x, it.y) }.distinct()
        for (p in ids) {
            val v = visuals[p.id] ?: continue
            val (cx, cy) = pieceCenter(v)
            val dmg = selected.damageAgainst(p.material)
            val kills = dmg >= p.hp
            val label = if (kills) "X" else "-$dmg"
            val col = when { dmg == 0 -> Pal.GRAY; kills -> Pal.RED; else -> Pal.CREAM }
            Draw.panel(c, cx - 8, cy - 6, 16f, 11f, Pal.INK, 0f)
            PixelFont.draw(c, label, cx - PixelFont.width(label) / 2f, cy - 4, col)
            // HP pips
            for (i in 0 until p.maxHp.coerceAtMost(8)) {
                Draw.rect(c, cx - p.maxHp.coerceAtMost(8) * 1.5f + i * 3f, cy + 6, 2f, 2f, if (i < p.hp) Pal.GREEN else Pal.INK)
            }
        }
        // Reticle on the exact target
        aim?.let { a ->
            if (selected == ToolType.LASER || selected == ToolType.WRECKING_BALL) return
            val x = originX + a.x * cs; val y = groundY - (a.y + 1) * cs
            val o = 2f + sin(time * 10f) * 1.5f
            val l = 4f
            for ((dx, dy) in listOf(-o to -o, cs + o - l to -o, -o to cs + o - 1, cs + o - l to cs + o - 1)) {
                Draw.rect(c, x + dx, y + dy, l, 1f, Pal.WHITE)
            }
            for ((dx, dy) in listOf(-o to -o, cs + o - 1 to -o, -o to cs + o - l, cs + o - 1 to cs + o - l)) {
                Draw.rect(c, x + dx, y + dy, 1f, l, Pal.WHITE)
            }
        }
    }

    private fun drawToolFx(c: Canvas) {
        val cs = renderer.cell.toFloat()
        for (b in beams) {
            val a = (b[4] / 0.35f).coerceIn(0f, 1f)
            val grow = 1f + (1f - a) * 0.6f
            val bw = b[2] * grow
            val bx = b[0] + b[2] / 2 - bw / 2
            Draw.rect(c, bx, b[1], bw, b[3], Pal.alpha(Pal.PURPLE, 0.45f * a))
            Draw.rect(c, bx + bw * 0.3f, b[1], bw * 0.4f, b[3], Pal.alpha(0xFFE9DDFF.toInt(), a))
        }
        val tool = toolFx ?: return
        val p = (toolFxTime / toolFxDur).coerceIn(0f, 1f)
        val tx = originX + toolFxTarget.x * cs
        val ty = groundY - (toolFxTarget.y + 1) * cs
        val icon = Sprites.tools[tool] ?: return
        when (tool) {
            ToolType.HAMMER -> {
                c.save()
                val angle = -70f + 90f * Ease.inOutCubic(p)
                c.rotate(angle, tx + cs, ty + cs / 2)
                Draw.bitmap(c, icon, tx + cs - 4, ty - 14, 2f)
                c.restore()
            }
            ToolType.DRILL -> {
                val jitter = sin(time * 80f) * 1f
                c.save()
                c.scale(-1f, 1f, tx + cs / 2, 0f)
                Draw.bitmap(c, icon, tx - 24 + jitter + p * 6f, ty + cs / 2 - 12, 2f)
                c.restore()
                if (rnd.nextFloat() < 0.6f) particles.sparks(tx + cs / 2, ty + cs / 2, Pal.GOLD, 1, 80f)
            }
            ToolType.DYNAMITE -> {
                if (p < 0.6f) {
                    Draw.bitmap(c, icon, tx + cs / 2 - 6, ty + cs / 2 - 6)
                    if (rnd.nextFloat() < 0.7f) particles.sparks(tx + cs / 2 + 2, ty + cs / 2 - 6, Pal.GOLD, 1, 40f)
                } else {
                    val r = (p - 0.6f) / 0.4f * cs * 2.2f
                    Draw.rect(c, tx + cs / 2 - r, ty + cs / 2 - r, r * 2, r * 2, Pal.alpha(Pal.GOLD, (1f - p) * 1.6f))
                }
            }
            ToolType.LASER -> {
                // Emitter descends from the top of the screen.
                val ey = -cam + 4f + (1f - Ease.outCubic(p)) * -20f
                Draw.bitmap(c, icon, tx + cs / 2 - 12, ey + 30f, 2f)
            }
            ToolType.WRECKING_BALL -> {
                val bx = -30f + (w + 60f) * Ease.inOutCubic(p)
                val by = ty + cs / 2 - 12
                val pivotX = w / 2f
                val pivotY = by - 140f
                // Chain
                val steps = 14
                for (i in 0..steps) {
                    val f = i / steps.toFloat()
                    Draw.rect(c, pivotX + (bx + 12 - pivotX) * f, pivotY + (by - pivotY) * f, 2f, 2f, Pal.INK_SOFT)
                }
                Draw.bitmap(c, icon, bx, by, 2f)
            }
        }
    }

    private fun drawHud(c: Canvas) {
        Draw.rect(c, 0f, 0f, w, hudH, Pal.alpha(Pal.INK, 0.62f))
        Draw.rect(c, 0f, hudH, w, 1f, Pal.alpha(Pal.WHITE, 0.12f))
        drawButtons(c)

        PixelFont.drawShadow(c, "${city + 1}-${index + 1}", 42f, 10f, Pal.CREAM)
        val cityName = Campaign.cities[city].name.uppercase()
        if (42f + PixelFont.width(cityName) < w / 2f - 22f) PixelFont.drawShadow(c, cityName, 42f, 21f, Pal.alpha(Pal.WHITE, 0.6f))

        // Turns left
        val left = board.turnsLeft
        val urgent = left <= 2 && state != State.WON
        val turnColor = if (urgent && (time % 0.6f) < 0.3f) Pal.RED else Pal.WHITE
        val cx = w / 2f + 6f
        Draw.panel(c, cx - 26, 5f, 52f, 28f, Pal.PANEL, 2f)
        PixelFont.drawShadow(c, "$left", cx, 9f, turnColor, 2, align = 1)
        PixelFont.draw(c, "TURNS", cx - PixelFont.width("TURNS") / 2f, 24f, Pal.alpha(Pal.CREAM, 0.8f))

        // Star outlook: which rating the player is still on track for.
        val used = board.turnsUsed
        val potential = level.turns.starsFor(used).coerceAtLeast(if (used <= level.turns.max) 1 else 0)
        val sx = cx + 32
        UiKit.stars(c, sx + 15, 8f, potential, 1f, 0f)
        val nextLimit = when (potential) { 3 -> level.turns.three; 2 -> level.turns.two; else -> level.turns.max }
        val remain = (nextLimit - used).coerceAtLeast(0)
        PixelFont.draw(c, if (remain == 1) "1 TURN" else "$remain TURNS", sx, 23f, Pal.alpha(Pal.CREAM, 0.7f))

        // Gold block status
        if (hasGold) {
            val gx = w - 22f
            val gy = 14f
            when {
                goldCollected -> Draw.bitmap(c, Sprites.goldBar, gx, gy + sin(time * 5f))
                goldLost -> {
                    Draw.bitmap(c, Sprites.goldBarGray, gx, gy, 1f, 140)
                    PixelFont.draw(c, "×", gx + 3, gy, Pal.RED)
                }
                else -> Draw.bitmap(c, Sprites.goldBarGray, gx, gy)
            }
        }
    }

    private fun drawToolbar(c: Canvas) {
        val top = h - toolbarH
        Draw.rect(c, 0f, top, w, toolbarH, Pal.PANEL_DARK)
        Draw.rect(c, 0f, top, w, 1f, Pal.PANEL_LIGHT)
        Draw.rect(c, 0f, top + 1, w, 1f, Pal.alpha(Pal.WHITE, 0.08f))
        val tipColor = if (tipFlash > 0f && (time % 0.2f) < 0.1f) Pal.GOLD else Pal.CREAM
        val lines = PixelFont.wrap(tip, w - 12f)
        PixelFont.drawShadow(c, lines.firstOrNull() ?: "", w / 2f, top + 5, tipColor, align = 1)
        for (b in toolButtons.values) b.draw(c)
        for ((t, b) in toolButtons) {
            if (board.charges[t] == -1) {
                PixelFont.drawShadow(c, "∞", b.x + b.w - 8, b.y - 2, Pal.CREAM, align = 1)
            }
        }
    }

    private fun drawHint(c: Canvas) {
        val hint = level.hint ?: return
        val a = hintAnim
        Draw.dim(c, w, h, 0.5f * a)
        val pw = w - 40f
        val lines = PixelFont.wrap(hint, pw - 56f)
        val ph = (40f + lines.size * 10f).coerceAtLeast(56f)
        val px = 20f
        val py = h * 0.36f - ph / 2 + (1f - Ease.outBack(a)) * 60f
        Draw.panel(c, px, py, pw, ph, Pal.CREAM, 4f)
        Draw.panel(c, px + 8, py - 8, 64f, 14f, Pal.ORANGE, 2f)
        PixelFont.drawShadow(c, "FOREMAN", px + 40, py - 5, Pal.WHITE, align = 1, shadow = Pal.ORANGE_DARK)
        // Show what is being introduced.
        val g = Campaign.globalIndex(city, index)
        val newTool = Campaign.toolUnlock.entries.firstOrNull { it.value == g && g > 0 }?.key
        val newMat = Campaign.materialUnlock.entries.firstOrNull { it.value == g && g > 0 }?.key
        val iconX = px + 12f
        val iconY = py + 14f
        if (newTool != null) {
            Draw.bitmap(c, Sprites.tools[newTool]!!, iconX, iconY, 2f)
        } else if (newMat != null) {
            val tex = renderer.textures.get(newMat)
            Draw.bitmapRect(c, tex, iconX, iconY, 24f, 24f)
            Sprites.specials[newMat]?.let { Draw.bitmap(c, it, iconX + 12 - it.width / 2f, iconY + 12 - it.height / 2f) }
        } else {
            Draw.bitmap(c, Sprites.crew[0].body, iconX + 4, iconY - 2)
        }
        var ty = py + 16f
        for (line in lines) {
            PixelFont.draw(c, line, px + 44, ty, Pal.INK)
            ty += 10f
        }
        val blink = (time % 1f) < 0.6f
        if (blink) PixelFont.draw(c, "TAP TO START", px + pw / 2 - PixelFont.width("TAP TO START") / 2, py + ph - 14, Pal.ORANGE_DARK)
    }

    private fun drawPause(c: Canvas) {
        Draw.dim(c, w, h, 0.6f * pauseAnim)
        val off = (1f - Ease.outBack(pauseAnim)) * 40f
        c.save()
        c.translate(0f, off)
        val px = (w - 200f) / 2f
        val py = h / 2f - 108f
        Draw.panel(c, px, py, 200f, 222f, Pal.PANEL, 5f)
        PixelFont.drawShadow(c, "PAUSED", w / 2f, py + 13, Pal.CREAM, 2, align = 1)
        pauseButtons.forEach { it.draw(c) }
        c.restore()
    }

    private fun drawResults(c: Canvas) {
        val t = resultTime
        Draw.dim(c, w, h, (t * 2f).coerceAtMost(0.65f))
        val appear = Ease.outBack((t / 0.45f).coerceIn(0f, 1f))
        val pw = 220f
        val ph = 214f
        val px = (w - pw) / 2f
        val py = h / 2f - 110f - (1f - appear) * h * 0.6f
        val won = state == State.WON
        c.save()
        c.translate(0f, py - (h / 2f - 110f))
        val basePy = h / 2f - 110f
        Draw.panel(c, px, basePy, pw, ph, if (won) Pal.PANEL else Pal.PANEL, 5f)
        val ribbon = if (won) Pal.GREEN else Pal.RED
        Draw.panel(c, px - 8, basePy - 10, pw + 16, 24f, ribbon, 3f)
        PixelFont.drawShadow(c, if (won) "TOWER DOWN!" else "TOWER STANDS", w / 2f, basePy - 5, Pal.WHITE, 2, align = 1,
            shadow = Pal.shade(ribbon, 0.5f))
        if (won) {
            val stars = board.starsEarned
            for (i in 0 until 3) {
                val sx = w / 2f - 42f + i * 30f
                val pop = ((t - 0.45f - i * 0.32f) / 0.25f).coerceIn(0f, 1f)
                val big = i < stars && pop > 0f
                val sc = if (big) 2f + (1f - Ease.outElastic(pop)) * 1.5f else 2f
                val bmp = if (big) Sprites.star else Sprites.starEmpty
                val bw = bmp.width * sc
                Draw.bitmap(c, bmp, sx + 9 - bw / 2, basePy + 34 - (bmp.height * sc - 20) / 2, sc)
            }
            val r = reward ?: return c.restore()
            var ly = basePy + 66f
            fun line(label: String, value: Int, color: Int) {
                PixelFont.drawShadow(c, label, px + 16, ly, Pal.CREAM)
                PixelFont.drawShadow(c, "+${UiKit.formatGold(value)}", px + pw - 16, ly, color, align = 2)
                ly += 11f
            }
            PixelFont.drawShadow(c, "${board.turnsUsed} TURNS USED", w / 2f, ly, Pal.alpha(Pal.WHITE, 0.7f), align = 1)
            ly += 13f
            line(if (r.newBest) "${stars} STAR REWARD" else "STARS (ALREADY PAID)", r.starGold, Pal.GOLD)
            if (hasGold) {
                val label = when {
                    r.firstGoldBlock -> "GOLD BLOCK"
                    board.goldCollected -> "GOLD BLOCK (OWNED)"
                    else -> "GOLD BLOCK LOST"
                }
                line(label, r.goldBlockGold, Pal.GOLD)
            }
            if (r.bonusGold > 0) line("VAULT BONUS", r.bonusGold, Pal.GOLD)
            Draw.rect(c, px + 14, ly, pw - 28, 1f, Pal.alpha(Pal.WHITE, 0.2f))
            ly += 4f
            Draw.bitmap(c, Sprites.coin, w / 2f - 30, ly + 1)
            PixelFont.drawShadow(c, UiKit.formatGold(shownGold.toInt()), w / 2f - 18, ly, Pal.GOLD, 2)
        } else {
            Draw.bitmap(c, Sprites.crew[0].body, w / 2f - 14, basePy + 28, 2f)
            PixelFont.drawShadow(c, lostReason, w / 2f, basePy + 76, Pal.CREAM, align = 1)
            PixelFont.drawShadow(c, "${board.pieces.size} BLOCKS LEFT STANDING", w / 2f, basePy + 92, Pal.alpha(Pal.WHITE, 0.7f), align = 1)
            PixelFont.drawShadow(c, "TIP: BREAK THE BOTTOM - LET GRAVITY", w / 2f, basePy + 116, Pal.alpha(Pal.CREAM, 0.6f), align = 1)
            PixelFont.drawShadow(c, "DO THE HEAVY LIFTING!", w / 2f, basePy + 126, Pal.alpha(Pal.CREAM, 0.6f), align = 1)
        }
        if (t > 0.5f) resultButtons.forEach { it.draw(c) }
        c.restore()
    }
}
