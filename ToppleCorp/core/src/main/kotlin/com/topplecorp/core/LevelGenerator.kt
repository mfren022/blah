package com.topplecorp.core

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Builds campaign towers by dropping tetromino-like pieces Tetris-style, then verifies
 * each level with the [Solver] and derives star thresholds from it.
 */
object LevelGenerator {

    /** Normal piece shapes (base orientation). */
    private val SHAPES: List<List<Cell>> = listOf(
        cells("XXXX"),            // I
        cells("XX", "XX"),        // O
        cells("XXX", ".X."),      // T
        cells(".XX", "XX."),      // S
        cells("XX.", ".XX"),      // Z
        cells("X..", "XXX"),      // J
        cells("..X", "XXX"),      // L
        cells("XXX"),             // I3
        cells("XX", "X."),        // small L
        cells("XX"),              // domino
    )

    private val SPECIAL_SHAPES = mapOf(
        Material.GOLD to cells("X"),
        Material.TNT to cells("X"),
        Material.AC_UNIT to cells("XX"),
        Material.WATER_TANK to cells("X", "X"),
        Material.GENERATOR to cells("XX", "XX"),
        Material.VAULT to cells("XX", "XX"),
    )

    /** Parses rows (top row first) into cells with y = 0 at the bottom. */
    fun cells(vararg rows: String): List<Cell> {
        val out = ArrayList<Cell>()
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { x, ch -> if (ch == 'X') out += Cell(x, rows.size - 1 - r) }
        }
        return out
    }

    private fun normalize(cs: List<Cell>): List<Cell> {
        val mx = cs.minOf { it.x }; val my = cs.minOf { it.y }
        return cs.map { Cell(it.x - mx, it.y - my) }.sortedWith(compareBy({ it.y }, { it.x }))
    }

    fun rotations(shape: List<Cell>): List<List<Cell>> {
        val out = ArrayList<List<Cell>>()
        var cur = normalize(shape)
        repeat(4) {
            if (out.none { it == cur }) out += cur
            cur = normalize(cur.map { Cell(it.y, -it.x) })
        }
        return out
    }

    private class Params(
        val width: Int,
        val targetHeight: Int,
        val materials: Map<Material, Double>,
        val specials: List<Material>,
        val tools: Map<ToolType, Int>,
    )

    private fun params(city: Int, index: Int): Params {
        val g = Campaign.globalIndex(city, index)
        val width = 5 + minOf(3, g / 25)
        val ramp = index / 29.0
        val targetHeight = (3 + g * 0.15 + ramp * 1.5).roundToInt()
        val unlocked = Campaign.materialsAt(g)

        val materials = LinkedHashMap<Material, Double>()
        for (m in unlocked) {
            if (m.isSpecial) continue
            val age = g - (Campaign.materialUnlock[m] ?: 0)
            // Newly introduced materials show up a lot in their intro level, then settle.
            var w = when (m) {
                Material.WOOD -> 3.0
                Material.GLASS -> 1.5
                Material.PLASTIC -> 1.5
                Material.BRICK -> 2.5
                Material.STONE -> 2.0
                Material.METAL -> 1.2
                Material.STEEL -> 0.7
                else -> 1.0
            }
            if (age == 0) w *= 3.0
            materials[m] = w
        }
        // Older soft materials fade out in later cities.
        if (g >= 60) materials[Material.WOOD] = (materials[Material.WOOD] ?: 0.0) * 0.5
        if (g >= 90) materials[Material.GLASS] = (materials[Material.GLASS] ?: 0.0) * 0.6

        val specials = ArrayList<Material>()
        val rnd = Random(g * 7919L + 13)
        fun has(m: Material) = m in unlocked
        fun intro(m: Material) = Campaign.materialUnlock[m] == g
        if (has(Material.GOLD)) specials += Material.GOLD
        for (m in listOf(Material.GENERATOR, Material.AC_UNIT, Material.TNT, Material.WATER_TANK, Material.VAULT)) {
            if (!has(m)) continue
            val count = when {
                intro(m) -> 2
                rnd.nextDouble() < 0.55 -> 1 + (if (g > 110 && rnd.nextBoolean()) 1 else 0)
                else -> 0
            }
            repeat(count) { specials += m }
        }

        val tools = LinkedHashMap<ToolType, Int>()
        for (t in Campaign.toolsAt(g)) {
            val since = g - Campaign.toolUnlock[t]!!
            tools[t] = when (t) {
                ToolType.HAMMER -> -1
                ToolType.DRILL, ToolType.DYNAMITE -> if (since == 0) 3 else (1 + since / 30).coerceAtMost(3)
                ToolType.LASER, ToolType.WRECKING_BALL -> if (since == 0) 2 else (1 + since / 45).coerceAtMost(2)
            }
        }
        return Params(width, targetHeight, materials, specials, tools)
    }

    private fun pickMaterial(rnd: Random, pool: Map<Material, Double>, relHeight: Double): Material {
        val weights = pool.mapValues { (m, w) -> if (m.weight >= 4) w * (0.4 + relHeight * 1.4) else w }
        val total = weights.values.sum()
        var r = rnd.nextDouble() * total
        for ((m, w) in weights) { r -= w; if (r <= 0) return m }
        return weights.keys.last()
    }

    /** Builds the raw tower (no star thresholds yet). */
    private fun buildTower(city: Int, index: Int, seed: Long, p: Params): LevelDef {
        val rnd = Random(seed)
        val w = p.width
        val skyline = IntArray(w)
        val occupied = HashSet<Cell>()
        val pieces = ArrayList<PieceDef>()
        var nextId = 1

        // Schedule specials at heights through the tower. Gold rides high so it has to come down.
        val pending = p.specials.map { m ->
            val frac = if (m == Material.GOLD) 0.55 + rnd.nextDouble() * 0.35 else 0.1 + rnd.nextDouble() * 0.75
            m to (frac * p.targetHeight)
        }.sortedBy { it.second }.toMutableList()

        var guard = 0
        while (skyline.min() < p.targetHeight && skyline.average() < p.targetHeight + 1.0 && guard++ < 600) {
            val avg = skyline.average()
            val special = pending.firstOrNull()?.takeIf { avg >= it.second }
            val material: Material
            val shapes: List<List<Cell>>
            if (special != null) {
                pending.removeAt(0)
                material = special.first
                shapes = rotations(SPECIAL_SHAPES[material]!!)
            } else {
                material = pickMaterial(rnd, p.materials, avg / p.targetHeight)
                val base = SHAPES[rnd.nextInt(SHAPES.size)]
                shapes = rotations(base)
            }

            var bestCells: List<Cell>? = null
            var bestScore = Double.MAX_VALUE
            for (shape in shapes) {
                val sw = shape.maxOf { it.x } + 1
                if (sw > w) continue
                for (x in 0..w - sw) {
                    // Lowest y where the shape rests on the skyline.
                    var y = 0
                    for (c in shape) y = maxOf(y, skyline[x + c.x] - c.y)
                    // Holes under the piece.
                    var holes = 0
                    for (cx in 0 until sw) {
                        val bottom = shape.filter { it.x == cx }.minOfOrNull { it.y } ?: continue
                        holes += (y + bottom) - skyline[x + cx]
                    }
                    val top = y + shape.maxOf { it.y }
                    val over = (top + 1 - (p.targetHeight + 1)).coerceAtLeast(0)
                    val score = holes * 2.5 + y + over * 4 + rnd.nextDouble() * 2.2
                    if (score < bestScore) {
                        bestScore = score
                        bestCells = shape.map { Cell(x + it.x, y + it.y) }
                    }
                }
            }
            val placed = bestCells ?: continue
            if (placed.any { it in occupied }) continue
            occupied += placed
            for (c in placed) skyline[c.x] = maxOf(skyline[c.x], c.y + 1)
            pieces += PieceDef(nextId++, material, placed)
        }
        val height = (pieces.maxOfOrNull { pc -> pc.cells.maxOf { it.y } } ?: 0) + 1
        val g = Campaign.globalIndex(city, index)
        return LevelDef(
            city = city, index = index, name = "${city + 1}-${index + 1}",
            width = w, height = height, pieces = pieces, tools = p.tools,
            turns = TurnLimits(99, 99, 99), hint = Campaign.introFor(g), seed = seed,
        )
    }

    /**
     * Generates a verified level. Re-rolls until the solver can beat it while keeping the gold block,
     * and sets the star thresholds from the solver's turn count.
     */
    fun generate(city: Int, index: Int): LevelDef {
        val p = params(city, index)
        var fallback: Pair<LevelDef, Solver.Result>? = null
        for (attempt in 0 until 40) {
            val seed = (city + 1) * 100_000L + index * 1_000L + attempt
            val raw = buildTower(city, index, seed, p)
            val board = Board.fromLevel(raw)
            // Gold must not start on the ground (it would be collected instantly).
            if (board.pieces.any { pc -> pc.material == Material.GOLD && pc.cells.any { it.y == 0 } }) continue
            val result = Solver.bestSolve(raw) ?: continue
            val hasGold = raw.pieces.any { it.material == Material.GOLD }
            if (!hasGold || result.gold) return withLimits(raw, result.turns)
            if (fallback == null) fallback = raw to result
        }
        fallback?.let { return withLimits(it.first, it.second.turns) }
        error("Could not generate level ${city + 1}-${index + 1}")
    }

    private fun withLimits(level: LevelDef, solverTurns: Int): LevelDef {
        // The very first levels are generous.
        val bonus = if (level.city == 0 && level.index < 5) 1 else 0
        val l = Solver.limitsFor(solverTurns)
        return level.copy(turns = TurnLimits(l.three + bonus, l.two + bonus, l.max + bonus))
    }
}
