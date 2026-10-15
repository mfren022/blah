package com.topplecorp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BoardTest {
    private fun level(vararg pieces: PieceDef, tools: Map<ToolType, Int> = mapOf(ToolType.HAMMER to -1)) =
        LevelDef(0, 0, "t", 4, 8, pieces.toList(), tools, TurnLimits(3, 5, 8))

    @Test
    fun hammerDamagesAndDestroys() {
        val b = Board.fromLevel(level(PieceDef(1, Material.WOOD, listOf(Cell(0, 0), Cell(1, 0)))))
        b.applyTool(ToolType.HAMMER, Cell(0, 0))
        assertEquals(1, b.piece(1)!!.hp)
        b.applyTool(ToolType.HAMMER, Cell(1, 0))
        assertTrue(b.pieces.isEmpty())
        assertEquals(Outcome.WON, b.outcome)
        assertEquals(3, b.starsEarned)
    }

    @Test
    fun hammerCannotDentMetal() {
        val b = Board.fromLevel(level(PieceDef(1, Material.METAL, listOf(Cell(0, 0)))))
        b.applyTool(ToolType.HAMMER, Cell(0, 0))
        assertEquals(Material.METAL.hp, b.piece(1)!!.hp)
        // Only a hammer and immune blocks left: nothing useful to do.
        assertEquals(Outcome.LOST, b.outcome)
    }

    @Test
    fun unsupportedPiecesFallAndCrush() {
        // Stone sits on glass that sits on wood. Breaking the glass drops the stone onto the wood.
        val b = Board.fromLevel(level(
            PieceDef(1, Material.WOOD, listOf(Cell(0, 0))),
            PieceDef(2, Material.GLASS, listOf(Cell(0, 1), Cell(0, 2), Cell(0, 3))),
            PieceDef(3, Material.STONE, listOf(Cell(0, 4))),
        ))
        val phases = b.applyTool(ToolType.HAMMER, Cell(0, 2))
        val fell = phases.flatten().filterIsInstance<GameEvent.Fell>().first()
        assertEquals(3, fell.distance)
        // impactDamage(4, 3) = 4 >= wood hp, so the wood is crushed and the stone then hits the ground.
        assertTrue(b.piece(1) == null)
        assertEquals(Cell(0, 0), b.piece(3)!!.cells.single())
    }

    @Test
    fun goldIsCollectedWhenItReachesTheGround() {
        val b = Board.fromLevel(level(
            PieceDef(1, Material.GLASS, listOf(Cell(1, 0))),
            PieceDef(2, Material.GOLD, listOf(Cell(1, 1))),
        ))
        b.applyTool(ToolType.HAMMER, Cell(1, 0))
        assertTrue(b.goldCollected)
        assertTrue(b.pieces.isEmpty())
    }

    @Test
    fun hittingGoldLosesIt() {
        val b = Board.fromLevel(level(
            PieceDef(1, Material.WOOD, listOf(Cell(1, 0))),
            PieceDef(2, Material.GOLD, listOf(Cell(1, 1))),
        ))
        b.applyTool(ToolType.HAMMER, Cell(1, 1))
        assertFalse(b.goldCollected)
        assertTrue(b.piece(2) == null)
    }

    @Test
    fun generatorShocksNeighbours() {
        val b = Board.fromLevel(level(
            PieceDef(1, Material.GENERATOR, listOf(Cell(0, 0))),
            PieceDef(2, Material.WOOD, listOf(Cell(1, 0))),
            tools = mapOf(ToolType.DRILL to 1),
        ))
        b.applyTool(ToolType.DRILL, Cell(0, 0))
        assertTrue(b.pieces.isEmpty())
        assertTrue(b.charges[ToolType.DRILL] == 0)
    }

    @Test
    fun acUnitRefundsATurn() {
        val b = Board.fromLevel(level(
            PieceDef(1, Material.AC_UNIT, listOf(Cell(0, 0), Cell(1, 0))),
            PieceDef(2, Material.WOOD, listOf(Cell(3, 0))),
        ))
        b.applyTool(ToolType.HAMMER, Cell(0, 0))
        b.applyTool(ToolType.HAMMER, Cell(0, 0))
        assertEquals(1, b.turnsUsed)
    }

    @Test
    fun jsonRoundTrip() {
        val l = LevelGenerator.generate(1, 4)
        val back = LevelJson.decode(LevelJson.encode(l))
        assertEquals(l, back)
    }

    @Test
    fun shippedLevelsAreBeatable() {
        val dir = listOfNotNull(System.getProperty("topple.levels"), "../app/src/main/assets/levels")
            .map(::File).firstOrNull { it.isDirectory } ?: error("levels directory not found")
        val files = dir.listFiles { f -> f.name.endsWith(".json") }!!
        assertEquals(Campaign.cityCount * Campaign.LEVELS_PER_CITY, files.size)
        for (f in files) {
            val l = LevelJson.decode(f.readText())
            val r = Solver.bestSolve(l)
            assertNotNull("${f.name} unsolvable", r)
            assertTrue("${f.name} needs ${r!!.turns} > ${l.turns.three}", r.turns <= l.turns.three)
        }
    }
}
