package com.ultimatesudoku.core

import kotlin.random.Random

enum class Difficulty(val title: String, val blurb: String) {
    EASY("Easy", "Singles only. A relaxed warm-up."),
    MEDIUM("Medium", "Fewer clues, plus pointing and pairs."),
    HARD("Hard", "Triples, X-Wings and kites."),
    EXPERT("Expert", "Wings, Swordfish and coloring."),
    EXTREME("Extreme", "Chains, Jellyfish and beyond."),
}

class Puzzle(
    val givens: IntArray,
    val solution: IntArray,
    val difficulty: Difficulty,
    val rating: Rating,
) {
    val clues: Int get() = givens.count { it != 0 }
}

/**
 * Makes puzzles with exactly one solution and grades them with [LogicSolver]:
 *
 * - Easy and Medium remove clues while the puzzle stays within reach of the allowed techniques,
 *   down to a clue count that sets the pace.
 * - Hard and Expert remove clues while the techniques of their tier still finish the puzzle, and
 *   keep the result once it actually needs that tier.
 * - Extreme removes as many clues as uniqueness allows and keeps puzzles that need tier 5 techniques
 *   or more than this solver knows.
 *
 * Clues are removed in symmetric pairs (180-degree rotation), as in printed puzzles. Generation is
 * deterministic for a given [random] seed, which is what makes daily challenges the same for everyone.
 */
class Generator(private val random: Random) {
    private val solver = Solver()
    private val logic = LogicSolver()

    fun generate(difficulty: Difficulty, maxAttempts: Int = 200): Puzzle {
        var best: Puzzle? = null
        repeat(maxAttempts) {
            val puzzle = attempt(difficulty)
            if (puzzle.difficulty == difficulty) return puzzle
            if (best == null || distance(puzzle, difficulty) < distance(best!!, difficulty)) best = puzzle
        }
        return best!!
    }

    private fun distance(p: Puzzle, target: Difficulty) = kotlin.math.abs(p.difficulty.ordinal - target.ordinal)

    private fun attempt(target: Difficulty): Puzzle {
        val solution = solver.randomSolution(random)
        val givens = solution.copyOf()
        val maxTier = MAX_TIER[target.ordinal]
        val minClues = MIN_CLUES[target.ordinal]

        val order = IntArray(41) { it }
        for (i in order.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val t = order[i]
            order[i] = order[j]
            order[j] = t
        }
        var clues = 81
        for (cell in order) {
            val mirror = 80 - cell
            val removing = if (cell == mirror) 1 else 2
            if (clues - removing < minClues) continue
            givens[cell] = 0
            givens[mirror] = 0
            val keep = solver.countSolutions(givens, 2) == 1 &&
                (maxTier >= 6 || logic.rate(givens, maxTier).solved)
            if (keep) {
                clues -= removing
            } else {
                givens[cell] = solution[cell]
                givens[mirror] = solution[mirror]
            }
        }
        val rating = logic.rate(givens)
        return Puzzle(givens, solution, classify(rating, clues), rating)
    }

    companion object {
        /** Highest technique tier each difficulty may require while removing clues (6 = anything). */
        private val MAX_TIER = intArrayOf(1, 2, 3, 4, 6)

        /** Clue floor per difficulty; lower floors make puzzles slower to solve. */
        private val MIN_CLUES = intArrayOf(38, 30, 0, 0, 0)

        /** The difficulty a rated puzzle belongs to. */
        fun classify(rating: Rating, clues: Int): Difficulty = when {
            rating.tier >= 5 -> Difficulty.EXTREME
            rating.tier == 4 -> Difficulty.EXPERT
            rating.tier == 3 -> Difficulty.HARD
            rating.tier == 2 -> Difficulty.MEDIUM
            clues >= 36 -> Difficulty.EASY
            else -> Difficulty.MEDIUM
        }
    }
}
