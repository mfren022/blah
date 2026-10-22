package com.ultimatesudoku.core

import com.ultimatesudoku.core.Grid.UNITS
import com.ultimatesudoku.core.Grid.bit
import com.ultimatesudoku.core.Grid.cellList
import com.ultimatesudoku.core.Grid.cellName
import com.ultimatesudoku.core.Grid.digitList
import com.ultimatesudoku.core.Grid.has
import com.ultimatesudoku.core.Grid.sees
import com.ultimatesudoku.core.Grid.size
import com.ultimatesudoku.core.Grid.unitName

/** Placed digits plus the candidates still open in each empty cell, like pencil marks on paper. */
class LogicState(val values: IntArray, val candidates: IntArray) {
    /** Starts from [values] with every candidate that the placed digits allow. */
    constructor(values: IntArray) : this(values.copyOf(), Grid.candidates(values))

    fun copy() = LogicState(values.copyOf(), candidates.copyOf())

    val isSolved: Boolean get() = values.none { it == 0 }

    fun place(cell: Int, digit: Int) {
        values[cell] = digit
        candidates[cell] = 0
        val keep = bit(digit).inv()
        for (p in Grid.PEERS[cell]) candidates[p] = candidates[p] and keep
    }

    fun eliminate(cell: Int, digit: Int) {
        candidates[cell] = candidates[cell] and bit(digit).inv()
    }

    fun apply(step: Step) {
        for (e in step.eliminations) eliminate(e.cell, e.digit)
        for (p in step.placements) place(p.cell, p.digit)
    }
}

/** How hard a puzzle is for a human: the hardest technique needed to finish it logically. */
class Rating(
    /** False when the techniques ran out before the grid was full. */
    val solved: Boolean,
    val hardest: Technique?,
    val steps: Int,
    private val counts: IntArray,
) {
    /** 0 when the puzzle is already full, 1-5 per [Technique.tier], 6 when logic alone gets stuck. */
    val tier: Int get() = if (!solved) 6 else hardest?.tier ?: 0

    fun count(technique: Technique) = counts[technique.ordinal]

    override fun toString() = if (solved) "tier $tier (${hardest?.title})" else "unsolved (stuck after ${hardest?.title})"
}

/**
 * Solves the way a person does: it always applies the easiest technique that makes progress and
 * explains each step. Every deduction is sound as long as each cell's candidates still include its
 * true digit, which holds for candidates computed from correctly placed digits.
 *
 * Unique Rectangle and BUG+1 assume the puzzle has exactly one solution, so they are only used when
 * [assumeUnique] is set.
 */
class LogicSolver(private val assumeUnique: Boolean = true) {

    private val finders: List<Pair<Technique, (LogicState) -> Step?>> = listOf(
        Technique.FULL_HOUSE to ::fullHouse,
        Technique.HIDDEN_SINGLE to ::hiddenSingle,
        Technique.NAKED_SINGLE to ::nakedSingle,
        Technique.POINTING to ::pointing,
        Technique.CLAIMING to ::claiming,
        Technique.NAKED_PAIR to { s -> nakedSubset(s, 2) },
        Technique.HIDDEN_PAIR to { s -> hiddenSubset(s, 2) },
        Technique.NAKED_TRIPLE to { s -> nakedSubset(s, 3) },
        Technique.HIDDEN_TRIPLE to { s -> hiddenSubset(s, 3) },
        Technique.X_WING to { s -> fish(s, 2) },
        Technique.SKYSCRAPER to ::skyscraper,
        Technique.TWO_STRING_KITE to ::twoStringKite,
        Technique.NAKED_QUAD to { s -> nakedSubset(s, 4) },
        Technique.HIDDEN_QUAD to { s -> hiddenSubset(s, 4) },
        Technique.XY_WING to ::xyWing,
        Technique.XYZ_WING to ::xyzWing,
        Technique.W_WING to ::wWing,
        Technique.SWORDFISH to { s -> fish(s, 3) },
        Technique.SIMPLE_COLORING to ::simpleColoring,
        Technique.UNIQUE_RECTANGLE to ::uniqueRectangle,
        Technique.BUG_PLUS_ONE to ::bugPlusOne,
        Technique.JELLYFISH to { s -> fish(s, 4) },
        Technique.XY_CHAIN to ::xyChain,
    ).filter { (technique, _) ->
        assumeUnique || (technique != Technique.UNIQUE_RECTANGLE && technique != Technique.BUG_PLUS_ONE)
    }

