package com.ultimatesudoku.core

import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolverTest {
    private val solver = Solver()

    @Test
    fun solvesClassicPuzzle() {
        val solution = solver.solve(Grid.parse(WIKIPEDIA)!!)
        assertArrayEquals(Grid.parse(WIKIPEDIA_SOLUTION), solution)
        assertEquals(1, solver.countSolutions(Grid.parse(WIKIPEDIA)!!))
    }

    @Test
    fun solvesVeryHardPuzzleQuickly() {
        val puzzle = Grid.parse(INKALA)!!
        val analysis = solver.analyze(puzzle)
        assertEquals(Solver.Analysis.Status.UNIQUE, analysis.status)
        assertValidSolution(puzzle, analysis.solution!!)
    }

    @Test
    fun emptyGridHasManySolutions() {
        assertEquals(2, solver.countSolutions(IntArray(81), 2))
        assertEquals(Solver.Analysis.Status.MULTIPLE, solver.analyze(IntArray(81)).status)
    }

    @Test
    fun conflictingCluesHaveNoSolution() {
        val grid = IntArray(81)
        grid[0] = 5
        grid[8] = 5
        assertEquals(0, solver.countSolutions(grid))
        assertNull(solver.solve(grid))
        assertEquals(Solver.Analysis.Status.INVALID, solver.analyze(grid).status)
    }

    @Test
    fun unsolvableButConsistentClues() {
        // Row 1 needs a 9 in R1C9, but column 9 already has one.
        val grid = Grid.parse("12345678." + ".".repeat(8) + "9" + ".".repeat(63))!!
        assertEquals(Solver.Analysis.Status.NO_SOLUTION, solver.analyze(grid).status)
    }

    @Test
    fun randomSolutionsAreValidAndVaried() {
        val random = Random(42)
        val a = solver.randomSolution(random)
        val b = solver.randomSolution(random)
        assertValidSolution(IntArray(81), a)
        assertValidSolution(IntArray(81), b)
        assertTrue(!a.contentEquals(b))
    }

    @Test
    fun parseAndFormatRoundTrip() {
        val grid = Grid.parse(WIKIPEDIA)!!
        assertEquals(WIKIPEDIA.replace('0', '.'), Grid.format(grid))
        assertNotNull(Grid.parse(Grid.format(grid)))
        assertNull(Grid.parse("123"))
    }

    companion object {
        const val WIKIPEDIA = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
        const val WIKIPEDIA_SOLUTION = "534678912672195348198342567859761423426853791713924856961537284287419635345286179"
        const val INKALA = "800000000003600000070090200050007000000045700000100030001000068008500010090000400"

        fun assertValidSolution(givens: IntArray, solution: IntArray) {
            for (c in 0 until 81) {
                assertTrue(solution[c] in 1..9)
                if (givens[c] != 0) assertEquals(givens[c], solution[c])
            }
            for (unit in Grid.UNITS) assertEquals((1..9).toSet(), unit.map { solution[it] }.toSet())
        }
    }
}
