package com.ultimatesudoku.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {
    private val givens = Grid.parse(SolverTest.WIKIPEDIA)!!
    private val solution = Grid.parse(SolverTest.WIKIPEDIA_SOLUTION)!!

    private fun newGame() = Game(GameMode.RANDOM, Difficulty.EASY, givens, solution)
    private fun firstEmpty() = (0 until 81).first { givens[it] == 0 }

    @Test
    fun correctAndWrongEntries() {
        val game = newGame()
        val cell = firstEmpty()
        val wrong = (1..9).first { it != solution[cell] }
        assertEquals(Game.Entry.WRONG, game.enter(cell, wrong, autoRemoveNotes = true, trackMistakes = true))
        assertEquals(1, game.mistakes)
        assertTrue(game.isWrong(cell))
        assertEquals(Game.Entry.PLACED, game.enter(cell, solution[cell], autoRemoveNotes = true, trackMistakes = true))
        assertEquals(1, game.mistakes)
        assertEquals(Game.Entry.IGNORED, game.enter(0, 9, autoRemoveNotes = true, trackMistakes = true))
    }

    @Test
    fun mistakesAreHiddenWhenNotTracked() {
        val game = newGame()
        val cell = firstEmpty()
        val wrong = (1..9).first { it != solution[cell] }
        assertEquals(Game.Entry.PLACED, game.enter(cell, wrong, autoRemoveNotes = true, trackMistakes = false))
        assertEquals(0, game.mistakes)
    }

    @Test
    fun notesUndoAndAutoRemoval() {
        val game = newGame()
        val cell = firstEmpty()
        val digit = solution[cell]
        val peer = Grid.PEERS[cell].first { givens[it] == 0 }
        assertTrue(game.toggleNote(peer, digit))
        assertTrue(Grid.has(game.notes[peer], digit))
        game.enter(cell, digit, autoRemoveNotes = true, trackMistakes = true)
        assertFalse(Grid.has(game.notes[peer], digit))
        assertEquals(cell, game.undo())
        assertEquals(0, game.values[cell])
        assertTrue(Grid.has(game.notes[peer], digit))
        assertEquals(peer, game.undo())
        assertEquals(0, game.notes[peer])
        assertEquals(-1, game.undo())
    }

    @Test
    fun fillNotesMatchesCandidates() {
        val game = newGame()
        assertTrue(game.fillNotes())
        assertArrayEquals(Grid.candidates(givens), game.notes)
        assertTrue(game.clearAllNotes())
        assertTrue(game.notes.all { it == 0 })
    }

    @Test
    fun hintsLeadToTheSolution() {
        val game = newGame()
        val logic = LogicSolver()
        var guard = 0
        while (game.isPlaying && guard++ < 500) {
            val hint = game.findHint(logic)
            assertNotNull(hint)
            game.countHint()
            game.applyHint(hint!!, autoRemoveNotes = true)
        }
        assertEquals(Game.Status.WON, game.status)
        assertArrayEquals(solution, game.values)
    }

    @Test
    fun hintPointsAtMistakesFirst() {
        val game = newGame()
        val cell = firstEmpty()
        game.enter(cell, (1..9).first { it != solution[cell] }, autoRemoveNotes = true, trackMistakes = true)
        val hint = game.findHint(LogicSolver())
        assertTrue(hint is Game.Hint.Mistake && hint.cell == cell)
    }

    @Test
    fun winningAndRevealing() {
        val game = newGame()
        val last = (0 until 81).last { givens[it] == 0 }
        for (c in 0 until 81) if (givens[c] == 0 && c != last) game.enter(c, solution[c], true, true)
        assertTrue(game.isPlaying)
        assertEquals(Game.Entry.COMPLETED, game.enter(last, solution[last], true, true))
        assertEquals(Game.Status.WON, game.status)
        assertEquals(1f, game.progress)

        val other = newGame()
        other.revealAll()
        assertEquals(Game.Status.REVEALED, other.status)
        assertFalse(other.canUndo)
    }

    @Test
    fun lossAndKeepPlaying() {
        val game = newGame()
        game.markLost()
        assertEquals(Game.Status.LOST, game.status)
        game.keepPlaying()
        assertTrue(game.isPlaying)
        assertTrue(game.limitWaived)
        game.restart()
        assertFalse(game.limitWaived)
    }

    @Test
    fun saveAndRestore() {
        val game = Game(GameMode.DAILY, Difficulty.HARD, givens, solution, "2026-10-22")
        val cell = firstEmpty()
        game.enter(cell, (1..9).first { it != solution[cell] }, true, true)
        game.toggleNote(cell + 1, 4)
        game.toggleNote(cell + 1, 9)
        game.elapsedMs = 123_456
        val copy = Game.deserialize(game.serialize())!!
        assertEquals(GameMode.DAILY, copy.mode)
        assertEquals(Difficulty.HARD, copy.difficulty)
        assertEquals("2026-10-22", copy.tag)
        assertArrayEquals(game.values, copy.values)
        assertArrayEquals(game.notes, copy.notes)
        assertEquals(1, copy.mistakes)
        assertEquals(123_456, copy.elapsedMs)
        // The undo history survives too.
        assertEquals(cell + 1, copy.undo())
        assertEquals(cell + 1, copy.undo())
        assertEquals(cell, copy.undo())
        assertNull(Game.deserialize("garbage"))
    }

    @Test
    fun dailyPuzzleIsStable() {
        val a = Daily.puzzle(2026, 10, 22, 4)
        val b = Daily.puzzle(2026, 10, 22, 4)
        assertArrayEquals(a.givens, b.givens)
        assertEquals(Difficulty.HARD, a.difficulty)
        assertEquals("2026-10-22", Daily.tag(2026, 10, 22))
    }
}