    /** The easiest step that makes progress, using techniques up to [maxTier], or null when stuck. */
    fun nextStep(state: LogicState, maxTier: Int = 5): Step? {
        for ((technique, find) in finders) {
            if (technique.tier > maxTier) break
            find(state)?.let { return it }
        }
        return null
    }

    /** Solves [puzzle] as far as techniques up to [maxTier] allow and reports the hardest one used. */
    fun rate(puzzle: IntArray, maxTier: Int = 5): Rating {
        val state = LogicState(puzzle)
        val counts = IntArray(Technique.entries.size)
        var hardest: Technique? = null
        var steps = 0
        while (!state.isSolved) {
            // Singles dominate; apply them in bulk without building explanations.
            val singles = applySingles(state)
            if (singles > 0) {
                counts[Technique.NAKED_SINGLE.ordinal] += singles
                if (hardest == null) hardest = Technique.NAKED_SINGLE
                steps += singles
                continue
            }
            if (state.isSolved) break
            val step = nextStep(state, maxTier) ?: return Rating(false, hardest, steps, counts)
            if (step.placements.isEmpty() && step.eliminations.isEmpty()) return Rating(false, hardest, steps, counts)
            counts[step.technique.ordinal]++
            if (hardest == null || step.technique.ordinal > hardest.ordinal) hardest = step.technique
            steps++
            state.apply(step)
        }
        return Rating(true, hardest, steps, counts)
    }

    /** Fills every naked and hidden single; returns how many digits were placed. */
    private fun applySingles(s: LogicState): Int {
        var placed = 0
        var progress = true
        while (progress) {
            progress = false
            for (c in 0 until 81) {
                if (s.values[c] == 0 && size(s.candidates[c]) == 1) {
                    s.place(c, Grid.first(s.candidates[c]))
                    placed++
                    progress = true
                }
            }
            for (unit in UNITS) {
                var once = 0
                var twice = 0
                var placedDigits = 0
                for (c in unit) {
                    val v = s.values[c]
                    if (v != 0) {
                        placedDigits = placedDigits or bit(v)
                        continue
                    }
                    val m = s.candidates[c]
                    twice = twice or (once and m)
                    once = once or m
                }
                var hidden = once and twice.inv() and placedDigits.inv()
                while (hidden != 0) {
                    val b = hidden and -hidden
                    hidden = hidden xor b
                    for (c in unit) {
                        if (s.values[c] == 0 && s.candidates[c] and b != 0) {
                            s.place(c, b.countTrailingZeroBits() + 1)
                            placed++
                            progress = true
                            break
                        }
                    }
                }
            }
        }
        return placed
    }

    // ---- Singles ------------------------------------------------------------------------------

    private fun fullHouse(s: LogicState): Step? {
        for (u in 0 until 27) {
            var empty = -1
            var emptyCount = 0
            var used = 0
            for (c in UNITS[u]) {
                val v = s.values[c]
                if (v == 0) {
                    emptyCount++
                    empty = c
                } else {
                    used = used or bit(v)
                }
            }
            if (emptyCount != 1) continue
            val missing = Grid.ALL_DIGITS and used.inv()
            if (size(missing) != 1 || s.candidates[empty] and missing == 0) continue
            val d = Grid.first(missing)
            return Step(
                Technique.FULL_HOUSE,
                "${cellName(empty)} is the last empty cell in ${unitName(u)}, so it takes the missing digit, $d.",
                placements = listOf(Candidate(empty, d)),
                cells = listOf(empty),
                units = listOf(u),
                highlights = listOf(Candidate(empty, d)),
            )
        }
        return null
    }

    private fun hiddenSingle(s: LogicState): Step? {
        for (u in BOXES_FIRST) {
            for (d in 1..9) {
                val b = bit(d)
                var where = -1
                var count = 0
                for (c in UNITS[u]) {
                    if (s.values[c] == d) {
                        count = -1
                        break
                    }
                    if (s.candidates[c] and b != 0) {
                        count++
                        where = c
                    }
                }
                if (count != 1) continue
                return Step(
                    Technique.HIDDEN_SINGLE,
                    "$d has only one possible spot in ${unitName(u)}: ${cellName(where)}. " +
                        "Every other cell there is blocked by a $d in its row, column or box.",
                    placements = listOf(Candidate(where, d)),
                    cells = listOf(where),
                    units = listOf(u),
                    highlights = listOf(Candidate(where, d)),
                )
            }
        }
        return null
    }

