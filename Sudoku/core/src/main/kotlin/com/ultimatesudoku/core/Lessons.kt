package com.ultimatesudoku.core

import com.ultimatesudoku.core.Grid.UNITS
import com.ultimatesudoku.core.Grid.bit
import com.ultimatesudoku.core.Grid.cellList
import com.ultimatesudoku.core.Grid.cellName
import com.ultimatesudoku.core.Grid.digitList
import com.ultimatesudoku.core.Grid.joinWords
import com.ultimatesudoku.core.Grid.sees
import com.ultimatesudoku.core.Grid.unitName

/** One frame of a walkthrough: [text] to read and, on the board, [overlay], drawing [candidates] in its cells. */
class Frame(val text: String, val overlay: Step, val candidates: IntArray)

/**
 * A worked example of one [step]: first a [challenge] that says where to look and what to find, answered
 * by tapping one of the [answers], then [frames] that explain the step, the last one showing its result.
 */
class Lesson(val challenge: Frame, val answers: Set<Int>, val frames: List<Frame>, val step: Step) {

    /** What the step does to [cell], to say once the player has found it. */
    fun outcome(cell: Int): String {
        step.placements.firstOrNull { it.cell == cell }?.let { return "${cellName(cell)} is ${it.digit}." }
        val gone = step.eliminations.filter { it.cell == cell }.fold(0) { mask, c -> mask or bit(c.digit) }
        if (gone == 0) return "${cellName(cell)} is part of it."
        return "${cellName(cell)} can't be ${alternatives(Grid.digits(gone).toList())}."
    }
}

/** "3", "3 or 7", "1, 4 or 9". */
internal fun alternatives(digits: List<Int>): String = joinWords(digits.map { it.toString() }).replace(" and ", " or ")

/** Builds the walkthrough of an [Example]'s step, technique by technique. */
internal class Lessons private constructor(private val example: Example) {
    private val technique = example.technique
    private val step = example.step
    private val values = example.values
    private val candidates = example.candidates
    private val gone = step.eliminations.map { it.cell }.distinct()

    /** The first digit the step is about. */
    private val digit = (step.placements + step.highlights + step.eliminations).first().digit

    private fun frame(
        text: String,
        cells: List<Int> = emptyList(),
        units: List<Int> = emptyList(),
        highlights: List<Candidate> = emptyList(),
        alt: List<Candidate> = emptyList(),
        hidden: Collection<Int> = emptyList(),
    ): Frame {
        val shown = candidates.copyOf()
        for (c in hidden) shown[c] = 0
        return Frame(text, Step(technique, text, cells = cells, units = units, highlights = highlights, altHighlights = alt), shown)
    }

    /** The step itself, with its result: digits placed or candidates crossed out. */
    private fun result(text: String) = Frame(text, step, candidates.copyOf())

    private fun lesson(challenge: Frame, frames: List<Frame>, answers: Collection<Int>? = null) = Lesson(
        challenge,
        (answers ?: (step.placements.map { it.cell } + gone)).toSet(),
        frames,
        step,
    )

    /** "remove 3 and 8 from R4C1 and R4C5", or with different digits per cell, "remove 3 from R4C1, and 8 from R4C5". */
    private fun removals(): String {
        val groups = LinkedHashMap<Int, MutableList<Int>>()
        for (cell in gone) {
            val mask = step.eliminations.filter { it.cell == cell }.fold(0) { m, c -> m or bit(c.digit) }
            groups.getOrPut(mask) { ArrayList() } += cell
        }
        val parts = groups.map { (mask, cells) -> "${digitList(mask)} from ${cellList(cells)}" }
        return "remove " + if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + ", and " + parts.last()
    }

    private fun marks(cells: List<Int>, d: Int) = cells.map { Candidate(it, d) }

    /** "2 or 4", smaller first. */
    private fun pair(a: Int, b: Int) = "${minOf(a, b)} or ${maxOf(a, b)}"

    private fun all(cells: List<Int>) = cells.flatMap { c -> Grid.digits(candidates[c]).map { Candidate(c, it) } }

