package com.ultimatesudoku.core

import java.time.Instant
import java.util.Locale

/** A finished puzzle: the board it started from, how long it took and how it went. */
class SolvedBoard(
    /** When it was solved, in epoch milliseconds. */
    val completedAt: Long,
    val mode: GameMode,
    val difficulty: Difficulty,
    /** The starting combination: the clues, with 0 for empty cells. */
    val givens: IntArray,
    val solution: IntArray,
    /** Time spent solving, excluding pauses. */
    val timeMs: Long,
    val mistakes: Int,
    val hints: Int,
    /** Identifies the puzzle within its mode, e.g. the date of a daily challenge. */
    val tag: String = "",
    /** Storage id, 0 until saved. */
    val id: Long = 0,
) {
    val clues: Int get() = givens.count { it != 0 }
    val isPerfect: Boolean get() = mistakes == 0 && hints == 0

    fun withId(id: Long) =
        SolvedBoard(completedAt, mode, difficulty, givens, solution, timeMs, mistakes, hints, tag, id)

    companion object {
        /** The record of [game], which must have been won. */
        fun of(game: Game, completedAt: Long): SolvedBoard {
            require(game.status == Game.Status.WON) { "Only won games are recorded" }
            return SolvedBoard(
                completedAt = completedAt,
                mode = game.mode,
                difficulty = game.difficulty,
                givens = game.givens.copyOf(),
                solution = game.solution.copyOf(),
                timeMs = game.elapsedMs,
                mistakes = game.mistakes,
                hints = game.hintsUsed,
                tag = game.tag,
            )
        }

        const val CSV_HEADER = "completed_at,mode,difficulty,tag,clues,time,time_ms,mistakes,hints,givens,solution"

        /** A spreadsheet-friendly export: one row per board, cells as 81-digit strings with 0 for empty. */
        fun toCsv(boards: List<SolvedBoard>): String = buildString {
            append(CSV_HEADER).append('\n')
            for (b in boards) {
                val fields = listOf(
                    Instant.ofEpochMilli(b.completedAt).toString(),
                    b.mode.name,
                    b.difficulty.name,
                    b.tag,
                    b.clues.toString(),
                    formatDuration(b.timeMs),
                    b.timeMs.toString(),
                    b.mistakes.toString(),
                    b.hints.toString(),
                    Grid.format(b.givens, '0'),
                    Grid.format(b.solution, '0'),
                )
                fields.joinTo(this, ",") { csvField(it) }
                append('\n')
            }
        }

        private fun csvField(value: String) =
            if (value.any { it == ',' || it == '"' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value
    }
}

/** "4:05", "12:30" or "1:02:03". */
fun formatDuration(ms: Long, locale: Locale = Locale.ROOT): String {
    val total = ms / 1000
    val h = total / 3600
    val m = total / 60 % 60
    val s = total % 60
    return if (h > 0) String.format(locale, "%d:%02d:%02d", h, m, s) else String.format(locale, "%d:%02d", m, s)
}
