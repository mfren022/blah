package com.ultimatesudoku.core

/**
 * Human solving techniques, easiest first. [tier] groups them by how hard they are to spot:
 * 1 singles, 2 basic, 3 intermediate, 4 advanced, 5 expert.
 */
enum class Technique(val title: String, val tier: Int) {
    FULL_HOUSE("Full House", 1),
    HIDDEN_SINGLE("Hidden Single", 1),
    NAKED_SINGLE("Naked Single", 1),
    POINTING("Pointing", 2),
    CLAIMING("Claiming", 2),
    NAKED_PAIR("Naked Pair", 2),
    HIDDEN_PAIR("Hidden Pair", 2),
    NAKED_TRIPLE("Naked Triple", 2),
    HIDDEN_TRIPLE("Hidden Triple", 3),
    X_WING("X-Wing", 3),
    SKYSCRAPER("Skyscraper", 3),
    TWO_STRING_KITE("2-String Kite", 3),
    NAKED_QUAD("Naked Quad", 3),
    HIDDEN_QUAD("Hidden Quad", 3),
    XY_WING("XY-Wing", 4),
    XYZ_WING("XYZ-Wing", 4),
    W_WING("W-Wing", 4),
    SWORDFISH("Swordfish", 4),
    SIMPLE_COLORING("Simple Coloring", 4),
    UNIQUE_RECTANGLE("Unique Rectangle", 4),
    BUG_PLUS_ONE("BUG+1", 4),
    JELLYFISH("Jellyfish", 5),
    XY_CHAIN("XY-Chain", 5),
}

/** A digit in a cell: a placement, an elimination or a highlighted candidate. */
data class Candidate(val cell: Int, val digit: Int)

/**
 * One logical deduction: the digits it places, the candidates it removes, and what to highlight
 * when explaining it.
 */
class Step(
    val technique: Technique,
    val description: String,
    val placements: List<Candidate> = emptyList(),
    val eliminations: List<Candidate> = emptyList(),
    /** Cells that make up the pattern. */
    val cells: List<Int> = emptyList(),
    /** Rows, columns or boxes (see [Grid.UNITS]) the pattern lives in. */
    val units: List<Int> = emptyList(),
    /** Candidates that form the pattern. */
    val highlights: List<Candidate> = emptyList(),
    /** A second group of pattern candidates, e.g. the other colour in a coloring chain. */
    val altHighlights: List<Candidate> = emptyList(),
) {
    override fun toString() = "${technique.title}: $description"
}