    private fun build(): Lesson = when (technique) {
        Technique.FULL_HOUSE -> fullHouse()
        Technique.HIDDEN_SINGLE -> hiddenSingle()
        Technique.NAKED_SINGLE -> nakedSingle()
        Technique.POINTING -> pointing()
        Technique.CLAIMING -> claiming()
        Technique.NAKED_PAIR -> nakedSubset(2)
        Technique.NAKED_TRIPLE -> nakedSubset(3)
        Technique.NAKED_QUAD -> nakedSubset(4)
        Technique.HIDDEN_PAIR -> hiddenSubset(2)
        Technique.HIDDEN_TRIPLE -> hiddenSubset(3)
        Technique.HIDDEN_QUAD -> hiddenSubset(4)
        Technique.X_WING -> fish(2)
        Technique.SWORDFISH -> fish(3)
        Technique.JELLYFISH -> fish(4)
        Technique.SKYSCRAPER -> skyscraper()
        Technique.TWO_STRING_KITE -> twoStringKite()
        Technique.XY_WING -> xyWing()
        Technique.XYZ_WING -> xyzWing()
        Technique.W_WING -> wWing()
        Technique.SIMPLE_COLORING -> simpleColoring()
        Technique.UNIQUE_RECTANGLE -> uniqueRectangle()
        Technique.BUG_PLUS_ONE -> bugPlusOne()
        Technique.XY_CHAIN -> xyChain()
    }

    // ---- Singles ------------------------------------------------------------------------------

    private fun fullHouse(): Lesson {
        val (c, d) = step.placements[0]
        val u = step.units[0]
        val present = Grid.ALL_DIGITS and bit(d).inv()
        return lesson(
            frame("Find a row, column or box with only one empty cell left, and tap that cell."),
            listOf(
                frame("${unitName(u).cap()} has just one empty cell: ${cellName(c)}.", listOf(c), listOf(u), hidden = listOf(c)),
                frame(
                    "Every row, column and box holds each digit from 1 to 9 exactly once. ${unitName(u).cap()} " +
                        "already has ${digitList(present)}, so only $d is missing.",
                    listOf(c), listOf(u), hidden = listOf(c),
                ),
                result("So ${cellName(c)} must be $d."),
            ),
        )
    }

    private fun hiddenSingle(): Lesson {
        val (c, d) = step.placements[0]
        val u = step.units[0]
        val empty = UNITS[u].filter { values[it] == 0 }
        val (blockers, lines) = blockers(d, empty - c)
        val blocking = if (blockers.size == 1) {
            "The $d at ${cellName(blockers[0])} rules"
        } else {
            "The ${d}s at ${cellList(blockers)} rule"
        }
        return lesson(
            frame("${unitName(u).cap()} still needs a $d, and only one of its empty cells can take it. Tap that cell.", units = listOf(u)),
            listOf(
                frame("Where can the $d of ${unitName(u)} go? It has ${COUNT[empty.size]} empty cells.", empty, listOf(u), hidden = empty),
                frame(
                    "A $d can't share a row, column or box with another $d. $blocking out every empty cell of " +
                        "${unitName(u)} except ${cellName(c)}.",
                    blockers + c, listOf(u) + lines, hidden = listOf(c),
                ),
                result(
                    "So ${cellName(c)} is $d. It's a hidden single: on its own, ${cellName(c)} could be " +
                        "${alternatives(Grid.digits(candidates[c]).toList())}, but it's the only place left for the $d.",
                ),
            ),
        )
    }

    /** Placed [d]s that, between them, see every cell of [cells]; and for each, the row, column or box it sees them through. */
    private fun blockers(d: Int, cells: List<Int>): Pair<List<Int>, List<Int>> {
        val sources = (0 until 81).filter { values[it] == d }
        val open = cells.toMutableSet()
        val picked = ArrayList<Int>()
        val lines = ArrayList<Int>()
        while (open.isNotEmpty()) {
            val best = sources.maxBy { s -> open.count { sees(it, s) } }
            val covered = open.filter { sees(it, best) }
            check(covered.isNotEmpty()) { "Nothing blocks ${cellList(open)}" }
            picked += best
            lines += when {
                covered.all { Grid.row(it) == Grid.row(best) } -> Grid.row(best)
                covered.all { Grid.col(it) == Grid.col(best) } -> 9 + Grid.col(best)
                else -> 18 + Grid.box(best)
            }
            open -= covered.toSet()
        }
        return picked to lines
    }