    private fun nakedSingle(s: LogicState): Step? {
        for (c in 0 until 81) {
            if (s.values[c] != 0 || size(s.candidates[c]) != 1) continue
            val d = Grid.first(s.candidates[c])
            return Step(
                Technique.NAKED_SINGLE,
                "${cellName(c)} has a single candidate left: $d. " +
                    "Every other digit already appears in its row, column or box.",
                placements = listOf(Candidate(c, d)),
                cells = listOf(c),
                units = Grid.UNITS_OF[c].toList(),
                highlights = listOf(Candidate(c, d)),
            )
        }
        return null
    }

    // ---- Locked candidates --------------------------------------------------------------------

    private fun pointing(s: LogicState): Step? {
        for (b in 0 until 9) {
            val box = 18 + b
            for (d in 1..9) {
                val cells = cellsWith(s, box, d)
                if (cells.size < 2) continue
                for (line in intArrayOf(Grid.row(cells[0]), 9 + Grid.col(cells[0]))) {
                    if (!cells.all { inLine(it, line) }) continue
                    val elims = UNITS[line].filter { Grid.box(it) != b && has(s.candidates[it], d) }
                    if (elims.isEmpty()) continue
                    return Step(
                        Technique.POINTING,
                        "In box ${b + 1}, every spot for $d lies in ${unitName(line)}. " +
                            "So the $d of ${unitName(line)} must be inside box ${b + 1}: " +
                            "remove $d from ${cellList(elims)}.",
                        eliminations = elims.map { Candidate(it, d) },
                        cells = cells,
                        units = listOf(box, line),
                        highlights = cells.map { Candidate(it, d) },
                    )
                }
            }
        }
        return null
    }

    private fun claiming(s: LogicState): Step? {
        for (line in 0 until 18) {
            for (d in 1..9) {
                val cells = cellsWith(s, line, d)
                if (cells.size < 2) continue
                val b = Grid.box(cells[0])
                if (!cells.all { Grid.box(it) == b }) continue
                val elims = UNITS[18 + b].filter { !inLine(it, line) && has(s.candidates[it], d) }
                if (elims.isEmpty()) continue
                return Step(
                    Technique.CLAIMING,
                    "In ${unitName(line)}, every spot for $d lies in box ${b + 1}. " +
                        "So the $d of box ${b + 1} must be on ${unitName(line)}: remove $d from ${cellList(elims)}.",
                    eliminations = elims.map { Candidate(it, d) },
                    cells = cells,
                    units = listOf(line, 18 + b),
                    highlights = cells.map { Candidate(it, d) },
                )
            }
        }
        return null
    }

    // ---- Subsets ------------------------------------------------------------------------------

    private fun nakedSubset(s: LogicState, k: Int): Step? {
        val technique = when (k) {
            2 -> Technique.NAKED_PAIR
            3 -> Technique.NAKED_TRIPLE
            else -> Technique.NAKED_QUAD
        }
        for (u in 0 until 27) {
            val empty = UNITS[u].filter { s.values[it] == 0 }
            if (empty.size <= k) continue
            val pool = empty.filter { size(s.candidates[it]) in 2..k }
            if (pool.size < k) continue
            val step = combinations(pool.size, k) { picked ->
                var union = 0
                for (i in picked) union = union or s.candidates[pool[i]]
                if (size(union) != k) return@combinations null
                val subset = picked.map { pool[it] }
                val elims = ArrayList<Candidate>()
                for (c in empty) {
                    if (c in subset) continue
                    for (d in Grid.digits(s.candidates[c] and union)) elims += Candidate(c, d)
                }
                if (elims.isEmpty()) return@combinations null
                Step(
                    technique,
                    "${cellList(subset)} can only hold ${digitList(union)}, so those ${k.words()} digits are " +
                        "used up there. Remove them from the rest of ${unitName(u)}.",
                    eliminations = elims,
                    cells = subset,
                    units = listOf(u),
                    highlights = subset.flatMap { c -> Grid.digits(s.candidates[c]).map { Candidate(c, it) } },
                )
            }
            if (step != null) return step
        }
        return null
    }

