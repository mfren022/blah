package com.ultimatesudoku.core

enum class GameMode { RANDOM, DAILY, CUSTOM, STORY }

/**
 * A puzzle being played: the digits and notes entered so far, an undo history, mistakes, hints and
 * time. Mistakes and hints are never undone.
 */
class Game(
    val mode: GameMode,
    val difficulty: Difficulty,
    val givens: IntArray,
    val solution: IntArray,
    /** Identifies the puzzle within its mode, e.g. the date of a daily challenge. */
    val tag: String = "",
) {
    enum class Status { PLAYING, WON, LOST, REVEALED }

    enum class Entry { IGNORED, PLACED, WRONG, COMPLETED }

    sealed class Hint {
        /** A digit the player entered doesn't match the solution. */
        class Mistake(val cell: Int) : Hint()

        /** The next logical deduction from the current position. */
        class Logic(val step: Step) : Hint()

        /** Nothing in the solver's toolbox applies; the cell's digit is given away. */
        class Reveal(val cell: Int, val digit: Int) : Hint()
    }

    private class Change(val cell: Int, val oldValue: Int, val oldNotes: Int, val newValue: Int, val newNotes: Int)

    val values: IntArray = givens.copyOf()
    val notes = IntArray(81)
    var mistakes = 0
        private set
    var hintsUsed = 0
        private set
    var elapsedMs = 0L
    var status = Status.PLAYING
        private set

    /** The player chose to keep going after reaching the mistake limit. */
    var limitWaived = false
        private set

    private val history = ArrayList<List<Change>>()

    val canUndo: Boolean get() = status == Status.PLAYING && history.isNotEmpty()
    val isPlaying: Boolean get() = status == Status.PLAYING
    val isFull: Boolean get() = values.none { it == 0 }
    val isPerfect: Boolean get() = mistakes == 0 && hintsUsed == 0

    fun isGiven(cell: Int) = givens[cell] != 0
    fun isWrong(cell: Int) = values[cell] != 0 && values[cell] != solution[cell]

    /** How many of [digit] are still to be placed. */
    fun remaining(digit: Int) = maxOf(0, 9 - values.count { it == digit })

    /** Share of the empty cells filled in correctly. */
    val progress: Float
        get() {
            val open = givens.count { it == 0 }
            if (open == 0) return 1f
            val done = (0 until 81).count { givens[it] == 0 && values[it] == solution[it] }
            return done.toFloat() / open
        }

    /**
     * Writes [digit] into [cell]. With [trackMistakes] a wrong digit counts as a mistake; with
     * [autoRemoveNotes] the digit is erased from the notes of the cells it sees (unless it is a
     * known mistake).
     */
    fun enter(cell: Int, digit: Int, autoRemoveNotes: Boolean, trackMistakes: Boolean): Entry {
        if (!isPlaying || isGiven(cell) || values[cell] == digit) return Entry.IGNORED
        val correct = digit == solution[cell]
        val changes = arrayListOf(Change(cell, values[cell], notes[cell], digit, 0))
        if (autoRemoveNotes && (correct || !trackMistakes)) changes += clearNotesAround(cell, digit)
        commit(changes)
        if (!correct && trackMistakes) mistakes++
        if (values.contentEquals(solution)) {
            status = Status.WON
            return Entry.COMPLETED
        }
        return if (!correct && trackMistakes) Entry.WRONG else Entry.PLACED
    }

    fun toggleNote(cell: Int, digit: Int): Boolean {
        if (!isPlaying || isGiven(cell) || values[cell] != 0) return false
        commit(listOf(Change(cell, 0, notes[cell], 0, notes[cell] xor Grid.bit(digit))))
        return true
    }

    fun erase(cell: Int): Boolean {
        if (!isPlaying || isGiven(cell) || (values[cell] == 0 && notes[cell] == 0)) return false
        commit(listOf(Change(cell, values[cell], notes[cell], 0, 0)))
        return true
    }

    /** Reverts the last move and returns the cell it touched, or -1 when there is nothing to undo. */
    fun undo(): Int {
        if (!canUndo) return -1
        val move = history.removeAt(history.lastIndex)
        for (change in move.asReversed()) {
            values[change.cell] = change.oldValue
            notes[change.cell] = change.oldNotes
        }
        return move.first().cell
    }

    /** Fills every empty cell's notes with the digits its row, column and box still allow. */
    fun fillNotes(): Boolean {
        if (!isPlaying) return false
        val candidates = Grid.candidates(values)
        val changes = (0 until 81)
            .filter { values[it] == 0 && notes[it] != candidates[it] }
            .map { Change(it, 0, notes[it], 0, candidates[it]) }
        commit(changes)
        return changes.isNotEmpty()
    }

    fun clearAllNotes(): Boolean {
        if (!isPlaying) return false
        val changes = (0 until 81).filter { notes[it] != 0 }.map { Change(it, values[it], notes[it], values[it], 0) }
        commit(changes)
        return changes.isNotEmpty()
    }

    /**
     * Finds a hint for the current position: first any wrong digit (preferring [selected]), then
     * the easiest logical step, using the player's notes where they still allow the right digit.
     */
    fun findHint(logic: LogicSolver, selected: Int = -1): Hint? {
        if (!isPlaying) return null
        val wrong = (0 until 81).filter { isWrong(it) }
        if (wrong.isNotEmpty()) return Hint.Mistake(if (selected in wrong) selected else wrong.first())
        val state = hintState()
        logic.nextStep(state)?.let { return Hint.Logic(it) }
        val cell = (0 until 81).filter { values[it] == 0 }.minByOrNull { Grid.size(state.candidates[it]) }
            ?: return null
        return Hint.Reveal(cell, solution[cell])
    }

    fun countHint() {
        hintsUsed++
    }

    /** Carries out [hint] (it can then be undone like any other move). */
    fun applyHint(hint: Hint, autoRemoveNotes: Boolean) {
        if (!isPlaying) return
        when (hint) {
            is Hint.Mistake -> erase(hint.cell)
            is Hint.Reveal -> enter(hint.cell, hint.digit, autoRemoveNotes, trackMistakes = false)
            is Hint.Logic -> {
                val state = hintState()
                val newNotes = HashMap<Int, Int>()
                for (e in hint.step.eliminations) {
                    if (values[e.cell] != 0) continue
                    val base = newNotes[e.cell] ?: if (notes[e.cell] != 0) notes[e.cell] else state.candidates[e.cell]
                    newNotes[e.cell] = base and Grid.bit(e.digit).inv()
                }
                val changes = ArrayList<Change>()
                for ((cell, mask) in newNotes) changes += Change(cell, 0, notes[cell], 0, mask)
                commit(changes)
                for (p in hint.step.placements) enter(p.cell, p.digit, autoRemoveNotes, trackMistakes = false)
            }
        }
    }

    /** Fills in the right digit for [cell], as a hint. */
    fun reveal(cell: Int, autoRemoveNotes: Boolean): Boolean {
        if (!isPlaying || isGiven(cell) || values[cell] == solution[cell]) return false
        hintsUsed++
        enter(cell, solution[cell], autoRemoveNotes, trackMistakes = false)
        return true
    }

    /** Shows the whole solution and ends the game without a win. */
    fun revealAll() {
        if (!isPlaying) return
        solution.copyInto(values)
        notes.fill(0)
        history.clear()
        status = Status.REVEALED
    }

    fun markLost() {
        if (isPlaying) status = Status.LOST
    }

    /** Back to playing after a loss, with the mistake limit switched off for this game. */
    fun keepPlaying() {
        if (status != Status.LOST) return
        status = Status.PLAYING
        limitWaived = true
    }

    /** Starts the same puzzle over. */
    fun restart() {
        givens.copyInto(values)
        notes.fill(0)
        history.clear()
        mistakes = 0
        hintsUsed = 0
        elapsedMs = 0
        limitWaived = false
        status = Status.PLAYING
    }

    private fun clearNotesAround(cell: Int, digit: Int): List<Change> {
        val bit = Grid.bit(digit)
        return Grid.PEERS[cell]
            .filter { values[it] == 0 && notes[it] and bit != 0 }
            .map { Change(it, 0, notes[it], 0, notes[it] and bit.inv()) }
    }

    /** The candidates hints reason from: the player's notes where they still allow the right digit. */
    fun hintCandidates(): IntArray = hintState().candidates

    private fun hintState(): LogicState {
        val candidates = Grid.candidates(values)
        for (c in 0 until 81) {
            val n = notes[c]
            if (values[c] == 0 && n != 0 && Grid.has(n, solution[c])) candidates[c] = candidates[c] and n
        }
        return LogicState(values.copyOf(), candidates)
    }

    private fun commit(changes: List<Change>) {
        if (changes.isEmpty()) return
        for (change in changes) {
            values[change.cell] = change.newValue
            notes[change.cell] = change.newNotes
        }
        history += changes
        if (history.size > MAX_HISTORY) history.removeAt(0)
    }

    // ---- Saving -------------------------------------------------------------------------------

    fun serialize(): String = buildString {
        appendLine("version=1")
        appendLine("mode=${mode.name}")
        appendLine("difficulty=${difficulty.name}")
        appendLine("tag=$tag")
        appendLine("givens=${Grid.format(givens, '0')}")
        appendLine("solution=${Grid.format(solution, '0')}")
        appendLine("values=${Grid.format(values, '0')}")
        appendLine("notes=${notes.joinToString("") { it.toString(16).padStart(3, '0') }}")
        appendLine("mistakes=$mistakes")
        appendLine("hints=$hintsUsed")
        appendLine("elapsed=$elapsedMs")
        appendLine("status=${status.name}")
        appendLine("waived=$limitWaived")
        appendLine(
            "history=" + history.joinToString(";") { move ->
                move.joinToString(",") { "${it.cell}.${it.oldValue}.${it.oldNotes}.${it.newValue}.${it.newNotes}" }
            },
        )
    }

    companion object {
        private const val MAX_HISTORY = 500

        /** Restores a game written by [serialize], or returns null if the text is damaged. */
        fun deserialize(text: String): Game? = try {
            val fields = text.lineSequence()
                .filter { '=' in it }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
            val givens = Grid.parse(fields.getValue("givens"))!!
            val solution = Grid.parse(fields.getValue("solution"))!!
            val game = Game(
                GameMode.valueOf(fields.getValue("mode")),
                Difficulty.valueOf(fields.getValue("difficulty")),
                givens,
                solution,
                fields["tag"].orEmpty(),
            )
            Grid.parse(fields.getValue("values"))!!.copyInto(game.values)
            val notes = fields.getValue("notes")
            for (c in 0 until 81) game.notes[c] = notes.substring(c * 3, c * 3 + 3).toInt(16) and Grid.ALL_DIGITS
            game.mistakes = fields.getValue("mistakes").toInt()
            game.hintsUsed = fields.getValue("hints").toInt()
            game.elapsedMs = fields.getValue("elapsed").toLong()
            game.status = Status.valueOf(fields.getValue("status"))
            game.limitWaived = fields["waived"].toBoolean()
            fields["history"].orEmpty().split(';').filter { it.isNotEmpty() }.forEach { move ->
                game.history += move.split(',').map { change ->
                    val (cell, oldValue, oldNotes, newValue, newNotes) = change.split('.').map { it.toInt() }
                    Change(cell, oldValue, oldNotes, newValue, newNotes)
                }
            }
            for (c in 0 until 81) require(givens[c] == 0 || game.values[c] == givens[c])
            game
        } catch (e: RuntimeException) {
            null
        }
    }
}
