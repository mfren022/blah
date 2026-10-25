package com.ultimatesudoku.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryPuzzlesTest {
    private val solver = Solver()
    private val logic = LogicSolver()

    /** The techniques used solving [givens] a step at a time, always with the easiest that applies. */
    private fun techniquesUsed(givens: IntArray): Set<Technique>? {
        val state = LogicState(givens)
        val used = HashSet<Technique>()
        while (!state.isSolved) {
            val step = logic.nextStep(state, maxTier = 1) ?: return null
            used += step.technique
            state.apply(step)
        }
        return used
    }

    @Test
    fun everyLevelOfThePondHasItsFixedPuzzle() {
        val pond = LevelMap.forChapter(0)
        for (level in pond.levels) {
            val (givens, solution) = checkNotNull(StoryPuzzles.puzzle(0, level.number)) { "No puzzle for $level" }
            // One solution, the one stored.
            assertEquals(1, solver.countSolutions(givens, 2))
            assertArrayEquals(solution, solver.solve(givens))
            // Full houses and hidden singles are all it takes, as the chapter teaches...
            val used = checkNotNull(techniquesUsed(givens)) { "$level gets stuck" }
            assertTrue("$level needs $used", used.all { it == Technique.FULL_HOUSE || it == Technique.HIDDEN_SINGLE })
            // ...and it grades at its level's difficulty.
            val rating = logic.rate(givens)
            assertTrue(rating.solved)
            assertEquals("$level", level.difficulty, Generator.classify(rating, givens.count { it != 0 }))
        }
        assertEquals(null, StoryPuzzles.puzzle(0, pond.levels.size + 1))
    }

    @Test
    fun onlyThePondCanBePlayedSoFar() {
        assertTrue(StoryPuzzles.playable(0))
        for (chapter in 1 until 9) assertFalse(StoryPuzzles.playable(chapter))
        assertEquals(null, StoryPuzzles.puzzle(1, 1))
    }

    @Test
    fun tagsNameTheChapterAndLevel() {
        assertEquals(2 to 7, StoryPuzzles.parseTag(StoryPuzzles.tag(2, 7)))
        assertEquals(null, StoryPuzzles.parseTag("2026-10-25"))
        assertEquals(null, StoryPuzzles.parseTag(""))
    }
}