    internal fun hiddenSubset(s: LogicState, k: Int): Step? {
        val technique = when (k) {
            2 -> Technique.HIDDEN_PAIR
            3 -> Technique.HIDDEN_TRIPLE
            else -> Technique.HIDDEN_QUAD
        }
        for (u in 0 until 27) {
            val cells = UNITS[u]
            if (cells.count { s.values[it] == 0 } <= k) continue
            val positions = IntArray(10)
            val digits = ArrayList<Int>()
            for (d in 1..9) {
                var m = 0
                for (i in 0 until 9) if (has(s.candidates[cells[i]], d)) m = m or (1 shl i)
                positions[d] = m
                if (size(m) in 2..k) digits += d
            }
            if (digits.size < k) continue
            val step = combinations(digits.size, k) { picked ->
                var where = 0
                var digitMask = 0
                for (i in picked) {
                    where = where or positions[digits[i]]
                    digitMask = digitMask or bit(digits[i])
                }
                if (size(where) != k) return@combinations null
                val subset = (0 until 9).filter { where and (1 shl it) != 0 }.map { cells[it] }
                val elims = subset.flatMap { c ->
                    Grid.digits(s.candidates[c] and digitMask.inv()).map { Candidate(c, it) }
                }
                if (elims.isEmpty()) return@combinations null
                Step(
                    technique,
                    "In ${unitName(u)}, ${digitList(digitMask)} can only go in ${cellList(subset)}. " +
                        "Those cells must take exactly these digits, so every other candidate in them goes.",
                    eliminations = elims,
                    cells = subset,
                    units = listOf(u),
                    highlights = subset.flatMap { c ->
                        Grid.digits(s.candidates[c] and digitMask).map { Candidate(c, it) }
                    },
                )
            }
            if (step != null) return step
        }
        return null
    }

    // ---- Fish ---------------------------------------------------------------------------------

    /** X-Wing (k = 2), Swordfish (3) and Jellyfish (4), with rows or columns as the base lines. */
    internal fun fish(s: LogicState, k: Int): Step? {
        val technique = when (k) {
            2 -> Technique.X_WING
            3 -> Technique.SWORDFISH
            else -> Technique.JELLYFISH
        }
        for (d in 1..9) {
            for (rowsAreBase in BOTH) {
                val masks = lineMasks(s, d, rowsAreBase)
                val lines = (0 until 9).filter { size(masks[it]) in 2..k }
                if (lines.size < k) continue
                val step = combinations(lines.size, k) { picked ->
                    val base = picked.map { lines[it] }
                    var cover = 0
                    for (line in base) cover = cover or masks[line]
                    if (size(cover) != k) return@combinations null
                    val coverLines = (0 until 9).filter { cover and (1 shl it) != 0 }
                    val elims = ArrayList<Candidate>()
                    for (x in coverLines) {
                        for (line in 0 until 9) {
                            if (line in base) continue
                            val c = at(line, x, rowsAreBase)
                            if (has(s.candidates[c], d)) elims += Candidate(c, d)
                        }
                    }
                    if (elims.isEmpty()) return@combinations null
                    val baseUnits = base.map { lineUnit(it, rowsAreBase) }
                    val coverUnits = coverLines.map { lineUnit(it, !rowsAreBase) }
                    val pattern = base.flatMap { line ->
                        coverLines.map { at(line, it, rowsAreBase) }.filter { has(s.candidates[it], d) }
                    }
                    Step(
                        technique,
                        "In ${Grid.joinWords(baseUnits.map(::unitName))}, every spot for $d lies in " +
                            "${Grid.joinWords(coverUnits.map(::unitName))}. Each of those lines takes its $d " +
                            "from this pattern, so remove $d from the rest of them.",
                        eliminations = elims,
                        cells = pattern,
                        units = baseUnits + coverUnits,
                        highlights = pattern.map { Candidate(it, d) },
                    )
                }
                if (step != null) return step
            }
        }
        return null
    }

    // ---- Single-digit patterns ----------------------------------------------------------------

    private fun skyscraper(s: LogicState): Step? {
        for (d in 1..9) {
            for (rowsAreBase in BOTH) {
                val masks = lineMasks(s, d, rowsAreBase)
                val pairs = (0 until 9).filter { size(masks[it]) == 2 }
                for (i in pairs.indices) {
                    for (j in i + 1 until pairs.size) {
                        val l1 = pairs[i]
                        val l2 = pairs[j]
                        val common = masks[l1] and masks[l2]
                        if (size(common) != 1) continue
                        val baseX = common.countTrailingZeroBits()
                        val end1 = at(l1, (masks[l1] xor common).countTrailingZeroBits(), rowsAreBase)
                        val end2 = at(l2, (masks[l2] xor common).countTrailingZeroBits(), rowsAreBase)
                        val elims = seeingBoth(s, d, end1, end2)
                        if (elims.isEmpty()) continue
                        val base1 = at(l1, baseX, rowsAreBase)
                        val base2 = at(l2, baseX, rowsAreBase)
                        val cells = listOf(base1, end1, base2, end2)
                        return Step(
                            Technique.SKYSCRAPER,
                            "${unitName(lineUnit(l1, rowsAreBase)).capitalized()} and ${unitName(lineUnit(l2, rowsAreBase))} " +
                                "each have two spots for $d, and ${cellName(base1)} and ${cellName(base2)} share " +
                                "${unitName(lineUnit(baseX, !rowsAreBase))}. They can't both be $d, so " +
                                "${cellName(end1)} or ${cellName(end2)} is. Any cell that sees both can't be $d.",
                            eliminations = elims.map { Candidate(it, d) },
                            cells = cells,
                            units = listOf(lineUnit(l1, rowsAreBase), lineUnit(l2, rowsAreBase), lineUnit(baseX, !rowsAreBase)),
                            highlights = cells.map { Candidate(it, d) },
                        )
                    }
                }
            }
        }
        return null
    }

