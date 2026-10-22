package com.ultimatesudoku.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolvedBoardTest {
    private val givens = Grid.parse(SolverTest.WIKIPEDIA)!!
    private val solution = Grid.parse(SolverTest.WIKIPEDIA_SOLUTION)!!

    private fun wonGame(): Game {
        val game = Game(GameMode.DAILY, Difficulty.MEDIUM, givens, solution, "2026-10-22")
        val wrongCell = (0 until 81).first { givens[it] == 0 }
        game.enter(wrongCell, (1..9).first { it != solution[wrongCell] }, true, true)
        for (c in 0 until 81) if (givens[c] == 0) game.enter(c, solution[c], true, true)
        game.elapsedMs = 754_321
        return game
    }

    @Test
    fun recordsTheStartingBoardAndTime() {
        val board = SolvedBoard.of(wonGame(), completedAt = 1_792_639_528_000)
        assertEquals(Grid.format(givens), Grid.format(board.givens))
        assertEquals(Grid.format(solution), Grid.format(board.solution))
        assertEquals(754_321, board.timeMs)
        assertEquals(1, board.mistakes)
        assertEquals(GameMode.DAILY, board.mode)
        assertEquals("2026-10-22", board.tag)
        assertEquals(30, board.clues)
        assertTrue(!board.isPerfect)
    }

    @Test(expected = IllegalArgumentException::class)
    fun onlyWonGamesAreRecorded() {
        SolvedBoard.of(Game(GameMode.RANDOM, Difficulty.EASY, givens, solution), 0)
    }

    @Test
    fun exportsCsv() {
        val board = SolvedBoard.of(wonGame(), completedAt = 1_792_639_528_000)
        val lines = SolvedBoard.toCsv(listOf(board)).trim().lines()
        assertEquals(SolvedBoard.CSV_HEADER, lines[0])
        val fields = lines[1].split(',')
        assertEquals(11, fields.size)
        assertEquals("2026-10-22T03:25:28Z", fields[0])
        assertEquals("DAILY", fields[1])
        assertEquals("12:34", fields[5])
        assertEquals("754321", fields[6])
        assertEquals(SolverTest.WIKIPEDIA, fields[9])
        assertEquals(SolverTest.WIKIPEDIA_SOLUTION, fields[10])
    }

    @Test
    fun formatsDurations() {
        assertEquals("0:05", formatDuration(5_999))
        assertEquals("12:30", formatDuration(750_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
    }
}
