package com.ultimatesudoku.core

/**
 * A solving technique as the app teaches it: what it does in a sentence, why it works, how to find it,
 * and real positions to practise it on.
 */
class Strategy(
    val technique: Technique,
    val summary: String,
    val howItWorks: String,
    val howToSpot: String,
    private val positions: List<String>,
) {
    /** Where it applies, each with the step it finds there and a walkthrough. */
    val examples: List<Example> by lazy { positions.map { Example.parse(technique, it) } }

    /** Its place in the list, from 1. */
    val number: Int get() = technique.ordinal + 1

    /**
     * Whether its examples show pencil marks. Singles are found by scanning the digits on the board; every
     * other technique works on the candidates.
     */
    val showsCandidates: Boolean get() = technique.tier > 1
}

/** A position where [technique] makes progress: the step it finds, and a [lesson] that walks through it. */
class Example(val technique: Technique, val givens: IntArray, val values: IntArray) {
    /** What each empty cell could still be, judging by the digits placed: the pencil marks a player would write. */
    val candidates: IntArray = Grid.candidates(values)

    val step: Step = checkNotNull(LogicSolver().find(technique, LogicState(values.copyOf(), candidates.copyOf()))) {
        "${technique.title} finds nothing in ${Grid.format(values)}"
    }

    val lesson: Lesson by lazy { Lessons.of(this) }

    companion object {
        /** Reads 81 characters: '.' for an empty cell, '1'-'9' for a clue and 'a'-'i' for 1-9 filled in since. */
        fun parse(technique: Technique, text: String): Example {
            require(text.length == 81) { "An example needs 81 cells: $text" }
            val givens = IntArray(81)
            val values = IntArray(81)
            text.forEachIndexed { cell, ch ->
                when (ch) {
                    '.' -> Unit
                    in '1'..'9' -> {
                        givens[cell] = ch - '0'
                        values[cell] = ch - '0'
                    }
                    in 'a'..'i' -> values[cell] = ch - 'a' + 1
                    else -> throw IllegalArgumentException("Unexpected '$ch' in $text")
                }
            }
            return Example(technique, givens, values)
        }
    }
}

/** Every technique the solver knows, simplest first, as the strategies screens teach them. */
object Strategies {

    /** The names of the tiers, from 1. */
    val TIERS = listOf("Singles", "Basic", "Intermediate", "Advanced", "Expert")

    fun of(technique: Technique): Strategy = ALL[technique.ordinal]