    private fun twoStringKite(s: LogicState): Step? {
        for (d in 1..9) {
            val rowPairs = (0 until 9).map { cellsWith(s, it, d) }.filter { it.size == 2 }
            val colPairs = (9 until 18).map { cellsWith(s, it, d) }.filter { it.size == 2 }
            for (rowPair in rowPairs) {
                for (colPair in colPairs) {
                    for (a in 0..1) {
                        for (b in 0..1) {
                            val rowLink = rowPair[a]
                            val rowEnd = rowPair[1 - a]
                            val colLink = colPair[b]
                            val colEnd = colPair[1 - b]
                            if (rowLink == colLink || rowLink == colEnd || rowEnd == colLink || rowEnd == colEnd) continue
                            val box = Grid.box(rowLink)
                            if (Grid.box(colLink) != box || Grid.box(rowEnd) == box || Grid.box(colEnd) == box) continue
                            val elims = seeingBoth(s, d, rowEnd, colEnd)
                            if (elims.isEmpty()) continue
                            val cells = listOf(rowEnd, rowLink, colLink, colEnd)
                            return Step(
                                Technique.TWO_STRING_KITE,
                                "${unitName(Grid.row(rowLink)).capitalized()} and ${unitName(9 + Grid.col(colLink))} " +
                                    "each have two spots for $d, and they meet in box ${box + 1} at " +
                                    "${cellName(rowLink)} and ${cellName(colLink)}. Those can't both be $d, so " +
                                    "${cellName(rowEnd)} or ${cellName(colEnd)} is. Any cell that sees both can't be $d.",
                                eliminations = elims.map { Candidate(it, d) },
                                cells = cells,
                                units = listOf(Grid.row(rowLink), 9 + Grid.col(colLink), 18 + box),
                                highlights = cells.map { Candidate(it, d) },
                            )
                        }
                    }
                }
            }
        }
        return null
    }

    // ---- Wings --------------------------------------------------------------------------------

    private fun xyWing(s: LogicState): Step? {
        for (pivot in 0 until 81) {
            val pm = s.candidates[pivot]
            if (s.values[pivot] != 0 || size(pm) != 2) continue
            val wings = Grid.PEERS[pivot].filter { s.values[it] == 0 && size(s.candidates[it]) == 2 }
            for (w1 in wings) {
                val m1 = s.candidates[w1]
                val shared = m1 and pm
                if (size(shared) != 1) continue
                val z = m1 and pm.inv()
                val wanted = (pm xor shared) or z
                for (w2 in wings) {
                    if (w2 == w1 || s.candidates[w2] != wanted) continue
                    val zd = Grid.first(z)
                    val elims = seeingBoth(s, zd, w1, w2).filter { it != pivot }
                    if (elims.isEmpty()) continue
                    val x = Grid.first(shared)
                    val y = Grid.first(pm xor shared)
                    return Step(
                        Technique.XY_WING,
                        "${cellName(pivot)} is $x or $y. If it's $x, ${cellName(w1)} must be $zd; if it's $y, " +
                            "${cellName(w2)} must be $zd. Either way one of them is $zd, so any cell that sees " +
                            "both can't be $zd.",
                        eliminations = elims.map { Candidate(it, zd) },
                        cells = listOf(pivot, w1, w2),
                        highlights = listOf(Candidate(pivot, x), Candidate(pivot, y), Candidate(w1, x), Candidate(w2, y)),
                        altHighlights = listOf(Candidate(w1, zd), Candidate(w2, zd)),
                    )
                }
            }
        }
        return null
    }