    private fun nakedSingle(): Lesson {
        val (c, d) = step.placements[0]
        val box = 18 + Grid.box(c)
        return lesson(
            frame("One empty cell in ${unitName(box)} can only be one digit. Tap it.", units = listOf(box)),
            listOf(
                frame("Look at ${cellName(c)}. Which digits could go here?", listOf(c), hidden = listOf(c)),
                frame(
                    "Its row, column and box already hold ${digitList(Grid.ALL_DIGITS and bit(d).inv())}: every digit except $d.",
                    listOf(c), Grid.UNITS_OF[c].toList(), hidden = listOf(c),
                ),
                result("So ${cellName(c)} can only be $d. It's a naked single: every other digit is ruled out, and one candidate is left standing."),
            ),
        )
    }

    // ---- Locked candidates --------------------------------------------------------------------

    private fun pointing(): Lesson {
        val (box, line) = step.units
        val spots = step.cells
        val marks = marks(spots, digit)
        return lesson(
            frame(
                "In ${unitName(box)}, every $digit sits on one line. Tap a cell outside the box that can't be $digit.",
                spots, listOf(box), marks,
            ),
            listOf(
                frame("In ${unitName(box)}, $digit can only go in ${cellList(spots)}.", spots, listOf(box), marks),
                frame(
                    "They're all in ${unitName(line)}. Whichever one is the $digit, the $digit of ${unitName(line)} " +
                        "is inside ${unitName(box)}.",
                    spots, listOf(box, line), marks,
                ),
                result("${unitName(line).cap()} can't have a second $digit, so remove it from ${cellList(gone)}."),
            ),
        )
    }

    private fun claiming(): Lesson {
        val (line, box) = step.units
        val spots = step.cells
        val marks = marks(spots, digit)
        return lesson(
            frame(
                "In ${unitName(line)}, every $digit falls inside one box. Tap a cell of that box that can't be $digit.",
                spots, listOf(line), marks,
            ),
            listOf(
                frame("In ${unitName(line)}, $digit can only go in ${cellList(spots)}.", spots, listOf(line), marks),
                frame(
                    "They're all in ${unitName(box)}. So the $digit of ${unitName(box)} must be one of them, on ${unitName(line)}.",
                    spots, listOf(line, box), marks,
                ),
                result("No other cell of ${unitName(box)} can be $digit: remove it from ${cellList(gone)}."),
            ),
        )
    }

    // ---- Subsets ------------------------------------------------------------------------------

    private fun nakedSubset(k: Int): Lesson {
        val u = step.units[0]
        val cells = step.cells
        val union = cells.fold(0) { m, c -> m or candidates[c] }
        val digits = Grid.digits(union).toList()
        val why = if (k == 2) {
            "One of them is ${digits[0]} and the other ${digits[1]}. We don't know which way round, but either way " +
                "they use up the ${digits[0]} and the ${digits[1]} of ${unitName(u)}."
        } else {
            "${COUNT[k].cap()} cells, ${COUNT[k]} digits: each of ${digitList(union)} has to go in one of these cells, " +
                "so these cells use up all ${COUNT[k]}. A cell needn't show every one of them."
        }
        return lesson(
            frame(
                "${unitName(u).cap()} has a naked ${SET[k]}: ${COUNT[k]} cells that can only hold the same ${COUNT[k]} " +
                    "digits between them. Tap a cell that loses a candidate because of it.",
                units = listOf(u),
            ),
            listOf(
                frame(
                    "In ${unitName(u)}, look at ${cellList(cells)}. Between them, they can only hold ${digitList(union)}.",
                    cells, listOf(u), step.highlights,
                ),
                frame(why, cells, listOf(u), step.highlights),
                result("No other cell in ${unitName(u)} can be ${alternatives(digits)}: ${removals()}."),
            ),
        )
    }

    private fun hiddenSubset(k: Int): Lesson {
        val u = step.units[0]
        val cells = step.cells
        val digits = step.highlights.fold(0) { m, c -> m or bit(c.digit) }
        return lesson(
            frame(
                "${unitName(u).cap()} has a hidden ${SET[k]}: ${COUNT[k]} digits that fit in only the same ${COUNT[k]} " +
                    "cells. Tap one of those cells.",
                units = listOf(u),
            ),
            listOf(
                frame(
                    "In ${unitName(u)}, where can ${digitList(digits)} go? Only in ${cellList(cells)}.",
                    cells, listOf(u), step.highlights,
                ),
                frame(
                    "${COUNT[k].cap()} digits need ${COUNT[k]} cells, and only these can take them. So ${cellList(cells)} " +
                        "hold ${digitList(digits)} between them, and nothing else.",
                    cells, listOf(u), step.highlights,
                ),
                result("Every other candidate in them goes: ${removals()}."),
            ),
            answers = cells,
        )
    }

