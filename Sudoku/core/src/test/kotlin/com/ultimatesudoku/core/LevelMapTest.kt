package com.ultimatesudoku.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelMapTest {
    private val maps = (0 until 9).map { LevelMap.forChapter(it) }

    @Test
    fun levelsAreNumberedFromOne() {
        for (map in maps) {
            map.levels.forEachIndexed { i, level -> assertEquals(i + 1, level.number) }
            assertTrue(map.levels.all { it.lane in 0 until LevelMap.LANES })
        }
        // The placeholders number theirs in reading order.
        for (map in maps.drop(1)) {
            for ((a, b) in map.levels.zipWithNext()) {
                assertTrue(b.row > a.row || (b.row == a.row && b.lane > a.lane))
            }
        }
    }

    @Test
    fun thePondIsLaidOutAsDesigned() {
        val pond = maps[0]
        // Level 1; then 2 and 3, or 4 and 5, to level 6; then 7 and 8, or 9 and 10, to the final level.
        assertEquals(
            listOf(0 to 1, 1 to 0, 2 to 0, 1 to 2, 2 to 2, 3 to 1, 4 to 0, 5 to 0, 4 to 2, 5 to 2, 6 to 1),
            pond.levels.map { it.row to it.lane },
        )
        assertEquals(
            listOf(1 to 2, 1 to 4, 2 to 3, 4 to 5, 3 to 6, 5 to 6, 6 to 7, 6 to 9, 7 to 8, 9 to 10, 8 to 11, 10 to 11),
            pond.paths.map { it.from to it.to },
        )
        assertEquals(4, pond.routes())
        // Medium at level 6 and at the end, Easy everywhere else.
        for (level in pond.levels) {
            val medium = level.number == 6 || level == pond.final
            assertEquals(if (medium) Difficulty.MEDIUM else Difficulty.EASY, level.difficulty)
        }
    }

    @Test
    fun oneEntranceAndOneFinalLevel() {
        for (map in maps) {
            assertEquals(1, map.levels.count { it.row == 0 })
            assertEquals(1, map.levels.count { it.row == map.rows - 1 })
            assertEquals(1, map.final.lane)
        }
    }

    @Test
    fun everyLevelIsOnARouteFromEntranceToFinal() {
        for (map in maps) {
            for (path in map.paths) assertEquals(map.level(path.from).row + 1, map.level(path.to).row)
            for (level in map.levels) {
                if (level != map.entrance) assertTrue("Nothing leads to $level", map.paths.any { it.to == level.number })
                if (level != map.final) assertTrue("Nothing leads on from $level", map.paths.any { it.from == level.number })
            }
            assertTrue("Every chapter offers a choice", map.routes() >= 2)
        }
    }

    @Test
    fun pathsNeverCross() {
        for (map in maps) {
            for (p in map.paths) for (q in map.paths) {
                if (map.level(p.from).row != map.level(q.from).row) continue
                val upper = map.level(p.from).lane - map.level(q.from).lane
                val lower = map.level(p.to).lane - map.level(q.to).lane
                assertTrue("$p crosses $q", upper * lower >= 0)
            }
        }
    }

    @Test
    fun eachChapterHasItsOwnStableMap() {
        for (i in maps.indices) {
            val again = LevelMap.forChapter(i)
            assertEquals(maps[i].levels, again.levels)
            assertEquals(maps[i].paths, again.paths)
        }
        assertTrue(maps.map { it.levels to it.paths }.toSet().size >= 7)
        assertTrue(maps.last().rows > maps.first().rows)
    }

    @Test
    fun countsRoutes() {
        // Entrance, a split into two, a merge into the final level: two routes.
        val map = LevelMap(
            listOf(LevelMap.Level(1, 0, 1), LevelMap.Level(2, 1, 0), LevelMap.Level(3, 1, 2), LevelMap.Level(4, 2, 1)),
            listOf(LevelMap.Path(1, 2), LevelMap.Path(1, 3), LevelMap.Path(2, 4), LevelMap.Path(3, 4)),
        )
        assertEquals(2, map.routes())
        assertEquals(listOf(map.level(2), map.level(3)), map.next(map.entrance))
    }
}
