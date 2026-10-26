package com.ultimatesudoku.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategiesTest {
    private val solver = Solver()
    private val logic = LogicSolver()

    @Test
    fun everyTechniqueIsTaughtInOrder() {
        assertEquals(Technique.entries, Strategies.ALL.map { it.technique })
        for (strategy in Strategies.ALL) {
            assertSame(strategy, Strategies.of(strategy.technique))
            assertEquals(strategy.technique.ordinal + 1, strategy.number)
            for (text in listOf(strategy.summary, strategy.howItWorks, strategy.howToSpot)) {
                assertTrue("${strategy.technique}", text.isNotBlank() && text.endsWith("."))
            }
            assertEquals(3, strategy.examples.size)
        }
        // Simplest first: the tiers never go back down.
        assertEquals(Strategies.ALL.map { it.technique.tier }.sorted(), Strategies.ALL.map { it.technique.tier })
        assertEquals(5, Strategies.TIERS.size)
    }

    @Test
    fun everyExampleIsARealPositionWhereItsTechniqueWorks() {
        for (strategy in Strategies.ALL) {
            for (example in strategy.examples) {
                val name = "${strategy.technique.title} in ${Grid.format(example.values)}"
                // A position from a proper puzzle: consistent, clues kept, one solution.
                assertTrue(name, Grid.isConsistent(example.values))
                assertTrue(name, (0 until 81).all { example.givens[it] == 0 || example.givens[it] == example.values[it] })
                assertEquals(name, 1, solver.countSolutions(example.values, 2))
                val solution = checkNotNull(solver.solve(example.values))
                // The step is the technique's, and right.
                val step = example.step
                assertEquals(name, strategy.technique, step.technique)
                assertTrue(name, step.placements.isNotEmpty() || step.eliminations.isNotEmpty())
                for (p in step.placements) assertEquals(name, solution[p.cell], p.digit)
                for (e in step.eliminations) assertTrue(name, solution[e.cell] != e.digit && Grid.has(example.candidates[e.cell], e.digit))
                // Beyond singles, there's no single to take instead.
                if (strategy.technique.tier > 1) assertNull(name, logic.nextStep(LogicState(example.values), maxTier = 1))
            }
        }
    }

    @Test
    fun everyExampleHasAWalkthrough() {
        for (strategy in Strategies.ALL) {
            for (example in strategy.examples) {
                val name = "${strategy.technique.title} in ${Grid.format(example.values)}"
                val lesson = example.lesson
                assertTrue(name, lesson.answers.isNotEmpty())
                assertTrue(name, lesson.answers.all { example.values[it] == 0 })
                assertEquals(name, 3, lesson.frames.size)
                // The result shows the step itself; nothing before it gives the answer away.
                assertSame(name, example.step, lesson.frames.last().overlay)
                for (frame in listOf(lesson.challenge) + lesson.frames.dropLast(1)) {
                    assertTrue(name, frame.overlay.placements.isEmpty() && frame.overlay.eliminations.isEmpty())
                    // Without pencil marks, a cell shows candidates only when the overlay picks it out.
                    if (!strategy.showsCandidates) {
                        for (p in example.step.placements) {
                            if (p.cell in frame.overlay.cells) assertEquals(name, 0, frame.candidates[p.cell])
                        }
                    }
                }
                for (frame in listOf(lesson.challenge) + lesson.frames) {
                    assertTrue(name, frame.text.isNotBlank() && !frame.text.contains("  "))
                    // Highlighted candidates are drawn only in the overlay's cells, so they must be there.
                    val cells = frame.overlay.cells.toSet()
                    assertTrue(name, (frame.overlay.highlights + frame.overlay.altHighlights).all { it.cell in cells })
                    assertTrue(name, (frame.overlay.highlights + frame.overlay.altHighlights).all { Grid.has(frame.candidates[it.cell], it.digit) })
                }
                for (cell in lesson.answers) assertTrue(name, lesson.outcome(cell).startsWith(Grid.cellName(cell)))
            }
        }
    }

    @Test
    fun aHiddenSingleIsWalkedThrough() {
        val example = Strategies.of(Technique.HIDDEN_SINGLE).examples[0]
        val (cell, digit) = example.step.placements[0]
        val box = example.step.units[0]
        val lesson = example.lesson
        assertEquals(setOf(cell), lesson.answers)
        assertTrue(lesson.challenge.text.startsWith("${Grid.unitName(box).replaceFirstChar { it.uppercaseChar() }} still needs a $digit"))
        // The second frame names the digits that block the rest of the box, and lights them up.
        val blockers = lesson.frames[1].overlay.cells - cell
        assertTrue(blockers.isNotEmpty() && blockers.all { example.values[it] == digit })
        val others = Grid.UNITS[box].filter { example.values[it] == 0 && it != cell }
        assertTrue(others.all { c -> blockers.any { Grid.sees(it, c) } })
        assertEquals("${Grid.cellName(cell)} is $digit.", lesson.outcome(cell))
    }

    @Test
    fun anXWingIsWalkedThrough() {
        val example = Strategies.of(Technique.X_WING).examples[0]
        val lesson = example.lesson
        assertEquals(example.step.eliminations.map { it.cell }.toSet(), lesson.answers)
        assertEquals(2, lesson.frames[0].overlay.units.size)
        assertEquals(4, lesson.frames[1].overlay.units.size)
        assertTrue(lesson.frames[0].text, lesson.frames[0].text.contains("just two spots each"))
        val first = lesson.answers.first()
        assertEquals("${Grid.cellName(first)} can't be ${example.step.eliminations.first { it.cell == first }.digit}.", lesson.outcome(first))
    }

    @Test
    fun findLooksForOneTechniqueOnly() {
        val example = Strategies.of(Technique.XY_WING).examples[0]
        val state = LogicState(example.values.copyOf(), example.candidates.copyOf())
        assertEquals(Technique.XY_WING, logic.find(Technique.XY_WING, state)?.technique)
        assertNull(logic.find(Technique.FULL_HOUSE, state))
        assertNotNull(logic.nextStep(state))
        assertFalse(LogicSolver(assumeUnique = false).find(Technique.UNIQUE_RECTANGLE, LogicState(Strategies.of(Technique.UNIQUE_RECTANGLE).examples[0].values)) != null)
    }
}