    // ---- Fish ---------------------------------------------------------------------------------

    private fun fish(k: Int): Lesson {
        val base = step.units.take(k)
        val cover = step.units.drop(k)
        val spots = step.cells
        val marks = step.highlights
        val counts = base.map { b -> spots.count { it in UNITS[b] } }
        val each = when (counts.max() - counts.min()) {
            0 -> COUNT[counts.min()]
            1 -> "${COUNT[counts.min()]} or ${COUNT[counts.max()]}"
            else -> "${COUNT[counts.min()]} to ${COUNT[counts.max()]}"
        }
        val baseKind = if (base[0] < 9) "row" else "column"
        val coverKind = if (cover[0] < 9) "row" else "column"
        val why = if (k == 2) {
            "They line up in ${lines(cover)}. ${unitName(base[0]).cap()} takes its $digit in one of these ${coverKind}s " +
                "and ${unitName(base[1])} in the other, so both ${coverKind}s get their $digit from these two ${baseKind}s."
        } else {
            "Between them they use only ${lines(cover)}. Each of the ${COUNT[k]} ${baseKind}s takes its $digit in a " +
                "different one of those ${coverKind}s, so all ${COUNT[k]} ${coverKind}s get their $digit from these ${baseKind}s."
        }
        return lesson(
            frame("Look at the ${digit}s in ${lines(base)}. Tap a cell where $digit can be crossed out.", spots, base, marks),
            listOf(
                frame("In ${lines(base)}, $digit has just $each spots each.", spots, base, marks),
                frame(why, spots, base + cover, marks),
                result("So no other cell in ${lines(cover)} can be $digit: remove it from ${cellList(gone)}."),
            ),
        )
    }

    /** "rows 1 and 4", "columns 2, 5 and 7". */
    private fun lines(units: List<Int>): String {
        val kind = if (units[0] < 9) "rows" else "columns"
        return "$kind ${joinWords(units.map { (if (it < 9) it + 1 else it - 8).toString() })}"
    }

    // ---- Single-digit patterns ----------------------------------------------------------------

    private fun skyscraper(): Lesson {
        val (l1, l2, shared) = step.units
        val (base1, end1, base2, end2) = step.cells
        val marks = step.highlights
        return lesson(
            frame(
                "${lines(listOf(l1, l2)).cap()} each have only two spots for $digit. Tap a cell that can't be $digit.",
                step.cells, listOf(l1, l2), marks,
            ),
            listOf(
                frame(
                    "${unitName(l1).cap()} has just two spots for $digit, ${cellName(base1)} and ${cellName(end1)}, and so " +
                        "has ${unitName(l2)}: ${cellName(base2)} and ${cellName(end2)}.",
                    step.cells, listOf(l1, l2), marks,
                ),
                frame(
                    "${cellName(base1)} and ${cellName(base2)} are both in ${unitName(shared)}, so they can't both be $digit. " +
                        "That leaves ${cellName(end1)} or ${cellName(end2)} to be $digit, maybe both.",
                    step.cells, listOf(l1, l2, shared), marks(listOf(base1, base2), digit), marks(listOf(end1, end2), digit),
                ),
                result(
                    "A cell that sees both ${cellName(end1)} and ${cellName(end2)} can't be $digit: remove it from ${cellList(gone)}.",
                ),
            ),
        )
    }

    private fun twoStringKite(): Lesson {
        val (rowEnd, rowLink, colLink, colEnd) = step.cells
        val (row, col, box) = step.units
        val marks = step.highlights
        return lesson(
            frame(
                "${unitName(row).cap()} and ${unitName(col)} each have only two spots for $digit. Tap a cell that can't be $digit.",
                step.cells, listOf(row, col), marks,
            ),
            listOf(
                frame(
                    "${unitName(row).cap()} has two spots for $digit, ${cellName(rowLink)} and ${cellName(rowEnd)}, and so has " +
                        "${unitName(col)}: ${cellName(colLink)} and ${cellName(colEnd)}.",
                    step.cells, listOf(row, col), marks,
                ),
                frame(
                    "${cellName(rowLink)} and ${cellName(colLink)} are both in ${unitName(box)}, so they can't both be $digit. " +
                        "So ${cellName(rowEnd)} or ${cellName(colEnd)} must be $digit.",
                    step.cells, listOf(row, col, box), marks(listOf(rowLink, colLink), digit), marks(listOf(rowEnd, colEnd), digit),
                ),
                result(
                    "A cell that sees both ${cellName(rowEnd)} and ${cellName(colEnd)} can't be $digit: remove it from ${cellList(gone)}.",
                ),
            ),
        )
    }

