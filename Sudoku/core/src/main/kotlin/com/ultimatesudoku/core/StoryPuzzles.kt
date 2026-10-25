package com.ultimatesudoku.core

/**
 * The story's fixed puzzles: the same for every player, each graded at its level's difficulty and
 * solvable with the techniques its chapter teaches. A chapter can be played once it has its puzzles.
 */
object StoryPuzzles {

    /**
     * Chapter 1, The Pond, level by level: givens, then solution. Each needs nothing beyond full houses
     * and hidden singles; the Easy ones have plenty of clues, and the Medium ones (6 and the final
     * level) far fewer.
     */
    private val POND = listOf(
        // Level 1: Easy, 40 clues
        "...8.51.44...76.23..8.24.5...4.6..1.3624.7985.5..9.3...8.74.5..14.35...85.36.9..." to
            "279835164415976823638124759894563217362417985751298346986741532147352698523689471",
        // Level 2: Easy, 39 clues
        "427.5.8...91...2.3...9824..1...3...43.27489.59...1...2..3596...2.9...34...8.2.597" to
            "427153869891467253536982471175239684362748915984615732743596128259871346618324597",
        // Level 3: Easy, 38 clues
        "9..8..6.5.8..65..461..2....8.9546..21.6...4.94..9713.6....9..272..75..6.7.8..2..3" to
            "924837615387165294615429738839546172176283459452971386561394827243758961798612543",
        // Level 4: Easy, 40 clues
        ".4...9..317956...8...18.6.935.67..9...64.23...2..51.864.2.35...8...465315..9...4." to
            "648729153179563428235184679351678294786492315924351786412835967897246531563917842",
        // Level 5: Easy, 38 clues
        "7543....286.721.9......476...96..5.8...9.5...5.8..76...421......8.543.273....6184" to
            "754369812863721495291854763419632578637985241528417639942178356186543927375296184",
        // Level 6: Medium, 32 clues
        "..7.3..94.83.2.51.5.........5...3..9.296.735.6..8...7.........5.15.6.78.87..9.6.." to
            "267531894483926517591784236758213469129647358634859172346178925915462783872395641",
        // Level 7: Easy, 37 clues
        ".8651.4.2..2..4...4516..........8.63.1897624.26.3..........9628...2..5..6.4.5793." to
            "786513492932784156451692387549128763318976245267345819175439628893261574624857931",
        // Level 8: Easy, 36 clues
        "....8.6433.....217.2.3.49.874...9.6....1.6....6.8...942.87.1.3.493.....2176.2...." to
            "915287643384965217627314958742539861839146725561872394258791436493658172176423589",
        // Level 9: Easy, 37 clues
        "9...78.3..18.39....6..4.189.315..6....6.8.9....9..674.623.5..7....86.59..9.42...1" to
            "952178436418639257367245189731594628546782913289316745623951874174863592895427361",
        // Level 10: Easy, 36 clues
        ".2576849.48...9..2..924.......9..5....25.43....1..7.......751..8..3...25.5742198." to
            "125768493483159672769243851648932517972514368531687249296875134814396725357421986",
        // Final level: Medium, 30 clues
        "8.2.4.6........825.6...5.34.1....7.8...6.1...4.9....1.24.1...9.691........8.2.1.3" to
            "852349671934716825167285934316492758785631249429857316243168597691573482578924163",
    )

    private val CHAPTERS = mapOf(0 to POND)

    /** Whether chapter [chapter] (from 0) has its puzzles, and so can be played. */
    fun playable(chapter: Int): Boolean = CHAPTERS[chapter].orEmpty().isNotEmpty()

    /** Level [level]'s puzzle in [chapter], as its givens and its solution, or null if it hasn't one yet. */
    fun puzzle(chapter: Int, level: Int): Pair<IntArray, IntArray>? {
        val (givens, solution) = CHAPTERS[chapter]?.getOrNull(level - 1) ?: return null
        return checkNotNull(Grid.parse(givens)) to checkNotNull(Grid.parse(solution))
    }

    /** The tag a story game carries, naming its chapter and level. */
    fun tag(chapter: Int, level: Int): String = "$chapter/$level"

    /** The chapter and level a story game's [tag] names, or null if it doesn't name them. */
    fun parseTag(tag: String): Pair<Int, Int>? {
        val parts = tag.split('/')
        if (parts.size != 2) return null
        val chapter = parts[0].toIntOrNull() ?: return null
        val level = parts[1].toIntOrNull() ?: return null
        return chapter to level
    }
}
