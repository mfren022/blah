package com.ultimatesudoku.core

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicSolverTest {
    private val logic = LogicSolver()

    @Test
    fun everyStepAgreesWithTheSolution() {
        val solver = Solver()
        val random = Random(7)
        val used = IntArray(Technique.entries.size)
        var stuck = 0
        repeat(PUZZLES) {
            val solution = solver.randomSolution(random)
            val puzzle = minimalPuzzle(solution, random, solver)
            val state = LogicState(puzzle)
            while (!state.isSolved) {
                val step = logic.nextStep(state) ?: break
                assertTrue("Step makes no progress: $step", step.placements.isNotEmpty() || step.eliminations.isNotEmpty())
                for (p in step.placements) {
                    assertEquals("Wrong placement by $step\n${Grid.format(puzzle)}", solution[p.cell], p.digit)
                }
                for (e in step.eliminations) {
                    assertTrue("Removed the answer: $step\n${Grid.format(puzzle)}", solution[e.cell] != e.digit)
                    assertTrue("Removed a missing candidate: $step", Grid.has(state.candidates[e.cell], e.digit))
                }
                used[step.technique.ordinal]++
                state.apply(step)
            }
            if (!state.isSolved) stuck++
        }
        println("Technique use over $PUZZLES minimal puzzles (stuck: $stuck):")
        Technique.entries.forEach { println("  %-18s %6d".format(it.title, used[it.ordinal])) }
        // Each technique must have fired at least once, or it is likely dead code. Hidden quads and
        // jellyfish are the complements of smaller subsets and fish that are tried first, so they are
        // too rare to show up here; they share code with those and are tested directly below.
        val rare = setOf(Technique.HIDDEN_QUAD, Technique.JELLYFISH)
        for (t in Technique.entries - rare) assertTrue("${t.title} never used", used[t.ordinal] > 0)
    }

    @Test
    fun findsJellyfish() {
        // Digit 1 in rows 1, 3, 5 and 7 is confined to columns 1, 3, 5 and 7; elsewhere it is open.
        val values = IntArray(81)
        val candidates = IntArray(81) { Grid.ALL_DIGITS }
        val baseRows = listOf(0, 2, 4, 6)
        val coverCols = listOf(0, 2, 4, 6)
        for ((i, r) in baseRows.withIndex()) {
            for (c in 0 until 9) {
                val keep = c in coverCols && c != coverCols[i] // each base row misses one cover column
                if (!keep) candidates[Grid.cell(r, c)] = candidates[Grid.cell(r, c)] and Grid.bit(1).inv()
            }
        }
        val step = logic.fish(LogicState(values, candidates), 4)!!
        assertEquals(Technique.JELLYFISH, step.technique)
        val expected = (0 until 9).filter { it !in baseRows }.flatMap { r -> coverCols.map { Grid.cell(r, it) } }
        assertEquals(expected.map { Candidate(it, 1) }.toSet(), step.eliminations.toSet())
    }

    @Test
    fun findsHiddenQuad() {
        // In row 1, digits 1-4 only fit in its first four cells, which also allow 5-9.
        val values = IntArray(81)
        val candidates = IntArray(81) { Grid.ALL_DIGITS }
        val low = Grid.bit(1) or Grid.bit(2) or Grid.bit(3) or Grid.bit(4)
        for (c in 4 until 9) candidates[c] = Grid.ALL_DIGITS and low.inv()
        val step = logic.hiddenSubset(LogicState(values, candidates), 4)!!
        assertEquals(Technique.HIDDEN_QUAD, step.technique)
        val expected = (0 until 4).flatMap { c -> (5..9).map { Candidate(c, it) } }
        assertEquals(expected.toSet(), step.eliminations.toSet())
    }

    @Test
    fun rateMatchesStepByStepSolving() {
        val solver = Solver()
        val random = Random(11)
        repeat(60) {
            val puzzle = minimalPuzzle(solver.randomSolution(random), random, solver)
            val rating = logic.rate(puzzle)
            val state = LogicState(puzzle)
            var tier = 0
            while (!state.isSolved) {
                val step = logic.nextStep(state) ?: break
                tier = maxOf(tier, step.technique.tier)
                state.apply(step)
            }
            assertEquals(state.isSolved, rating.solved)
            if (rating.solved) assertEquals(tier, rating.tier)
        }
    }

    @Test
    fun explainsHiddenSingle() {
        // Classic newspaper puzzle: the first step is a single.
        val state = LogicState(Grid.parse(SolverTest.WIKIPEDIA)!!)
        val step = logic.nextStep(state)!!
        assertEquals(1, step.technique.tier)
        assertEquals(1, step.placements.size)
        assertTrue(step.description.isNotBlank())
    }

    @Test
    fun maxTierLimitsTechniques() {
        val puzzle = Grid.parse(SolverTest.INKALA)!!
        val rating = logic.rate(puzzle, maxTier = 1)
        assertFalse(rating.solved)
    }

    @Test
    fun uniquenessTechniquesCanBeDisabled() {
        val strict = LogicSolver(assumeUnique = false)
        val solver = Solver()
        val random = Random(3)
        repeat(40) {
            val solution = solver.randomSolution(random)
            val state = LogicState(minimalPuzzle(solution, random, solver))
            while (!state.isSolved) {
                val step = strict.nextStep(state) ?: break
                assertTrue(step.technique != Technique.UNIQUE_RECTANGLE && step.technique != Technique.BUG_PLUS_ONE)
                state.apply(step)
            }
        }
    }

    companion object {
        const val PUZZLES = 1500

        /** Removes clues in random order while the solution stays unique. */
        fun minimalPuzzle(solution: IntArray, random: Random, solver: Solver): IntArray {
            val givens = solution.copyOf()
            for (cell in (0 until 81).shuffled(random)) {
                val digit = givens[cell]
                givens[cell] = 0
                if (solver.countSolutions(givens, 2) != 1) givens[cell] = digit
            }
            return givens
        }
    }
}