    val ALL: List<Strategy> = listOf(
        Strategy(
            Technique.FULL_HOUSE,
            "A row, column or box with one empty cell left takes the one digit it's missing.",
            "Every row, column and box holds each digit from 1 to 9 exactly once. When eight cells of one are " +
                "filled, a single digit is missing, and there's only one place it can go: the last empty cell. " +
                "Nothing else on the board matters.",
            "Look for rows, columns and boxes that are nearly full. Full houses turn up more and more towards the " +
                "end of a puzzle, and filling one often completes another.",
            EXAMPLES.getValue(Technique.FULL_HOUSE),
        ),
        Strategy(
            Technique.HIDDEN_SINGLE,
            "When a digit fits in only one cell of a row, column or box, it goes there.",
            "Pick a digit and a box, say 7 in box 5. A 7 can't go in a cell whose row, column or box already has " +
                "a 7, so every 7 on the board blocks its whole row, column and box. If those blocks cover every empty " +
                "cell of box 5 but one, that cell must be the 7, even if, on its own, it could hold other digits too. " +
                "The single is hidden among the cell's other candidates. Rows and columns work the same way.",
            "Take one digit at a time, starting with those already placed most often, and sweep across the boxes. " +
                "For each box still missing it, trace the rows and columns of that digit's copies and see how many " +
                "cells are left. This cross-hatching solves most easy puzzles on its own.",
            EXAMPLES.getValue(Technique.HIDDEN_SINGLE),
        ),
        Strategy(
            Technique.NAKED_SINGLE,
            "When a cell has only one candidate left, that candidate is its digit.",
            "Look at a single cell and at everything its row, column and box already contain. Every digit there is " +
                "ruled out for the cell. If eight different digits are ruled out, the ninth is the only one left and " +
                "the cell must be it. A hidden single asks where a digit can go; a naked single asks what a cell can hold.",
            "Look for cells at busy crossings, whose row, column and box are all well filled. With pencil marks on, " +
                "naked singles are cells showing a single candidate, and they often appear right after a placement " +
                "takes a candidate away from the cells around it.",
            EXAMPLES.getValue(Technique.NAKED_SINGLE),
        ),
        Strategy(
            Technique.POINTING,
            "If a digit's spots in a box all lie on one row or column, it can't be anywhere else on that line.",
            "Every box needs each digit once. Suppose that inside box 1, the 3 can only go in cells on row 2. Then " +
                "box 1's 3 will be on row 2, wherever exactly it lands. Row 2 can't hold a second 3, so none of its " +
                "cells outside box 1 can be 3. The candidates in the box point along the line and clear it.",
            "Look inside each box at one digit's candidates. When two or three of them sit in a straight line and " +
                "there are none elsewhere in the box, follow that line out of the box and cross the digit off.",
            EXAMPLES.getValue(Technique.POINTING),
        ),
        Strategy(
            Technique.CLAIMING,
            "If a digit's spots in a row or column all lie in one box, it can't be anywhere else in that box.",
            "This is pointing turned around. Suppose that in row 9, the 1 can only go in cells inside box 7. Row 9 " +
                "needs a 1, so box 7's 1 must be one of those cells on row 9, and no other cell of box 7 can be 1. " +
                "The line claims the digit for its part of the box. It's also called box/line reduction.",
            "Scan a row or column for one digit's candidates. If they're all bunched inside one box, two or three " +
                "neighbouring cells, cross the digit off the other cells of that box.",
            EXAMPLES.getValue(Technique.CLAIMING),
        ),
        Strategy(
            Technique.NAKED_PAIR,
            "Two cells in a unit that can only hold the same two digits use them up: no other cell there can have them.",
            "Say R4C2 and R4C7 can each only be 3 or 8. One of them will be 3 and the other 8. We don't know which " +
                "way round, but either way, row 4's 3 and 8 are taken by these two cells, so every other cell in row 4 " +
                "loses 3 and 8. If the pair also shares a box, the rest of the box loses them too.",
            "With pencil marks on, look for two cells in the same row, column or box showing exactly the same two " +
                "candidates. Cells with only two candidates are worth keeping an eye on all through a puzzle.",
            EXAMPLES.getValue(Technique.NAKED_PAIR),
        ),
        Strategy(
            Technique.HIDDEN_PAIR,
            "When two digits can only go in the same two cells of a unit, those cells hold those digits and nothing else.",
            "Suppose that in column 6, the 2 can only be in R3C6 or R7C6, and so can the 6. Two digits need two " +
                "cells, and only these two can take them, so one is 2 and the other 6. Any other candidates in those " +
                "cells can go. The pair is hidden because, until you find it, the cells show other candidates too.",
            "Look at digits rather than cells: in a row, column or box, find two digits that each have only two " +
                "spots, the same two cells. Hidden pairs like to hide in units with many empty cells.",
            EXAMPLES.getValue(Technique.HIDDEN_PAIR),
        ),
        Strategy(
            Technique.NAKED_TRIPLE,
            "Three cells in a unit whose candidates, taken together, are only three digits use those digits up.",
            "It works like a naked pair with one more cell. If three cells of row 1 can only hold digits from 2, 5 " +
                "and 9, they'll take row 1's 2, 5 and 9 between them, and no other cell of row 1 can have those. Each " +
                "cell needn't show all three: candidates 2 5, 5 9 and 2 9 make a triple too.",
            "Look for three cells in a unit with two or three candidates each, all drawn from the same three " +
                "digits. It's easy to miss when no cell shows all three.",
            EXAMPLES.getValue(Technique.NAKED_TRIPLE),
        ),
        Strategy(
            Technique.HIDDEN_TRIPLE,
            "When three digits can only go in the same three cells of a unit, those cells hold those digits and nothing else.",
            "If, in box 4, the digits 1, 4 and 7 can only appear in three particular cells, those cells must take " +
                "1, 4 and 7 between them, and any other candidates in them go. As with the naked triple, a digit " +
                "needn't appear in all three cells, as long as it appears in none outside them.",
            "Count where each digit can go in a unit. Digits with only two or three spots are the ones to combine: " +
                "if three of them share the same three cells, you've found it.",
            EXAMPLES.getValue(Technique.HIDDEN_TRIPLE),
        ),
        Strategy(
            Technique.X_WING,
            "A digit with two spots in each of two rows, lined up in two columns, can't be anywhere else in those columns.",
            "Say 5 has only two spots in row 2 and two in row 7, and the four cells form a rectangle in columns 3 " +
                "and 8. Row 2's 5 is in column 3 or column 8, and row 7's 5 is in the other one. Either way, both " +
                "columns get their 5 from these two rows, so no other cell in columns 3 and 8 can be 5. It works " +
                "just as well with columns as the base and rows as the cover.",
            "Pick a digit and note the rows where it has only two spots. If two of those rows have their spots in " +
                "the same two columns, you have an X-Wing. Then try the columns.",
            EXAMPLES.getValue(Technique.X_WING),
        ),
        Strategy(
            Technique.SKYSCRAPER,
            "Two rows with two spots each for a digit, one pair lined up: one of the other two ends holds the digit.",
            "Suppose 4 has only two spots in row 2 and two in row 6, and one spot of each is in column 3. Those two " +
                "share a column, so they can't both be 4, which means at least one row takes its 4 at its other end. " +
                "Any cell that sees both of those ends can't be 4. It's an X-Wing with one corner out of line: two " +
                "towers of different heights.",
            "Look for two rows, or two columns, where a digit has exactly two spots and one pair lines up. Then " +
                "find the cells that see both of the other ends, the tops of the towers.",
            EXAMPLES.getValue(Technique.SKYSCRAPER),
        ),
        Strategy(
            Technique.TWO_STRING_KITE,
            "A row and a column with two spots each for a digit, linked inside a box: one of their far ends holds the digit.",
            "Suppose 6 has two spots in row 3 and two in column 7, and one spot of each sits in the same box, in " +
                "different cells. Those two can't both be 6, so either the row's other spot or the column's other " +
                "spot is 6, and a cell that sees both far ends can't be 6.",
            "Find a digit with just two spots in some row and in some column. If one spot of each lands in a shared " +
                "box, look where the row of one far end crosses the column of the other.",
            EXAMPLES.getValue(Technique.TWO_STRING_KITE),
        ),
        Strategy(
            Technique.NAKED_QUAD,
            "Four cells in a unit whose candidates add up to only four digits use those digits up.",
            "The same idea as naked pairs and triples, with four cells and four digits. If four cells of a column " +
                "can only hold 1, 3, 6 and 8 between them, they take all four, so no other cell in the column can.",
            "Quads matter in units with many empty cells. Look for four cells with short candidate lists that never " +
                "go beyond the same four digits. The other empty cells of the unit often form a hidden subset, which " +
                "can be easier to see.",
            EXAMPLES.getValue(Technique.NAKED_QUAD),
        ),
        Strategy(
            Technique.HIDDEN_QUAD,
            "When four digits can only go in the same four cells of a unit, those cells hold those digits and nothing else.",
            "If, in a row, the digits 2, 4, 5 and 9 can only appear in four particular cells, those cells must take " +
                "those four digits between them, and every other candidate in them goes.",
            "Rare and hard to see. In a unit with lots of empty cells, list where each digit can go: four digits " +
                "confined to the same four cells make the quad.",
            EXAMPLES.getValue(Technique.HIDDEN_QUAD),
        ),
        Strategy(
            Technique.XY_WING,
            "A two-candidate pivot and two wings that each share a digit with it: one wing holds their third digit.",
            "Take a pivot cell that can only be X or Y. It sees one wing that can be X or Z, and another that can " +
                "be Y or Z. If the pivot is X, the first wing must be Z; if it's Y, the second wing must be Z. Either " +
                "way one wing is Z, so any cell that sees both wings can't be Z.",
            "Look at cells with exactly two candidates. For a pivot X Y, search the cells it sees for X Z and Y Z " +
                "with the same Z. Then cross Z off the cells that see both wings.",
            EXAMPLES.getValue(Technique.XY_WING),
        ),
        Strategy(
            Technique.XYZ_WING,
            "An XY-Wing whose pivot can also be the third digit: one of the three cells holds it.",
            "The pivot can be X, Y or Z, and it sees two wings: one is X or Z, the other Y or Z. If the pivot is X, " +
                "the first wing is Z; if it's Y, the second wing is Z; and if it's Z, the pivot is. So one of the three " +
                "is Z, and any cell that sees all three can't be Z.",
            "Start from a cell with three candidates and look for two two-candidate cells it sees that split them " +
                "up. A cell has to see all three to lose the digit, so it's usually in the pivot's box.",
            EXAMPLES.getValue(Technique.XYZ_WING),
        ),
        Strategy(
            Technique.W_WING,
            "Two cells with the same two candidates, tied by a line where one digit has two spots: the other digit goes from cells that see both.",
            "Say R1C2 and R7C8 can each only be 4 or 6, and somewhere a row, column or box has just two spots for " +
                "4, one seen by each cell. That unit needs a 4, and whichever spot takes it stops the cell that sees " +
                "it from being 4. So the two cells can't both be 4: at least one is 6, and a cell that sees both " +
                "can't be 6.",
            "Find two cells with the same two candidates that don't see each other. For one of their digits, look " +
                "for a unit with exactly two spots for it, each spot seen by one of the cells.",
            EXAMPLES.getValue(Technique.W_WING),
        ),
        Strategy(
            Technique.SWORDFISH,
            "An X-Wing across three rows and three columns.",
            "Suppose that in rows 1, 4 and 8, the 9 only appears in columns 2, 5 and 7, two or three spots in each " +
                "row. Each of those rows puts its 9 in one of the three columns, and no two rows can use the same " +
                "column. So the three columns get their 9s from these rows, and 9 can go from the rest of those columns.",
            "Pick a digit and list the rows where it has only two or three spots. If three of them use only three " +
                "columns between them, you have a swordfish. As with X-Wings, try the columns too.",
            EXAMPLES.getValue(Technique.SWORDFISH),
        ),
        Strategy(
            Technique.SIMPLE_COLORING,
            "Follow a digit's two-spot links in two colours: one colour is right. A clash, or a cell seeing both colours, removes candidates.",
            "When a unit has only two spots for a digit, exactly one of them is that digit: they're linked. Links " +
                "chain across the board. Colour the chain in two alternating colours: either every cell of one colour " +
                "holds the digit, or every cell of the other. If two cells of the same colour see each other, that " +
                "colour can't be right, so the digit goes from all its cells. And any other cell that sees both " +
                "colours loses the digit, whichever colour is right.",
            "Pick a digit that has many units with only two spots. Start at one and alternate colours as you follow " +
                "the links. Then look for same-coloured cells in one unit, or uncoloured cells that see both colours.",
            EXAMPLES.getValue(Technique.SIMPLE_COLORING),
        ),
        Strategy(
            Technique.UNIQUE_RECTANGLE,
            "Four cells over two boxes that could swap two digits would mean two solutions, so the pattern must be broken.",
            "Picture four cells at the corners of a rectangle covering two rows, two columns and two boxes. If all " +
                "four could only be 3 or 9, the 3s and 9s could swap places and the rest of the grid would still work, " +
                "so the puzzle would have two solutions. A proper puzzle has exactly one, so this deadly pattern can't " +
                "happen. If three corners can only be 3 or 9, the fourth must be something else: cross 3 and 9 off it.",
            "Look for two cells with the same pair of candidates in one row, and the same pair again in matching " +
                "places in another, the four cells spanning exactly two boxes. If one corner has extra candidates, " +
                "the pair goes from it. This relies on the puzzle having a single solution.",
            EXAMPLES.getValue(Technique.UNIQUE_RECTANGLE),
        ),
        Strategy(
            Technique.BUG_PLUS_ONE,
            "If every empty cell but one has two candidates, the odd cell's extra candidate is its digit.",
            "In a Bivalue Universal Grave, a BUG, every empty cell has two candidates and every candidate appears " +
                "exactly twice in each row, column and box. Such a position always has at least two solutions, so a " +
                "proper puzzle never reaches one. When every empty cell but one has two candidates, and that one has " +
                "three, its extra candidate is all that stops the BUG, so it must be the cell's digit: the one that " +
                "appears three times in the cell's row, column and box.",
            "Late in a hard puzzle, notice when nearly every empty cell shows just two candidates. Find the cell " +
                "with three, and its digit is the one that appears three times in its row, column and box.",
            EXAMPLES.getValue(Technique.BUG_PLUS_ONE),
        ),
        Strategy(
            Technique.JELLYFISH,
            "A Swordfish across four rows and four columns.",
            "If, in four rows, a digit only appears in the same four columns, each row takes its digit in a " +
                "different one of those columns, so the four columns get their digit from these rows, and it can go " +
                "from the rest of them. X-Wing, Swordfish and Jellyfish make up the fish family: two, three and four " +
                "lines.",
            "Very rare. Look for a digit that has only a few spots in many rows. If four of those rows use just " +
                "four columns between them, it's a jellyfish.",
            EXAMPLES.getValue(Technique.JELLYFISH),
        ),
        Strategy(
            Technique.XY_CHAIN,
            "A chain of two-candidate cells, each forcing the next: the first or the last cell holds the digit.",
            "Start at a cell that is X or Y. If it isn't X, it's Y. A cell it sees that is Y or Z must then be Z, " +
                "the next one along, Z or W, must be W, and so on down the chain. If the chain ends at a cell that " +
                "would then have to be X, either the start or the end is X, and any cell that sees both can't be X. " +
                "An XY-Wing is the shortest XY-Chain.",
            "Trace paths through two-candidate cells where each cell shares a digit with the one before. Look for " +
                "chains that start and end on the same digit, then check the cells that see both ends.",
            EXAMPLES.getValue(Technique.XY_CHAIN),
        ),
    )
}