    // ---- Wings --------------------------------------------------------------------------------

    private fun xyWing(): Lesson {
        val (pivot, w1, w2) = step.cells
        val x = step.highlights[0].digit
        val y = step.highlights[1].digit
        val z = step.altHighlights[0].digit
        return lesson(
            frame(
                "${cellName(pivot)} and two cells it sees, ${cellName(w1)} and ${cellName(w2)}, form an XY-Wing. " +
                    "Tap a cell that loses a candidate.",
                step.cells,
            ),
            listOf(
                frame("${cellName(pivot)}, the pivot, can only be ${pair(x, y)}.", listOf(pivot), highlights = all(listOf(pivot))),
                frame(
                    "It sees ${cellName(w1)}, which is ${pair(x, z)}, and ${cellName(w2)}, which is ${pair(y, z)}. If the pivot is $x, " +
                        "${cellName(w1)} must be $z; if it's $y, ${cellName(w2)} must be $z.",
                    step.cells, highlights = step.highlights, alt = step.altHighlights,
                ),
                result("Either way one of the wings is $z, so a cell that sees both wings can't be $z: remove it from ${cellList(gone)}."),
            ),
        )
    }

    private fun xyzWing(): Lesson {
        val (pivot, w1, w2) = step.cells
        val z = step.altHighlights[0].digit
        val a = Grid.first(candidates[w1] and bit(z).inv())
        val b = Grid.first(candidates[w2] and bit(z).inv())
        return lesson(
            frame(
                "${cellName(pivot)} and two cells it sees, ${cellName(w1)} and ${cellName(w2)}, form an XYZ-Wing. " +
                    "Tap a cell that loses a candidate.",
                step.cells,
            ),
            listOf(
                frame(
                    "${cellName(pivot)}, the pivot, can be ${alternatives(Grid.digits(candidates[pivot]).toList())}.",
                    listOf(pivot), highlights = all(listOf(pivot)),
                ),
                frame(
                    "It sees ${cellName(w1)}, which is ${pair(a, z)}, and ${cellName(w2)}, which is ${pair(b, z)}. If the pivot is $a, " +
                        "${cellName(w1)} must be $z; if it's $b, ${cellName(w2)} must be $z; otherwise the pivot is $z itself.",
                    step.cells, highlights = step.highlights, alt = step.altHighlights,
                ),
                result("One of the three is always $z, so a cell that sees all three can't be $z: remove it from ${cellList(gone)}."),
            ),
        )
    }

    private fun wWing(): Lesson {
        val (c1, p, q, c2) = step.cells
        val u = step.units[0]
        val link = step.highlights[0].digit
        val other = step.altHighlights[0].digit
        // Which end of the link sees which cell.
        val (near1, near2) = if (sees(p, c1) && sees(q, c2)) p to q else q to p
        return lesson(
            frame(
                "${cellName(c1)} and ${cellName(c2)} can each only be ${pair(link, other)}. Tap a cell that loses a candidate.",
                listOf(c1, c2), highlights = all(listOf(c1, c2)),
            ),
            listOf(
                frame(
                    "${cellName(c1)} and ${cellName(c2)} can each only be ${pair(link, other)}. They don't see each other, so " +
                        "they could be the same digit.",
                    listOf(c1, c2), highlights = all(listOf(c1, c2)),
                ),
                frame(
                    "In ${unitName(u)}, $link has only two spots: ${cellName(near1)}, which sees ${cellName(c1)}, and " +
                        "${cellName(near2)}, which sees ${cellName(c2)}. One of them is $link, so ${cellName(c1)} and " +
                        "${cellName(c2)} can't both be $link: at least one of them is $other.",
                    step.cells, listOf(u), step.highlights, step.altHighlights,
                ),
                result("So a cell that sees both ${cellName(c1)} and ${cellName(c2)} can't be $other: remove it from ${cellList(gone)}."),
            ),
        )
    }

    // ---- Coloring and chains ------------------------------------------------------------------

