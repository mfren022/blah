package com.ultimatesudoku.core

/**
 * Geometry of the 9x9 board.
 *
 * Cells are numbered 0..80 row by row. Digits are 1..9 and 0 marks an empty cell. Sets of digits
 * (candidates, notes) are 9-bit masks where bit `d - 1` stands for digit `d`.
 */
object Grid {
    const val CELLS = 81
    const val ALL_DIGITS = 0x1FF

    fun row(cell: Int) = cell / 9
    fun col(cell: Int) = cell % 9
    fun box(cell: Int) = cell / 27 * 3 + cell % 9 / 3
    fun cell(row: Int, col: Int) = row * 9 + col

    /** The 27 units: 0..8 are rows, 9..17 columns and 18..26 boxes, each listing its 9 cells. */
    val UNITS: Array<IntArray> = Array(27) { unit ->
        IntArray(9) { i ->
            when {
                unit < 9 -> cell(unit, i)
                unit < 18 -> cell(i, unit - 9)
                else -> {
                    val box = unit - 18
                    cell(box / 3 * 3 + i / 3, box % 3 * 3 + i % 3)
                }
            }
        }
    }

    /** For each cell: the indices of its row, column and box units. */
    val UNITS_OF: Array<IntArray> = Array(CELLS) { intArrayOf(row(it), 9 + col(it), 18 + box(it)) }

    /** For each cell: the 20 other cells that share a unit with it. */
    val PEERS: Array<IntArray> = Array(CELLS) { c ->
        (0 until CELLS).filter { it != c && sees(it, c) }.toIntArray()
    }

    /** Whether two different cells share a row, column or box. */
    fun sees(a: Int, b: Int) = a != b && (row(a) == row(b) || col(a) == col(b) || box(a) == box(b))

    fun bit(digit: Int) = 1 shl (digit - 1)
    fun has(mask: Int, digit: Int) = mask and (1 shl (digit - 1)) != 0
    fun size(mask: Int) = mask.countOneBits()

    /** The smallest digit in a non-empty mask. */
    fun first(mask: Int) = mask.countTrailingZeroBits() + 1

    fun digits(mask: Int): IntArray {
        val out = IntArray(mask.countOneBits())
        var m = mask
        var i = 0
        while (m != 0) {
            out[i++] = m.countTrailingZeroBits() + 1
            m = m and (m - 1)
        }
        return out
    }

    fun cellName(cell: Int) = "R${row(cell) + 1}C${col(cell) + 1}"

    fun unitName(unit: Int) = when {
        unit < 9 -> "row ${unit + 1}"
        unit < 18 -> "column ${unit - 8}"
        else -> "box ${unit - 17}"
    }

    /** "3", "3 and 7", "1, 4 and 9". */
    fun digitList(mask: Int): String = joinWords(digits(mask).map { it.toString() })

    fun cellList(cells: Iterable<Int>): String = joinWords(cells.map { cellName(it) })

    fun joinWords(words: List<String>): String = when (words.size) {
        0 -> ""
        1 -> words[0]
        else -> words.dropLast(1).joinToString(", ") + " and " + words.last()
    }

    /** Reads 81 cells; digits 1-9 are clues and '0', '.', '-' or '_' are empty. Other characters are skipped. */
    fun parse(text: String): IntArray? {
        val values = IntArray(CELLS)
        var i = 0
        for (ch in text) {
            val v = when (ch) {
                in '1'..'9' -> ch - '0'
                '0', '.', '-', '_' -> 0
                else -> continue
            }
            if (i == CELLS) return null
            values[i++] = v
        }
        return if (i == CELLS) values else null
    }

    fun format(values: IntArray, empty: Char = '.'): String =
        buildString(CELLS) { for (v in values) append(if (v == 0) empty else '0' + v) }

    /** Marks every filled cell whose digit repeats elsewhere in its row, column or box. */
    fun conflicts(values: IntArray): BooleanArray {
        val out = BooleanArray(CELLS)
        for (c in 0 until CELLS) {
            val v = values[c]
            if (v == 0) continue
            for (p in PEERS[c]) {
                if (values[p] == v) {
                    out[c] = true
                    break
                }
            }
        }
        return out
    }

    fun isConsistent(values: IntArray) = conflicts(values).none { it }

    /** Digits still possible in each empty cell given the filled ones (0 for filled cells). */
    fun candidates(values: IntArray): IntArray {
        val out = IntArray(CELLS)
        for (c in 0 until CELLS) {
            if (values[c] != 0) continue
            var used = 0
            for (p in PEERS[c]) if (values[p] != 0) used = used or bit(values[p])
            out[c] = ALL_DIGITS and used.inv()
        }
        return out
    }
}
