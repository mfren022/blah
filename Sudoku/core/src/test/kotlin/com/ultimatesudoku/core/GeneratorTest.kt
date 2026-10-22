package com.ultimatesudoku.core

import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorTest {
    @Test
    fun everyDifficultyGeneratesUniqueGradedPuzzles() {
        val solver = Solver()
        for (difficulty in Difficulty.entries) {
            val generator = Generator(Random(difficulty.ordinal + 100L))
            var totalMs = 0L
            var maxMs = 0L
            var hits = 0
            val runs = 12
            val clues = ArrayList<Int>()
            repeat(runs) {
                val start = System.nanoTime()
                val puzzle = generator.generate(difficulty)
                val ms = (System.nanoTime() - start) / 1_000_000
                totalMs += ms
                maxMs = maxOf(maxMs, ms)
                if (puzzle.difficulty == difficulty) hits++
                clues += puzzle.clues
                assertEquals(1, solver.countSolutions(puzzle.givens, 2))
                assertArrayEquals(puzzle.solution, solver.solve(puzzle.givens))
                for (c in 0 until 81) if (puzzle.givens[c] != 0) assertEquals(puzzle.solution[c], puzzle.givens[c])
            }
            println(
                "%-8s hit %2d/%d  avg %4d ms  max %5d ms  clues %s".format(
                    difficulty.title, hits, runs, totalMs / runs, maxMs, clues.sorted().joinToString(","),
                ),
            )
            assertEquals("Generated a different difficulty than asked for", runs, hits)
        }
    }

    @Test
    fun sameSeedGivesSamePuzzle() {
        val a = Generator(Random(20261022)).generate(Difficulty.HARD)
        val b = Generator(Random(20261022)).generate(Difficulty.HARD)
        assertArrayEquals(a.givens, b.givens)
    }

    @Test
    fun symmetricClues() {
        val puzzle = Generator(Random(5)).generate(Difficulty.MEDIUM)
        for (c in 0 until 81) assertEquals(puzzle.givens[c] == 0, puzzle.givens[80 - c] == 0)
        assertTrue(puzzle.clues in 30..35)
    }
}