    private fun simpleColoring(): Lesson {
        val chain = step.cells
        val linked = marks(chain, digit)
        val clash = gone.all { it in chain }
        val outcome = if (clash) {
            val (a, b) = gone.firstNotNullOf { a -> gone.firstOrNull { sees(a, it) }?.let { a to it } }
            "${cellName(a)} and ${cellName(b)} have the same colour and see each other, so that colour can't be the true " +
                "one: remove $digit from all its cells, ${cellList(gone)}."
        } else if (gone.size == 1) {
            "${cellName(gone[0])} sees both colours, so it can't be $digit whichever colour is true."
        } else {
            "${cellList(gone)} see both colours, so they can't be $digit whichever colour is true."
        }
        return lesson(
            frame("Follow the ${digit}s that come in pairs. Tap a cell that can't be $digit.", chain, highlights = linked),
            listOf(
                frame(
                    "Each of these ${digit}s is one of only two spots for $digit in some row, column or box, and they link " +
                        "up into a chain.",
                    chain, highlights = linked,
                ),
                frame(
                    "In each linked pair exactly one is $digit. Colour the chain alternately: either every shaded $digit is " +
                        "right, or every outlined one.",
                    chain, highlights = step.highlights, alt = step.altHighlights,
                ),
                result(outcome),
            ),
        )
    }

    private fun xyChain(): Lesson {
        val chain = step.cells
        val start = chain.first()
        val end = chain.last()
        val x = step.altHighlights[0].digit
        val links = step.highlights.joinToString(", then ") { "${cellName(it.cell)} is ${it.digit}" }
        return lesson(
            frame("These cells each have two candidates, and each sees the next. Tap a cell that loses a candidate.", chain),
            listOf(
                frame("Each of ${cellList(chain)} has just two candidates, and each sees the next one along.", chain),
                frame(
                    "Suppose ${cellName(start)} isn't $x. Then $links: each forces the next. So if ${cellName(start)} " +
                        "isn't $x, ${cellName(end)} is.",
                    chain, highlights = step.highlights, alt = step.altHighlights,
                ),
                result("Either way one end is $x, so a cell that sees both ends can't be $x: remove it from ${cellList(gone)}."),
            ),
        )
    }

    // ---- Uniqueness ---------------------------------------------------------------------------

    private fun uniqueRectangle(): Lesson {
        val corners = step.cells
        val target = step.eliminations[0].cell
        val others = corners - target
        val (a, b) = step.eliminations.map { it.digit }
        val left = Grid.digits(candidates[target] and bit(a).inv() and bit(b).inv()).toList()
        return lesson(
            frame("These four cells span two rows, two columns and two boxes. Tap the one that loses candidates.", corners),
            listOf(
                frame("${cellList(others)} can only be $a or $b.", others, highlights = step.highlights),
                frame(
                    "If ${cellName(target)} could only be $a or $b as well, the corners could swap their ${a}s and ${b}s " +
                        "and the rest of the grid would still work, giving two solutions.",
                    corners, highlights = step.highlights,
                ),
                result(
                    "A proper puzzle has exactly one solution, so ${cellName(target)} can't be $a or $b: that leaves ${alternatives(left)}.",
                ),
            ),
        )
    }

    private fun bugPlusOne(): Lesson {
        val (odd, d) = step.placements[0]
        return lesson(
            frame("Every empty cell has two candidates except one. Tap the odd one out: you can fill it."),
            listOf(
                frame(
                    "Every empty cell has exactly two candidates, except ${cellName(odd)}, which has three: " +
                        "${digitList(candidates[odd])}.",
                    listOf(odd), highlights = all(listOf(odd)),
                ),
                frame(
                    "Take away its extra candidate and every candidate would appear exactly twice in each row, column " +
                        "and box: a deadly grid, which always has two solutions. So the extra candidate must be right: $d, " +
                        "the one that appears three times in ${cellName(odd)}'s row, column and box.",
                    listOf(odd), Grid.UNITS_OF[odd].toList(), listOf(Candidate(odd, d)),
                ),
                result("So ${cellName(odd)} is $d."),
            ),
        )
    }

    companion object {
        private val COUNT = listOf("no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
        private val SET = listOf("", "single", "pair", "triple", "quad")

        fun of(example: Example): Lesson = Lessons(example).build()

        private fun String.cap() = replaceFirstChar { it.uppercaseChar() }
    }
}
