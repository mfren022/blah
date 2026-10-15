package com.topplecorp.core

/**
 * Greedy one-ply solver. Used to verify generated levels are beatable and to derive
 * star thresholds. The level editor can call [solve] to auto-compute par turns.
 */
object Solver {

    data class Move(val tool: ToolType, val target: Cell)

    data class Result(val turns: Int, val moves: List<Move>, val gold: Boolean)

    /** Every distinct action available on the board. */
    fun candidateMoves(board: Board): List<Move> {
        val out = ArrayList<Move>()
        val pieces = board.pieces.toList()
        for (tool in ToolType.entries) {
            if (!board.canUse(tool)) continue
            when (tool) {
                ToolType.LASER -> {
                    val xs = pieces.flatMap { p -> p.cells.map { it.x } }.toSortedSet()
                    xs.forEach { x -> out += Move(tool, Cell(x, 0)) }
                }
                ToolType.WRECKING_BALL -> {
                    val ys = pieces.flatMap { p -> p.cells.map { it.y } }.toSortedSet()
                    ys.forEach { y -> out += Move(tool, Cell(0, y)) }
                }
                else -> pieces.forEach { p ->
                    if (tool.damageAgainst(p.material) > 0) out += Move(tool, p.cells.first())
                }
            }
        }
        return out
    }

    private fun score(b: Board, toolPenalty: Int, tool: ToolType, startCharges: Map<ToolType, Int>, hadGold: Boolean): Int {
        var s = 0
        for (p in b.pieces) {
            val hard = if (p.material.hammerImmune) 3 else 1
            s += p.hp * 4 * hard + 6
        }
        if (tool != ToolType.HAMMER && (startCharges[tool] ?: -1) > 0) s += toolPenalty
        if (b.goldCollected) s -= 20
        if (!b.goldCollected && b.pieces.none { it.material == Material.GOLD } && hadGold) s += 30
        return s
    }

    /** Runs greedy play. Returns null if it fails within [maxTurns]. */
    fun solve(level: LevelDef, maxTurns: Int = 400, toolPenalty: Int = 10): Result? {
        val board = Board.fromLevel(level)
        val moves = ArrayList<Move>()
        val hadGold = board.pieces.any { it.material == Material.GOLD }
        while (board.pieces.isNotEmpty()) {
            if (board.turnsUsed >= maxTurns || moves.size >= maxTurns) return null
            if (!board.hasUsefulMove()) return null
            var best: Move? = null
            var bestScore = Int.MAX_VALUE
            for (m in candidateMoves(board)) {
                val copy = board.copy()
                if (copy.applyTool(m.tool, m.target).isEmpty()) continue
                val sc = score(copy, toolPenalty, m.tool, level.tools, hadGold)
                if (sc < bestScore) { bestScore = sc; best = m }
            }
            val m = best ?: return null
            board.applyTool(m.tool, m.target)
            moves += m
        }
        return Result(board.turnsUsed, moves, board.goldCollected)
    }

    /** Tries a few greedy flavours and returns the best result. */
    fun bestSolve(level: LevelDef, maxTurns: Int = 400): Result? =
        listOf(0, 6, 14, 30).mapNotNull { solve(level, maxTurns, it) }.minByOrNull { it.turns }

    /** Derives star thresholds from the number of turns the solver needed. */
    fun limitsFor(solverTurns: Int): TurnLimits {
        val three = solverTurns
        val two = three + maxOf(1, solverTurns / 4)
        val max = two + maxOf(3, solverTurns / 2)
        return TurnLimits(three, two, max)
    }
}
