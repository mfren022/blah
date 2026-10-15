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
import com.topplecorp.game.gfx.Sprites
import com.topplecorp.game.gfx.Textures
import com.topplecorp.game.gfx.Themes
import com.topplecorp.game.gfx.TowerRenderer
import com.topplecorp.game.gfx.VisualPiece
import com.topplecorp.game.ui.Button
import com.topplecorp.game.ui.Ease
import com.topplecorp.game.ui.Icon
import com.topplecorp.game.ui.Icons
import com.topplecorp.game.ui.Touch
import com.topplecorp.game.ui.TouchAction
import com.topplecorp.game.ui.Ui
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
    private val hudH = 44f
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

        buttons += Button(10f, 8f, 28f, 28f, icon = Icon.PAUSE) { setPaused(true) }.apply { radius = 14f; iconSize = 14f }

        // Tool bar
        toolButtons.clear()
        val tools = level.tools.keys.toList()
        val n = tools.size.coerceAtLeast(1)
        val slotW = ((w - 20f - (n - 1) * 6f) / n).coerceAtMost(52f)
        val total = n * slotW + (n - 1) * 6f
        var x = (w - total) / 2f
        for (t in tools) {
            val b = Button(x, h - 54f, slotW, 44f, color = Ui.ACCENT, sound = Sound.SELECT) { selectTool(t) }
            b.sprite = Sprites.tools[t]
            b.spriteScale = if (slotW >= 34f) 2f else 1f
            b.radius = 12f
            toolButtons[t] = b
            buttons += b
            x += slotW + 6f
        }
        refreshToolButtons()

        // Pause menu
        pauseButtons.clear()
        val pw = 180f
        val px = (w - pw) / 2f
        val py = h / 2f - 66f
        pauseButtons += Button(px, py, pw, 34f, "Resume", Icon.PLAY, Ui.GREEN, textSize = 12f) { setPaused(false) }
            .apply { primary = true }
        pauseButtons += Button(px, py + 44, pw, 34f, "Restart", Icon.RETRY, textSize = 12f) { restart() }
        pauseButtons += Button(px, py + 88, pw, 34f, "Levels", Icon.LIST, textSize = 12f) { toLevels() }
        pauseButtons += Button(px, py + 132, pw, 34f, if (game.save.soundOn) "Sound on" else "Sound off",
            if (game.save.soundOn) Icon.SOUND else Icon.MUTE, textSize = 12f) { toggleSound() }
        pauseButtons.forEach { it.radius = 12f }
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
        pauseButtons[3].label = if (game.save.soundOn) "Sound on" else "Sound off"
        pauseButtons[3].icon = if (game.save.soundOn) Icon.SOUND else Icon.MUTE
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
        if (!board.canUse(t)) { game.play(Sound.DENY); tip = "No ${t.displayName.lowercase()}s left!"; tipFlash = 1f; return }
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
        }
    }

    private fun updateTip() {
        tip = when {
            aim != null -> "Tap again to strike!"
            else -> selected.displayName + " — " + selected.description
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
            tip = "Hammers can't dent that!"; tipFlash = 1f
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
                    particles.floatText(cx, cy - 6, "Clank!", Pal.GRAY)
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
                    particles.floatText(cx, cy - 10, "Gold lost!", Pal.RED)
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
                particles.floatText(ox, oy - 8, "Zap!", Pal.GOLD)
                shake = maxOf(shake, 3f)
            }
            SpecialEffect.EXPLODE -> {
                game.play(Sound.EXPLOSION)
                particles.sparks(ox, oy, Pal.GOLD, 40, 260f)
                particles.sparks(ox, oy, Pal.ORANGE, 30, 200f)
                particles.debris(ox, oy, Pal.GRAY_DARK, 16, 1.5f)
                particles.floatText(ox, oy - 8, "Boom!", Pal.ORANGE)
                shake = maxOf(shake, 7f)
                game.vibrate(80, 255)
            }
            SpecialEffect.FLOOD -> {
                game.play(Sound.SPLASH)
                for (c in e.area) particles.water(cellCenterX(c.x.toFloat()), cellCenterY(c.y.toFloat()), 4)
                particles.floatText(ox, oy - 8, "Splash!", Pal.BLUE)
            }
            SpecialEffect.BONUS_TURN -> {
                game.play(Sound.BONUS)
                particles.floatText(ox, oy - 8, "+1 turn", Pal.TEAL)
                particles.sparks(ox, oy, 0xFFBFF6FF.toInt(), 16)
            }
            SpecialEffect.BONUS_GOLD -> {
                game.play(Sound.COIN)
                particles.floatText(ox, oy - 8, "+100 gold", Pal.GOLD)
                particles.sparks(ox, oy, Pal.GOLD, 24, 150f)
            }
            SpecialEffect.GOLD -> {
                goldCollected = true
                // The crew grabs it: remove the gold block visual.
                visuals.values.removeAll { v -> v.material == Material.GOLD && v.cells.any { it in e.origin } }
                game.play(Sound.GOLD)
                particles.floatText(ox, oy - 10, "Gold block!", Pal.GOLD)
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
                lostReason = if (board.turnsLeft <= 0) "Out of turns" else "No tool can damage what's left"
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
        val pw = 210f
        val px = (w - pw) / 2f
        val by = h / 2f + 60f
        if (state == State.WON) {
            reward = game.save.completeLevel(city, index, board.starsEarned, board.goldCollected, board.bonusGold)
            game.play(Sound.WIN)
            val hasNext = index + 1 < Campaign.LEVELS_PER_CITY
            resultButtons += Button(px, by, 52f, 36f, icon = Icon.LIST) { toLevels() }
            resultButtons += Button(px + 60, by, 52f, 36f, icon = Icon.RETRY) { restart() }
            resultButtons += Button(px + 120, by, pw - 120, 36f, if (hasNext) "Next" else "Cities", Icon.PLAY, Ui.GREEN, textSize = 12f) {
                if (hasNext) game.goTo { GameScene(game, city, index + 1) } else game.goTo { CityScene(game) }
            }.apply { primary = true }
        } else {
            game.play(Sound.LOSE)
            resultButtons += Button(px, by, pw / 2 - 4, 36f, "Levels", Icon.LIST, textSize = 12f) { toLevels() }
            resultButtons += Button(px + pw / 2 + 4, by, pw / 2 - 4, 36f, "Retry", Icon.RETRY, Ui.ACCENT, textSize = 12f) { restart() }
                .apply { primary = true }
        }
        resultButtons.forEach { it.radius = 12f; it.iconSize = 14f }
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

    // ------------------------------------------------------------------ draw (world)

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

    // ------------------------------------------------------------------ draw (UI)

    override fun drawUi(c: Canvas) {
        // World-anchored labels
        c.save()
        c.translate(0f, cam)
        drawAimLabels(c)
        c.restore()
        particles.drawText(c, cam)

        drawHud(c)
        drawToolbar(c)
        if (hintAnim > 0.01f) drawHint(c)
        if (pauseAnim > 0.01f) drawPause(c)
        if (resultTime >= 0f) drawResults(c)
    }

    /** Projected damage on each block the current aim would hit. */
    private fun drawAimLabels(c: Canvas) {
        if (aimCells.isEmpty() || state != State.PLAYING) return
        val ids = aimCells.mapNotNull { board.pieceAt(it.x, it.y) }.distinct()
        for (p in ids) {
            val v = visuals[p.id] ?: continue
            val (cx, cy) = pieceCenter(v)
            val dmg = selected.damageAgainst(p.material)
            val kills = dmg >= p.hp
            val label = if (kills) "Break" else "−$dmg"
            val col = when { dmg == 0 -> Ui.TEXT_FAINT; kills -> Ui.RED; else -> Ui.TEXT }
            val lw = Ui.width(label, 8f, Ui.Weight.BOLD) + 10f
            Ui.roundRect(c, cx - lw / 2, cy - 9f, lw, 18f, 6f, Ui.BG_STRONG)
            Ui.text(c, label, cx, cy - 3f, 8f, col, Ui.Align.CENTER, Ui.Weight.BOLD)
            // HP bar
            val bw = lw - 8f
            Ui.roundRect(c, cx - bw / 2, cy + 4f, bw, 2.5f, 1.25f, 0x40FFFFFF)
            Ui.roundRect(c, cx - bw / 2, cy + 4f, bw * p.hp / p.maxHp, 2.5f, 1.25f, Ui.GREEN)
        }
    }

    private fun drawHud(c: Canvas) {
        // Level chip
        val cityName = Campaign.cities[city].name
        val chipW = maxOf(Ui.width(cityName, 7.5f), Ui.width("Level ${city + 1}-${index + 1}", 9f, Ui.Weight.BOLD)) + 16f
        val chipX = 44f
        val showChip = chipX + chipW < w / 2f - 30f
        if (showChip) {
            Ui.panel(c, chipX, 8f, chipW, 28f, 10f)
            Ui.text(c, "Level ${city + 1}-${index + 1}", chipX + 8, 16f, 9f, Ui.TEXT, weight = Ui.Weight.BOLD)
            Ui.text(c, cityName, chipX + 8, 28f, 7.5f, Ui.TEXT_DIM)
        }
        drawButtons(c)

        // Turns left
        val left = board.turnsLeft
        val urgent = left <= 2 && state != State.WON
        val turnColor = if (urgent && (time % 0.6f) < 0.3f) Ui.RED else Ui.TEXT
        val cx = w / 2f
        Ui.panel(c, cx - 26, 6f, 52f, 32f, 10f)
        Ui.text(c, "$left", cx, 18f, 15f, turnColor, Ui.Align.CENTER, Ui.Weight.BOLD)
        Ui.text(c, if (left == 1) "turn" else "turns", cx, 31f, 7f, Ui.TEXT_DIM, Ui.Align.CENTER)

        // Star outlook: which rating the player is still on track for.
        val used = board.turnsUsed
        val potential = level.turns.starsFor(used).coerceAtLeast(if (used <= level.turns.max) 1 else 0)
        val nextLimit = when (potential) { 3 -> level.turns.three; 2 -> level.turns.two; else -> level.turns.max }
        val remain = (nextLimit - used).coerceAtLeast(0)
        val sx = cx + 32
        val gx = if (hasGold) w - 32f else w - 8f
        val sw = (gx - 6f) - sx
        if (sw > 40f) {
            Ui.panel(c, sx, 8f, sw, 28f, 10f)
            Ui.stars(c, sx + sw / 2, 16.5f, potential, 4.5f, 1.5f)
            Ui.text(c, if (remain == 1) "1 turn left" else "$remain turns left", sx + sw / 2, 29f, 7.5f, Ui.TEXT_DIM, Ui.Align.CENTER)
        }

        // Gold block status
        if (hasGold) {
            val bx = w - 30f
            Ui.panel(c, bx, 8f, 22f, 28f, 10f)
            val col = when {
                goldCollected -> Ui.GOLD
                goldLost -> Ui.withAlpha(Ui.RED, 0.8f)
                else -> 0x59FFFFFF
            }
            val bob = if (goldCollected) sin(time * 5f) else 0f
            Ui.roundRect(c, bx + 5, 17f + bob, 12f, 9f, 2f, col)
            if (goldLost) Icons.draw(c, Icon.CLOSE, bx + 11, 21.5f, 10f, Ui.TEXT)
        }
    }

    private fun drawToolbar(c: Canvas) {
        val top = h - toolbarH
        Ui.roundRect(c, 0f, top, w, toolbarH + 20f, 0f, Ui.BG)
        Ui.roundRect(c, 0f, top, w, 0.5f, 0f, Ui.BORDER)
        val tipColor = if (tipFlash > 0f && (time % 0.2f) < 0.1f) Ui.ACCENT else Ui.TEXT_DIM
        val lines = Ui.wrap(tip, w - 20f, 8f)
        Ui.text(c, lines.firstOrNull() ?: "", w / 2f, top + 10f, 8f, tipColor, Ui.Align.CENTER)
        for (b in toolButtons.values) b.draw(c)
        for ((t, b) in toolButtons) {
            if (board.charges[t] == -1) {
                Ui.roundRect(c, b.x + b.w - 11, b.y - 4, 14f, 12f, 6f, Ui.withAlpha(0xFF000000.toInt(), 0.85f))
                Ui.outline(c, b.x + b.w - 11, b.y - 4, 14f, 12f, 6f, Ui.withAlpha(Ui.ACCENT, 0.9f), 1f)
                Ui.text(c, "∞", b.x + b.w - 4, b.y + 1.5f, 8f, Ui.TEXT, Ui.Align.CENTER, Ui.Weight.BOLD)
            }
        }
    }

    private fun drawHint(c: Canvas) {
        val hint = level.hint ?: return
        val a = hintAnim
        Ui.dim(c, w, h, 0.45f * a)
        val pw = w - 32f
        val px = 16f
        val lines = Ui.wrap(hint, pw - 66f, 9.5f)
        val ph = (48f + lines.size * 13f).coerceAtLeast(76f)
        val py = h * 0.36f - ph / 2 + (1f - Ease.outBack(a)) * 50f
        Ui.panel(c, px, py, pw, ph, 14f, Ui.withAlpha(Ui.BG_STRONG, a))
        Ui.text(c, "Foreman", px + 56, py + 16, 8f, Ui.ACCENT, weight = Ui.Weight.BOLD, alpha = a)
        // What is being introduced (pixel art preview on a rounded tile)
        Ui.roundRect(c, px + 12, py + 12, 36f, 36f, 10f, Ui.withAlpha(0x1AFFFFFF, a))
        val g = Campaign.globalIndex(city, index)
        val newTool = Campaign.toolUnlock.entries.firstOrNull { it.value == g && g > 0 }?.key
        val newMat = Campaign.materialUnlock.entries.firstOrNull { it.value == g && g > 0 }?.key
        val ix = px + 18f
        val iy = py + 18f
        if (newTool != null) {
            Draw.bitmap(c, Sprites.tools[newTool]!!, ix, iy, 2f)
        } else if (newMat != null) {
            Draw.bitmapRect(c, renderer.textures.get(newMat), ix, iy, 24f, 24f)
            Sprites.specials[newMat]?.let { Draw.bitmap(c, it, ix + 12 - it.width / 2f, iy + 12 - it.height / 2f) }
        } else {
            Draw.bitmap(c, Sprites.crew[0].body, ix + 5, iy + 2)
        }
        var ty = py + 31f
        for (line in lines) {
            Ui.text(c, line, px + 56, ty, 9.5f, Ui.TEXT, weight = Ui.Weight.REGULAR, alpha = a)
            ty += 13f
        }
        val blink = 0.5f + 0.5f * sin(time * 4f)
        Ui.text(c, "Tap to start", px + pw / 2, py + ph - 11, 8f, Ui.TEXT_DIM, Ui.Align.CENTER, alpha = a * blink)
    }

    private fun drawPause(c: Canvas) {
        Ui.dim(c, w, h, 0.55f * pauseAnim)
        val off = (1f - Ease.outBack(pauseAnim)) * 40f
        c.save()
        c.translate(0f, off)
        val px = (w - 204f) / 2f
        val py = h / 2f - 108f
        Ui.panel(c, px, py, 204f, 228f, 16f, Ui.withAlpha(Ui.BG_STRONG, pauseAnim))
        Ui.text(c, "Paused", w / 2f, py + 22, 15f, Ui.TEXT, Ui.Align.CENTER, Ui.Weight.BOLD, alpha = pauseAnim)
        pauseButtons.forEach { it.draw(c, pauseAnim) }
        c.restore()
    }

    private fun drawResults(c: Canvas) {
        val t = resultTime
        Ui.dim(c, w, h, (t * 2f).coerceAtMost(0.6f))
        val appear = Ease.outBack((t / 0.45f).coerceIn(0f, 1f))
        val pw = 230f
        val ph = 222f
        val px = (w - pw) / 2f
        val basePy = h / 2f - 120f
        val won = state == State.WON
        c.save()
        c.translate(0f, -(1f - appear) * h * 0.6f)
        Ui.panel(c, px, basePy, pw, ph, 16f, Ui.BG_STRONG)
        Ui.text(c, if (won) "Tower down!" else "Tower stands", w / 2f, basePy + 24, 17f,
            if (won) Ui.TEXT else Ui.RED, Ui.Align.CENTER, Ui.Weight.BOLD)
        if (won) {
            val stars = board.starsEarned
            for (i in 0 until 3) {
                val sx = w / 2f + (i - 1) * 34f
                val pop = ((t - 0.45f - i * 0.32f) / 0.25f).coerceIn(0f, 1f)
                val big = i < stars && pop > 0f
                val r = if (big) 12f * (1f + (1f - Ease.outElastic(pop)) * 0.6f) else 12f
                Ui.star(c, sx, basePy + 56 - (if (i == 1) 4f else 0f), r, big)
            }
            val r = reward ?: return c.restore()
            var ly = basePy + 86f
            Ui.text(c, "${board.turnsUsed} turns used", w / 2f, ly, 8.5f, Ui.TEXT_DIM, Ui.Align.CENTER)
            ly += 18f
            fun line(label: String, value: Int) {
                Ui.text(c, label, px + 18, ly, 9f, Ui.TEXT)
                Ui.text(c, "+${UiKit.formatGold(value)}", px + pw - 18, ly, 9f, Ui.GOLD, Ui.Align.RIGHT, Ui.Weight.BOLD)
                ly += 15f
            }
            line(if (r.newBest) "$stars-star reward" else "Stars (already paid)", r.starGold)
            if (hasGold) {
                val label = when {
                    r.firstGoldBlock -> "Gold block"
                    board.goldCollected -> "Gold block (owned)"
                    else -> "Gold block lost"
                }
                line(label, r.goldBlockGold)
            }
            if (r.bonusGold > 0) line("Vault bonus", r.bonusGold)
            Ui.roundRect(c, px + 18, ly - 4, pw - 36, 0.5f, 0f, 0x33FFFFFF)
            ly += 10f
            val total = UiKit.formatGold(shownGold.toInt())
            val tw = Ui.width(total, 16f, Ui.Weight.BOLD)
            Ui.coin(c, w / 2f - tw / 2 - 8, ly, 6f)
            Ui.text(c, total, w / 2f - tw / 2 + 2, ly, 16f, Ui.GOLD, weight = Ui.Weight.BOLD)
        } else {
            Draw.bitmap(c, Sprites.crew[0].body, w / 2f - 14, basePy + 42, 2f)
            Ui.text(c, lostReason, w / 2f, basePy + 100, 10f, Ui.TEXT, Ui.Align.CENTER)
            Ui.text(c, "${board.pieces.size} blocks left standing", w / 2f, basePy + 116, 8.5f, Ui.TEXT_DIM, Ui.Align.CENTER)
            Ui.text(c, "Tip: break the bottom and let", w / 2f, basePy + 140, 8f, Ui.TEXT_FAINT, Ui.Align.CENTER)
            Ui.text(c, "gravity do the heavy lifting.", w / 2f, basePy + 152, 8f, Ui.TEXT_FAINT, Ui.Align.CENTER)
        }
        if (t > 0.5f) resultButtons.forEach { it.draw(c) }
        c.restore()
    }
}