    private fun xyzWing(s: LogicState): Step? {
        for (pivot in 0 until 81) {
            val pm = s.candidates[pivot]
            if (s.values[pivot] != 0 || size(pm) != 3) continue
            val wings = Grid.PEERS[pivot].filter {
                s.values[it] == 0 && size(s.candidates[it]) == 2 && s.candidates[it] and pm.inv() == 0
            }
            for (i in wings.indices) {
                for (j in i + 1 until wings.size) {
                    val w1 = wings[i]
                    val w2 = wings[j]
                    val m1 = s.candidates[w1]
                    val m2 = s.candidates[w2]
                    if (m1 == m2 || m1 or m2 != pm) continue
                    val zd = Grid.first(m1 and m2)
                    val elims = seeingBoth(s, zd, w1, w2).filter { it != pivot && sees(it, pivot) }
                    if (elims.isEmpty()) continue
                    return Step(
                        Technique.XYZ_WING,
                        "${cellName(pivot)} is ${digitList(pm)}, ${cellName(w1)} is ${digitList(m1)} and " +
                            "${cellName(w2)} is ${digitList(m2)}. Whatever the pivot is, one of these three cells " +
                            "is $zd, so any cell that sees all three can't be $zd.",
                        eliminations = elims.map { Candidate(it, zd) },
                        cells = listOf(pivot, w1, w2),
                        highlights = listOf(pivot, w1, w2).flatMap { c ->
                            Grid.digits(s.candidates[c] and bit(zd).inv()).map { Candidate(c, it) }
                        },
                        altHighlights = listOf(Candidate(pivot, zd), Candidate(w1, zd), Candidate(w2, zd)),
                    )
                }
            }
        }
        return null
    }

    private fun wWing(s: LogicState): Step? {
        val bivalue = (0 until 81).filter { s.values[it] == 0 && size(s.candidates[it]) == 2 }
        for (i in bivalue.indices) {
            for (j in i + 1 until bivalue.size) {
                val c1 = bivalue[i]
                val c2 = bivalue[j]
                val m = s.candidates[c1]
                if (s.candidates[c2] != m || sees(c1, c2)) continue
                for (link in Grid.digits(m)) {
                    val other = Grid.first(m and bit(link).inv())
                    for (u in 0 until 27) {
                        val ends = cellsWith(s, u, link)
                        if (ends.size != 2) continue
                        val (p, q) = ends
                        if (p == c1 || p == c2 || q == c1 || q == c2) continue
                        val linked = (sees(p, c1) && sees(q, c2)) || (sees(p, c2) && sees(q, c1))
                        if (!linked) continue
                        val elims = seeingBoth(s, other, c1, c2)
                        if (elims.isEmpty()) continue
                        return Step(
                            Technique.W_WING,
                            "${cellName(c1)} and ${cellName(c2)} can only be $link or $other, and $link in " +
                                "${unitName(u)} must be at ${cellName(p)} or ${cellName(q)}, which see one each. " +
                                "So they can't both be $link: one of them is $other, and any cell that sees both " +
                                "can't be $other.",
                            eliminations = elims.map { Candidate(it, other) },
                            cells = listOf(c1, p, q, c2),
                            units = listOf(u),
                            highlights = listOf(Candidate(c1, link), Candidate(c2, link), Candidate(p, link), Candidate(q, link)),
                            altHighlights = listOf(Candidate(c1, other), Candidate(c2, other)),
                        )
                    }
                }
            }
        }
        return null
    }

    // ---- Coloring and chains ------------------------------------------------------------------

