package com.ultimatesudoku.core

import kotlin.math.abs
import kotlin.random.Random

/**
 * The levels of one story chapter and the paths between them. Levels sit in rows, from the entrance
 * (row 0) down to the chapter's final level, in up to three lanes. Every path leads from a level to
 * one in the next row, so routes branch and merge but each visits one level per row, and no two
 * paths cross. Paths are listed row by row.
 */
class LevelMap(val levels: List<Level>, val paths: List<Path>) {

    /** A level, numbered from 1, and how hard its puzzle is. */
    data class Level(val number: Int, val row: Int, val lane: Int, val difficulty: Difficulty = Difficulty.EASY)

    /** A path from level [from] to level [to], by number. */
    data class Path(val from: Int, val to: Int)

    val rows: Int get() = levels.last().row + 1
    val entrance: Level get() = levels.first()
    val final: Level get() = levels.last()

    fun level(number: Int): Level = levels[number - 1]

    /** The levels that paths lead to from [level]. */
    fun next(level: Level): List<Level> = paths.filter { it.from == level.number }.map { level(it.to) }

    /** How many different routes lead from the entrance to the final level. */
    fun routes(): Int {
        val count = IntArray(levels.size + 1)
        count[entrance.number] = 1
        // Paths run row by row in order, so each level's count is complete before it is used.
        for (path in paths) count[path.to] += count[path.from]
        return count[final.number]
    }

    companion object {
        const val LANES = 3

        private val SINGLE = listOf(1)
        private val PAIR = listOf(0, 2)
        private val TRIPLE = listOf(0, 1, 2)

        /**
         * Chapter 1, The Pond: from level 1, two routes (2 then 3, or 4 then 5) to level 6, then two
         * more (7 then 8, or 9 then 10) to the final level. Levels are numbered down each route in
         * turn; 6 and the final level are Medium, the rest Easy.
         */
        private val POND = LevelMap(
            listOf(
                Level(1, 0, 1),
                Level(2, 1, 0), Level(3, 2, 0), Level(4, 1, 2), Level(5, 2, 2),
                Level(6, 3, 1, Difficulty.MEDIUM),
                Level(7, 4, 0), Level(8, 5, 0), Level(9, 4, 2), Level(10, 5, 2),
                Level(11, 6, 1, Difficulty.MEDIUM),
            ),
            listOf(
                Path(1, 2), Path(1, 4), Path(2, 3), Path(4, 5), Path(3, 6), Path(5, 6),
                Path(6, 7), Path(6, 9), Path(7, 8), Path(9, 10), Path(8, 11), Path(10, 11),
            ),
        )

        /**
         * The map for chapter [index] (from 0). Chapter 1's is designed; the others are placeholders
         * until theirs are, numbered in reading order (row by row, left to right), the same every time
         * and different for every chapter. Later chapters run longer.
         */
        fun forChapter(index: Int): LevelMap = if (index == 0) POND else placeholder(index)

        private fun placeholder(index: Int): LevelMap {
            val random = Random(0x5D0C + index * 31)
            val rows = ArrayList<List<Int>>()
            rows += SINGLE
            // Every chapter opens with a choice of route.
            rows += if (random.nextInt(3) == 0) TRIPLE else PAIR
            val middle = 4 + index / 3
            while (rows.size < middle + 1) rows += nextRow(rows.last(), random)
            rows += SINGLE
            return build(rows, random)
        }

        private fun nextRow(previous: List<Int>, random: Random): List<Int> {
            val choices = when (previous) {
                // After routes merge, they split again.
                SINGLE -> listOf(PAIR, PAIR, PAIR, TRIPLE)
                PAIR -> listOf(PAIR, SINGLE, SINGLE, TRIPLE)
                else -> listOf(PAIR, PAIR, SINGLE)
            }
            return choices[random.nextInt(choices.size)]
        }

        /** Numbers the levels in [rows] (each a list of lanes) and links each row to the next. */
        private fun build(rows: List<List<Int>>, random: Random): LevelMap {
            val levels = ArrayList<Level>()
            val numberAt = Array(rows.size) { IntArray(LANES) }
            rows.forEachIndexed { row, lanes ->
                for (lane in lanes) {
                    levels += Level(levels.size + 1, row, lane)
                    numberAt[row][lane] = levels.size
                }
            }
            val paths = ArrayList<Path>()
            for (row in 0 until rows.size - 1) {
                val upper = rows[row]
                val lower = rows[row + 1]
                val links = LinkedHashSet<Pair<Int, Int>>()
                when {
                    upper.size == 1 -> for (lane in lower) links += upper[0] to lane
                    lower.size == 1 -> for (lane in upper) links += lane to lower[0]
                    else -> {
                        // Straight on where the lanes continue, then to the nearest lane, so paths
                        // never cross.
                        for (lane in upper) if (lane in lower) links += lane to lane
                        for (lane in lower) if (links.none { it.second == lane }) links += nearest(upper, lane, random) to lane
                        for (lane in upper) if (links.none { it.first == lane }) links += lane to nearest(lower, lane, random)
                    }
                }
                for ((from, to) in links.sortedWith(compareBy({ it.first }, { it.second }))) {
                    paths += Path(numberAt[row][from], numberAt[row + 1][to])
                }
            }
            return LevelMap(levels, paths)
        }

        private fun nearest(lanes: List<Int>, lane: Int, random: Random): Int {
            val distance = lanes.minOf { abs(it - lane) }
            val closest = lanes.filter { abs(it - lane) == distance }
            return closest[random.nextInt(closest.size)]
        }
    }
}
