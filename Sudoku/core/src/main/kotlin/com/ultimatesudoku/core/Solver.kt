package com.ultimatesudoku.core

import kotlin.random.Random

/**
 * Exhaustive solver: depth-first search that always branches on the empty cell with the fewest
 * candidates, tracking used digits per row, column and box as bitmasks. Typical puzzles take well under
 * a millisecond and ones built to defeat brute force a few milliseconds. The generator uses it to check
 * uniqueness.
 *
 * Not thread-safe: use one instance per thread.
 */
class Solver {
    private val values = IntArray(81)
    private val rowUsed = IntArray(9)
    private val colUsed = IntArray(9)
    private val boxUsed = IntArray(9)

    private var limit = 1
    private var found = 0
    private var firstSolution: IntArray? = null
    private var random: Random? = null
    private var nodeBudget = Long.MAX_VALUE

    /** Search nodes visited by the last call; a rough measure of how hard the puzzle is to brute-force. */
    var nodes = 0L
        private set

    /** Returns the number of solutions, stopping once [limit] are found. Conflicting clues give 0. */
    fun countSolutions(grid: IntArray, limit: Int = 2): Int {
        if (!load(grid)) return 0
        this.limit = limit
        random = null
        search()
        return found
    }

    /** The first solution found, or null when the puzzle has none. */
    fun solve(grid: IntArray): IntArray? {
        if (!load(grid)) return null
        limit = 1
        random = null
        search()
        return firstSolution
    }

    /**
     * Like [countSolutions], but also returns the first solution, and gives up (returning
     * [Analysis.Status.TOO_HARD]) after [maxNodes] search nodes so hostile input can't stall the UI.
     */
    fun analyze(grid: IntArray, maxNodes: Long = 2_000_000L): Analysis {
        if (!load(grid)) return Analysis(Analysis.Status.INVALID, null, 0)
        limit = 2
        random = null
        nodeBudget = maxNodes
        try {
            search()
        } catch (e: BudgetExceeded) {
            return Analysis(Analysis.Status.TOO_HARD, null, nodes)
        } finally {
            nodeBudget = Long.MAX_VALUE
        }
        val status = when (found) {
            0 -> Analysis.Status.NO_SOLUTION
            1 -> Analysis.Status.UNIQUE
            else -> Analysis.Status.MULTIPLE
        }
        return Analysis(status, firstSolution, nodes)
    }

    /** A random complete grid. */
    fun randomSolution(random: Random): IntArray {
        load(IntArray(81))
        limit = 1
        this.random = random
        search()
        this.random = null
        return firstSolution!!
    }

    private fun load(grid: IntArray): Boolean {
        require(grid.size == 81)
        rowUsed.fill(0)
        colUsed.fill(0)
        boxUsed.fill(0)
        found = 0
        firstSolution = null
        nodes = 0
        for (c in 0 until 81) {
            val v = grid[c]
            values[c] = v
            if (v == 0) continue
            val bit = 1 shl (v - 1)
            val r = ROW[c]
            val k = COL[c]
            val b = BOX[c]
            if ((rowUsed[r] or colUsed[k] or boxUsed[b]) and bit != 0) return false
            rowUsed[r] = rowUsed[r] or bit
            colUsed[k] = colUsed[k] or bit
            boxUsed[b] = boxUsed[b] or bit
        }
        return true
    }

    /** Returns true once enough solutions have been found to stop. */
    private fun search(): Boolean {
        if (++nodes > nodeBudget) throw BudgetExceeded()
        var best = -1
        var bestMask = 0
        var bestCount = 10
        for (c in 0 until 81) {
            if (values[c] != 0) continue
            val mask = Grid.ALL_DIGITS and (rowUsed[ROW[c]] or colUsed[COL[c]] or boxUsed[BOX[c]]).inv()
            val count = mask.countOneBits()
            if (count < bestCount) {
                best = c
                bestMask = mask
                bestCount = count
                if (count <= 1) break
            }
        }
        if (best < 0) {
            found++
            if (firstSolution == null) firstSolution = values.copyOf()
            return found >= limit
        }
        if (bestCount == 0) return false

        val r = ROW[best]
        val k = COL[best]
        val b = BOX[best]
        val rng = random
        if (rng == null) {
            var mask = bestMask
            while (mask != 0) {
                val bit = mask and -mask
                mask = mask xor bit
                if (tryDigit(best, r, k, b, bit)) return true
            }
        } else {
            val digits = Grid.digits(bestMask)
            for (i in digits.size - 1 downTo 1) {
                val j = rng.nextInt(i + 1)
                val t = digits[i]
                digits[i] = digits[j]
                digits[j] = t
            }
            for (d in digits) if (tryDigit(best, r, k, b, 1 shl (d - 1))) return true
        }
        return false
    }

    private fun tryDigit(cell: Int, r: Int, k: Int, b: Int, bit: Int): Boolean {
        values[cell] = bit.countTrailingZeroBits() + 1
        rowUsed[r] = rowUsed[r] or bit
        colUsed[k] = colUsed[k] or bit
        boxUsed[b] = boxUsed[b] or bit
        val stop = search()
        rowUsed[r] = rowUsed[r] xor bit
        colUsed[k] = colUsed[k] xor bit
        boxUsed[b] = boxUsed[b] xor bit
        values[cell] = 0
        return stop
    }

    private class BudgetExceeded : RuntimeException() {
        override fun fillInStackTrace(): Throwable = this
    }

    class Analysis(val status: Status, val solution: IntArray?, val nodes: Long) {
        enum class Status { INVALID, NO_SOLUTION, UNIQUE, MULTIPLE, TOO_HARD }
    }

    private companion object {
        val ROW = IntArray(81) { Grid.row(it) }
        val COL = IntArray(81) { Grid.col(it) }
        val BOX = IntArray(81) { Grid.box(it) }
    }
}