    private fun simpleColoring(s: LogicState): Step? {
        for (d in 1..9) {
            val links = Array(81) { ArrayList<Int>(3) }
            for (u in 0 until 27) {
                val ends = cellsWith(s, u, d)
                if (ends.size == 2) {
                    links[ends[0]].add(ends[1])
                    links[ends[1]].add(ends[0])
                }
            }
            val color = IntArray(81) { -1 }
            for (start in 0 until 81) {
                if (links[start].isEmpty() || color[start] != -1) continue
                val chain = ArrayList<Int>()
                var consistent = true
                color[start] = 0
                val queue = ArrayDeque<Int>().apply { add(start) }
                while (queue.isNotEmpty()) {
                    val c = queue.removeFirst()
                    chain += c
                    for (n in links[c]) {
                        if (color[n] == -1) {
                            color[n] = 1 - color[c]
                            queue.add(n)
                        } else if (color[n] == color[c]) {
                            consistent = false
                        }
                    }
                }
                if (!consistent || chain.size < 3) continue
                val colorA = chain.filter { color[it] == 0 }
                val colorB = chain.filter { color[it] == 1 }
                val highlightsA = colorA.map { Candidate(it, d) }
                val highlightsB = colorB.map { Candidate(it, d) }

                for ((group, otherGroup) in listOf(colorA to colorB, colorB to colorA)) {
                    val clash = group.firstNotNullOfOrNull { a -> group.firstOrNull { b -> sees(a, b) }?.let { a to it } }
                        ?: continue
                    return Step(
                        Technique.SIMPLE_COLORING,
                        "Follow the $d's that come in pairs: along the chain, either every shaded cell or every " +
                            "outlined cell is $d. Two cells of one colour, ${cellName(clash.first)} and " +
                            "${cellName(clash.second)}, see each other, so that colour is wrong: remove $d from " +
                            "${cellList(group)}.",
                        eliminations = group.map { Candidate(it, d) },
                        cells = chain,
                        highlights = otherGroup.map { Candidate(it, d) },
                        altHighlights = group.map { Candidate(it, d) },
                    )
                }

                val members = chain.toHashSet()
                val elims = (0 until 81).filter { c ->
                    c !in members && has(s.candidates[c], d) &&
                        colorA.any { sees(c, it) } && colorB.any { sees(c, it) }
                }
                if (elims.isNotEmpty()) {
                    return Step(
                        Technique.SIMPLE_COLORING,
                        "Follow the $d's that come in pairs: along the chain, either every shaded cell or every " +
                            "outlined cell is $d. ${cellList(elims)} ${if (elims.size == 1) "sees" else "see"} " +
                            "both colours, so it can't be $d either way.",
                        eliminations = elims.map { Candidate(it, d) },
                        cells = chain,
                        highlights = highlightsA,
                        altHighlights = highlightsB,
                    )
                }
            }
        }
        return null
    }

    private fun xyChain(s: LogicState): Step? {
        val bivalue = BooleanArray(81) { s.values[it] == 0 && size(s.candidates[it]) == 2 }
        val previous = IntArray(81 * 10)
        val depth = IntArray(81 * 10)
        val queue = IntArray(81 * 10)
        for (start in 0 until 81) {
            if (!bivalue[start]) continue
            for (x in Grid.digits(s.candidates[start])) {
                // Suppose start is not x: then it is y, and each linked cell is forced in turn.
                val y = Grid.first(s.candidates[start] and bit(x).inv())
                previous.fill(-1)
                val first = start * 10 + y
                previous[first] = first
                depth[first] = 0
                var head = 0
                var tail = 0
                queue[tail++] = first
                while (head < tail) {
                    val state = queue[head++]
                    val c = state / 10
                    val v = state % 10
                    for (p in Grid.PEERS[c]) {
                        if (!bivalue[p] || p == start || !has(s.candidates[p], v)) continue
                        val pv = Grid.first(s.candidates[p] and bit(v).inv())
                        val next = p * 10 + pv
                        if (previous[next] != -1) continue
                        previous[next] = state
                        depth[next] = depth[state] + 1
                        if (pv == x && depth[next] >= 2) {
                            val elims = seeingBoth(s, x, start, p)
                            if (elims.isNotEmpty()) return xyChainStep(s, x, start, next, previous, elims)
                        }
                        if (depth[next] < MAX_CHAIN_LINKS) queue[tail++] = next
                    }
                }
            }
        }
        return null
    }

    private fun xyChainStep(s: LogicState, x: Int, start: Int, end: Int, previous: IntArray, elims: List<Int>): Step {
        val path = ArrayList<Int>()
        var state = end
        while (true) {
            path += state
            val prev = previous[state]
            if (prev == state) break
            state = prev
        }
        path.reverse()
        val cells = path.map { it / 10 }
        val endCell = end / 10
        val links = path.joinToString(" → ") { "${cellName(it / 10)}=${it % 10}" }
        return Step(
            Technique.XY_CHAIN,
            "A chain of two-candidate cells: if ${cellName(start)} isn't $x, then $links. So either " +
                "${cellName(start)} or ${cellName(endCell)} is $x, and any cell that sees both can't be $x.",
            eliminations = elims.map { Candidate(it, x) },
            cells = cells,
            highlights = path.map { Candidate(it / 10, it % 10) },
            altHighlights = listOf(Candidate(start, x), Candidate(endCell, x)),
        )
    }

    // ---- Uniqueness ---------------------------------------------------------------------------

