package com.topplecorp.core

/** Why a piece took damage. Used by the renderer to pick an effect and sound. */
enum class DamageCause { TOOL, FALL, CRUSH, SHOCK, EXPLOSION, FLOOD }

/** Something that happened while resolving a turn. Events are grouped into phases that play one after another. */
sealed class GameEvent {
    data class ToolUsed(val tool: ToolType, val target: Cell, val cells: List<Cell>) : GameEvent()
    data class Damaged(val pieceId: Int, val amount: Int, val hpLeft: Int, val cause: DamageCause) : GameEvent()
    data class Destroyed(val pieceId: Int, val material: Material, val cells: List<Cell>) : GameEvent()
    data class Effect(val effect: SpecialEffect, val origin: List<Cell>, val area: List<Cell>) : GameEvent()
    data class Fell(val pieceId: Int, val distance: Int) : GameEvent()
}

enum class Outcome { PLAYING, WON, LOST }

class PieceState(
    val id: Int,
    val material: Material,
    var cells: MutableList<Cell>,
    var hp: Int,
) {
    val maxHp: Int get() = material.hp
    val alive: Boolean get() = hp > 0
    fun copy() = PieceState(id, material, cells.toMutableList(), hp)
}

/**
 * Mutable simulation of one level. Pure logic, no Android dependencies, so it can be
 * used by the game, the solver and the level editor alike.
 */