    private fun uniqueRectangle(s: LogicState): Step? {
        for (r1 in 0 until 9) for (r2 in r1 + 1 until 9) for (c1 in 0 until 9) for (c2 in c1 + 1 until 9) {
            // A deadly pattern spans exactly two boxes.
            if ((r1 / 3 == r2 / 3) == (c1 / 3 == c2 / 3)) continue
            val corners = intArrayOf(Grid.cell(r1, c1), Grid.cell(r1, c2), Grid.cell(r2, c1), Grid.cell(r2, c2))
            if (corners.any { s.values[it] != 0 }) continue
            for (target in corners) {
                val others = corners.filter { it != target }
                val pair = s.candidates[others[0]]
                if (size(pair) != 2 || others.any { s.candidates[it] != pair }) continue
                val tm = s.candidates[target]
                if (tm and pair != pair || tm == pair) continue
                return Step(
                    Technique.UNIQUE_RECTANGLE,
                    "${cellList(others)} can only be ${digitList(pair)}. If ${cellName(target)} were also " +
                        "${digitList(pair).replace(" and ", " or ")}, the four corners could swap their digits " +
                        "and the puzzle would have two solutions. So ${cellName(target)} isn't " +
                        "${digitList(pair).replace(" and ", " or ")}.",
                    eliminations = Grid.digits(pair).map { Candidate(target, it) },
                    cells = corners.toList(),
                    highlights = others.flatMap { c -> Grid.digits(pair).map { Candidate(c, it) } },
                )
            }
        }
        return null
    }

    private fun bugPlusOne(s: LogicState): Step? {
        var odd = -1
        for (c in 0 until 81) {
            if (s.values[c] != 0) continue
            when (size(s.candidates[c])) {
                2 -> Unit
                3 -> if (odd == -1) odd = c else return null
                else -> return null
            }
        }
        if (odd == -1) return null
        for (d in Grid.digits(s.candidates[odd])) {
            val reduced = s.candidates[odd] and bit(d).inv()
            val isGrave = (0 until 27).all { u ->
                (1..9).all { digit ->
                    val count = UNITS[u].count { c -> has(if (c == odd) reduced else s.candidates[c], digit) }
                    count == 0 || count == 2
                }
            }
            if (!isGrave) continue
            return Step(
                Technique.BUG_PLUS_ONE,
                "Every unsolved cell has two candidates except ${cellName(odd)}. Without $d there, each " +
                    "candidate would appear exactly twice in every row, column and box, a deadlock with two " +
                    "solutions. So ${cellName(odd)} must be $d.",
                placements = listOf(Candidate(odd, d)),
                cells = listOf(odd),
                highlights = listOf(Candidate(odd, d)),
            )
        }
        return null
    }

    // ---- Helpers ------------------------------------------------------------------------------

    private fun cellsWith(s: LogicState, unit: Int, d: Int): List<Int> {
        val b = bit(d)
        return UNITS[unit].filter { s.candidates[it] and b != 0 }
    }

    /** Cells other than [a] and [b] that still have [d] and see both of them. */
    private fun seeingBoth(s: LogicState, d: Int, a: Int, b: Int): List<Int> =
        (0 until 81).filter { it != a && it != b && has(s.candidates[it], d) && sees(it, a) && sees(it, b) }

    private fun inLine(cell: Int, line: Int) =
        if (line < 9) Grid.row(cell) == line else Grid.col(cell) == line - 9

    /** For each row (or column), a mask of the columns (rows) where [d] is still a candidate. */
    private fun lineMasks(s: LogicState, d: Int, rows: Boolean): IntArray {
        val b = bit(d)
        return IntArray(9) { line ->
            var m = 0
            for (i in 0 until 9) if (s.candidates[at(line, i, rows)] and b != 0) m = m or (1 shl i)
            m
        }
    }

    private fun at(line: Int, index: Int, rows: Boolean) = if (rows) Grid.cell(line, index) else Grid.cell(index, line)

    private fun lineUnit(line: Int, rows: Boolean) = if (rows) line else 9 + line

    /** Calls [visit] with each k-element index combination of 0 until n until it returns a step. */
    private inline fun combinations(n: Int, k: Int, visit: (IntArray) -> Step?): Step? {
        if (k > n) return null
        val idx = IntArray(k) { it }
        while (true) {
            visit(idx)?.let { return it }
            var i = k - 1
            while (i >= 0 && idx[i] == n - k + i) i--
            if (i < 0) return null
            idx[i]++
            for (j in i + 1 until k) idx[j] = idx[j - 1] + 1
        }
    }

    private fun Int.words() = when (this) {
        2 -> "two"
        3 -> "three"
        else -> "four"
    }

    private fun String.capitalized() = replaceFirstChar { it.uppercaseChar() }

    private companion object {
        val BOXES_FIRST = (18 until 27) + (0 until 18)
        val BOTH = booleanArrayOf(true, false)
        const val MAX_CHAIN_LINKS = 12
    }
}