class Board private constructor(
    val level: LevelDef,
    val width: Int,
    val height: Int,
    private val grid: IntArray,
    private val pieceMap: LinkedHashMap<Int, PieceState>,
    val charges: MutableMap<ToolType, Int>,
) {
    var turnsUsed = 0
        private set
    var goldCollected = false
        private set
    var bonusGold = 0
        private set
    var bonusTurnsEarned = 0
        private set

    val pieces: Collection<PieceState> get() = pieceMap.values

    fun piece(id: Int): PieceState? = pieceMap[id]

    fun pieceAt(x: Int, y: Int): PieceState? {
        if (x !in 0 until width || y !in 0 until height) return null
        val id = grid[y * width + x]
        return if (id == 0) null else pieceMap[id]
    }

    val outcome: Outcome
        get() = when {
            pieceMap.isEmpty() -> Outcome.WON
            turnsUsed >= level.turns.max -> Outcome.LOST
            !hasUsefulMove() -> Outcome.LOST
            else -> Outcome.PLAYING
        }

    val starsEarned: Int get() = if (pieceMap.isEmpty()) level.turns.starsFor(turnsUsed) else 0

    val turnsLeft: Int get() = (level.turns.max - turnsUsed).coerceAtLeast(0)

    /** Highest occupied row + 1. */
    val towerHeight: Int
        get() = pieceMap.values.maxOfOrNull { p -> p.cells.maxOf { it.y } + 1 } ?: 0

    fun copy(): Board {
        val map = LinkedHashMap<Int, PieceState>()
        pieceMap.forEach { (k, v) -> map[k] = v.copy() }
        return Board(level, width, height, grid.copyOf(), map, charges.toMutableMap()).also {
            it.turnsUsed = turnsUsed
            it.goldCollected = goldCollected
            it.bonusGold = bonusGold
            it.bonusTurnsEarned = bonusTurnsEarned
        }
    }

    fun canUse(tool: ToolType): Boolean {
        val c = charges[tool] ?: return false
        return c != 0
    }

    /** True if at least one available tool can still damage at least one piece. */
    fun hasUsefulMove(): Boolean {
        if (pieceMap.isEmpty()) return false
        for ((tool, c) in charges) {
            if (c == 0) continue
            if (pieceMap.values.any { tool.damageAgainst(it.material) > 0 }) return true
        }
        return false
    }

    /** Cells the tool would affect if used on [target]. Used for the aiming preview. */
    fun affectedCells(tool: ToolType, target: Cell): List<Cell> {
        val p = pieceAt(target.x, target.y)
        return when (tool) {
            ToolType.HAMMER, ToolType.DRILL -> p?.cells?.toList() ?: emptyList()
            ToolType.DYNAMITE -> {
                if (p == null) emptyList() else {
                    val ids = touchingPieces(p.cells).map { it.id }.toSet() + p.id
                    ids.flatMap { pieceMap[it]!!.cells }
                }
            }
            ToolType.LASER -> (0 until height).map { Cell(target.x, it) }
            ToolType.WRECKING_BALL -> (0 until width).map { Cell(it, target.y) }
        }
    }

    /** Whether using [tool] on [target] is a legal action. */
    fun isValidTarget(tool: ToolType, target: Cell): Boolean {
        if (!canUse(tool)) return false
        return when (tool) {
            ToolType.LASER -> target.x in 0 until width && (0 until height).any { pieceAt(target.x, it) != null }
            ToolType.WRECKING_BALL -> target.y in 0 until height && (0 until width).any { pieceAt(it, target.y) != null }
            else -> pieceAt(target.x, target.y) != null
        }
    }

    /**
     * Uses a tool and fully resolves the consequences (destruction, special effects, gravity, impacts).
     * Returns phases of events for animation. Returns empty list if the action is invalid.
     */
    fun applyTool(tool: ToolType, target: Cell): List<List<GameEvent>> {
        if (!isValidTarget(tool, target)) return emptyList()
        val c = charges[tool]!!
        if (c > 0) charges[tool] = c - 1
        turnsUsed++

        val phases = ArrayList<List<GameEvent>>()
        val first = ArrayList<GameEvent>()
        val dmg = LinkedHashMap<Int, Int>()
        val p = pieceAt(target.x, target.y)
        when (tool) {
            ToolType.HAMMER, ToolType.DRILL -> p?.let { dmg[it.id] = tool.damageAgainst(it.material) }
            ToolType.DYNAMITE -> p?.let { center ->
                dmg[center.id] = tool.damageAgainst(center.material)
                touchingPieces(center.cells).forEach { dmg[it.id] = tool.damageAgainst(it.material) }
            }
            ToolType.LASER -> (0 until height).mapNotNull { pieceAt(target.x, it) }.distinct()
                .forEach { dmg[it.id] = tool.damageAgainst(it.material) }
            ToolType.WRECKING_BALL -> (0 until width).mapNotNull { pieceAt(it, target.y) }.distinct()
                .forEach { dmg[it.id] = tool.damageAgainst(it.material) }
        }
        first += GameEvent.ToolUsed(tool, target, affectedCells(tool, target))
        applyDamage(dmg, DamageCause.TOOL, first)
        resolve(first, phases)
        return phases
    }

    private fun applyDamage(dmg: Map<Int, Int>, cause: DamageCause, out: MutableList<GameEvent>) {
        for ((id, amount) in dmg) {
            val p = pieceMap[id] ?: continue
            if (!p.alive) continue
            p.hp = (p.hp - amount).coerceAtLeast(0)
            out += GameEvent.Damaged(id, amount, p.hp, cause)
        }
    }

    private fun resolve(start: MutableList<GameEvent>, phases: MutableList<List<GameEvent>>) {
        var current = start
        var guard = 0
        while (guard++ < 500) {
            val dead = pieceMap.values.filter { !it.alive }
            if (dead.isNotEmpty()) {
                val effectDamage = LinkedHashMap<Int, Int>()
                var effectCause = DamageCause.SHOCK
                val effectEvents = ArrayList<GameEvent>()
                for (d in dead) {
                    removePiece(d)
                    current += GameEvent.Destroyed(d.id, d.material, d.cells.toList())
                    triggerEffect(d, effectDamage, effectEvents)?.let { effectCause = it }
                }
                phases += current
                current = ArrayList()
                if (effectEvents.isNotEmpty()) {
                    current += effectEvents
                    applyDamage(effectDamage, effectCause, current)
                    phases += current
                    current = ArrayList()
                }
                continue
            }
            val landedGold = pieceMap.values.filter { it.material == Material.GOLD && it.cells.any { c -> c.y == 0 } }
            if (landedGold.isNotEmpty()) {
                if (current.isNotEmpty()) { phases += current; current = ArrayList() }
                for (g in landedGold) {
                    removePiece(g)
                    goldCollected = true
                    current += GameEvent.Effect(SpecialEffect.GOLD, g.cells.toList(), emptyList())
                }
                phases += current
                current = ArrayList()
                continue
            }
            val falls = settle()
            if (falls.isEmpty()) break
            if (current.isNotEmpty()) {
                phases += current
                current = ArrayList()
            }
            phases += falls.map { (id, d) -> GameEvent.Fell(id, d) }
            current = ArrayList()
            computeImpacts(falls, current)
            if (current.isEmpty()) continue
        }
        if (current.isNotEmpty()) phases += current
    }

    private fun triggerEffect(p: PieceState, dmg: MutableMap<Int, Int>, events: MutableList<GameEvent>): DamageCause? {
        fun hit(target: PieceState, amount: Int) {
            if (target.id == p.id || !target.alive) return
            dmg[target.id] = (dmg[target.id] ?: 0) + amount
        }
        return when (p.material.effect) {
            SpecialEffect.NONE -> null
            SpecialEffect.GOLD -> null // Smashed gold is simply lost.
            SpecialEffect.BONUS_TURN -> {
                bonusTurnsEarned++
                turnsUsed = (turnsUsed - 1).coerceAtLeast(0)
                events += GameEvent.Effect(SpecialEffect.BONUS_TURN, p.cells.toList(), emptyList()); null
            }
            SpecialEffect.BONUS_GOLD -> {
                bonusGold += 100
                events += GameEvent.Effect(SpecialEffect.BONUS_GOLD, p.cells.toList(), emptyList()); null
            }
            SpecialEffect.SHOCK -> {
                val area = neighborCells(p.cells)
                touchingPieces(p.cells).forEach { hit(it, 2) }
                events += GameEvent.Effect(SpecialEffect.SHOCK, p.cells.toList(), area)
                DamageCause.SHOCK
            }
            SpecialEffect.EXPLODE -> {
                val area = LinkedHashSet<Cell>()
                for (c in p.cells) for (dx in -1..1) for (dy in -1..1) {
                    val n = Cell(c.x + dx, c.y + dy)
                    if (n.x in 0 until width && n.y in 0 until height) area += n
                }
                area.mapNotNull { pieceAt(it.x, it.y) }.distinct().forEach { hit(it, 2) }
                events += GameEvent.Effect(SpecialEffect.EXPLODE, p.cells.toList(), area.toList())
                DamageCause.EXPLOSION
            }
            SpecialEffect.FLOOD -> {
                val area = ArrayList<Cell>()
                val hitIds = LinkedHashSet<Int>()
                val columns = p.cells.groupBy { it.x }.mapValues { e -> e.value.minOf { it.y } }
                for ((x, minY) in columns) for (y in minY - 1 downTo 0) {
                    area += Cell(x, y)
                    pieceAt(x, y)?.let { hitIds += it.id }
                }
                hitIds.forEach { id -> pieceMap[id]?.let { hit(it, 1) } }
                events += GameEvent.Effect(SpecialEffect.FLOOD, p.cells.toList(), area)
                DamageCause.FLOOD
            }
        }
    }

    private fun neighborCells(cells: List<Cell>): List<Cell> {
        val own = cells.toSet()
        val out = LinkedHashSet<Cell>()
        for (c in cells) for ((dx, dy) in DIRS) {
            val n = Cell(c.x + dx, c.y + dy)
            if (n !in own && n.x in 0 until width && n.y in 0 until height) out += n
        }
        return out.toList()
    }

    private fun touchingPieces(cells: List<Cell>): List<PieceState> {
        val own = cells.firstOrNull()?.let { pieceAt(it.x, it.y)?.id }
        return neighborCells(cells).mapNotNull { pieceAt(it.x, it.y) }.filter { it.id != own }.distinct()
    }

    private fun removePiece(p: PieceState) {
        for (c in p.cells) if (grid[c.y * width + c.x] == p.id) grid[c.y * width + c.x] = 0
        pieceMap.remove(p.id)
    }

    /** Drops every unsupported piece until everything rests. Returns fall distance per piece. */
    private fun settle(): LinkedHashMap<Int, Int> {
        val falls = LinkedHashMap<Int, Int>()
        while (true) {
            val grounded = HashSet<Int>()
            val queue = ArrayDeque<PieceState>()
            for (p in pieceMap.values) if (p.cells.any { it.y == 0 }) { grounded += p.id; queue += p }
            while (queue.isNotEmpty()) {
                val p = queue.removeFirst()
                for (c in p.cells) {
                    val above = pieceAt(c.x, c.y + 1) ?: continue
                    if (above.id != p.id && grounded.add(above.id)) queue += above
                }
            }
            val falling = pieceMap.values.filter { it.id !in grounded }
            if (falling.isEmpty()) break
            for (p in falling) for (c in p.cells) grid[c.y * width + c.x] = 0
            for (p in falling) {
                p.cells = p.cells.mapTo(ArrayList()) { Cell(it.x, it.y - 1) }
                for (c in p.cells) grid[c.y * width + c.x] = p.id
                falls[p.id] = (falls[p.id] ?: 0) + 1
            }
        }
        return falls
    }

    private fun computeImpacts(falls: Map<Int, Int>, out: MutableList<GameEvent>) {
        val dmg = LinkedHashMap<Int, Int>()
        val selfDmg = LinkedHashMap<Int, Int>()
        for ((id, d) in falls) {
            val p = pieceMap[id] ?: continue
            val below = LinkedHashSet<PieceState>()
            var onGround = false
            for (c in p.cells) {
                if (c.y == 0) { onGround = true; continue }
                val b = pieceAt(c.x, c.y - 1) ?: continue
                if (b.id != p.id) below += b
            }
            // Only the relative fall distance hurts: pieces that dropped together don't crush each other.
            var relative = if (onGround) d else 0
            for (b in below) {
                val rel = d - (falls[b.id] ?: 0)
                if (rel <= 0) continue
                relative = maxOf(relative, rel)
                val amount = impactDamage(p.material.weight, rel)
                if (amount > 0) dmg[b.id] = (dmg[b.id] ?: 0) + amount
            }
            val self = selfImpactDamage(p.material, relative)
            if (self > 0) selfDmg[id] = self
        }
        applyDamage(dmg, DamageCause.CRUSH, out)
        applyDamage(selfDmg, DamageCause.FALL, out)
    }

    companion object {
        private val DIRS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

        fun impactDamage(weight: Int, distance: Int): Int = (weight * (distance + 1)) / 4

        fun selfImpactDamage(m: Material, distance: Int): Int = when {
            distance <= 0 -> 0
            m.fragile -> m.hp
            m.bouncy -> 0
            distance >= 2 -> 1 + (distance - 2) / 3
            else -> 0
        }

        fun fromLevel(level: LevelDef): Board {
            val grid = IntArray(level.width * level.height)
            val map = LinkedHashMap<Int, PieceState>()
            for (p in level.pieces) {
                val cells = p.cells.filter { it.x in 0 until level.width && it.y in 0 until level.height }
                    .filter { grid[it.y * level.width + it.x] == 0 }
                if (cells.isEmpty()) continue
                cells.forEach { grid[it.y * level.width + it.x] = p.id }
                map[p.id] = PieceState(p.id, p.material, cells.toMutableList(), p.material.hp)
            }
            val board = Board(level, level.width, level.height, grid, map, level.tools.toMutableMap())
            board.settle() // Levels always start at rest.
            return board
        }
    }
}
