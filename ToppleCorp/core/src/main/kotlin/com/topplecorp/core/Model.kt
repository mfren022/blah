package com.topplecorp.core

/**
 * What happens when a block of this material is destroyed.
 */
enum class SpecialEffect {
    NONE,
    /** Gold collectible: collected when it reaches the ground intact. */
    GOLD,
    /** Electric discharge damaging every touching piece. */
    SHOCK,
    /** Refunds one turn to the player. */
    BONUS_TURN,
    /** Floods down, damaging every piece below in its columns. */
    FLOOD,
    /** Explodes, damaging every piece within a radius. */
    EXPLODE,
    /** Pays bonus gold at the end of a successful level. */
    BONUS_GOLD,
}

/**
 * Block materials. Ordered roughly by the point they are introduced in the campaign.
 * [weight] is how hard the block hits whatever it lands on.
 */
enum class Material(
    val displayName: String,
    val hp: Int,
    val weight: Int,
    val hammerImmune: Boolean = false,
    /** Shatters on any fall. */
    val fragile: Boolean = false,
    /** Takes no damage from falling. */
    val bouncy: Boolean = false,
    val effect: SpecialEffect = SpecialEffect.NONE,
    val isSpecial: Boolean = false,
    val description: String,
) {
    GLASS("Glass", 1, 1, fragile = true, description = "Shatters when it falls."),
    WOOD("Wood", 2, 2, description = "A sturdy plank."),
    PLASTIC("Plastic", 2, 1, bouncy = true, description = "Bouncy. Takes no fall damage."),
    BRICK("Brick", 3, 3, description = "Heavy and solid."),
    STONE("Stone", 4, 4, description = "Very heavy. Crushes what lies below."),
    METAL("Metal", 5, 5, hammerImmune = true, description = "Hammers can't dent it."),
    STEEL("Steel", 7, 6, hammerImmune = true, description = "Reinforced. Only power tools and big falls."),

    GOLD("Gold Block", 1, 2, bouncy = true, effect = SpecialEffect.GOLD, isSpecial = true,
        description = "Bring it down to your crew in one piece for 500 gold. Don't hit or crush it!"),
    GENERATOR("Generator", 3, 4, effect = SpecialEffect.SHOCK, isSpecial = true,
        description = "Discharges into every touching block when destroyed."),
    AC_UNIT("AC Unit", 2, 3, effect = SpecialEffect.BONUS_TURN, isSpecial = true,
        description = "Destroy it to cool down: +1 turn."),
    WATER_TANK("Water Tank", 2, 3, effect = SpecialEffect.FLOOD, isSpecial = true,
        description = "Floods everything below it."),
    TNT("TNT Crate", 1, 2, effect = SpecialEffect.EXPLODE, isSpecial = true,
        description = "Explodes, hurting everything nearby."),
    VAULT("Vault", 5, 5, hammerImmune = true, effect = SpecialEffect.BONUS_GOLD, isSpecial = true,
        description = "Crack it open for +100 gold."),
    ;

    companion object {
        fun parse(name: String): Material = valueOf(name.uppercase())
    }
}

enum class ToolType(
    val displayName: String,
    val description: String,
) {
    HAMMER("Hammer", "1 damage to a block. Useless on metal."),
    DRILL("Drill", "3 damage to a block."),
    DYNAMITE("Dynamite", "2 damage to a block and everything touching it."),
    LASER("Laser", "1 damage to every block in a column."),
    WRECKING_BALL("Wrecking Ball", "2 damage to every block in a row."),
    ;

    /** Damage this tool deals to a block of [material] it hits. */
    fun damageAgainst(material: Material): Int = when (this) {
        HAMMER -> if (material.hammerImmune) 0 else 1
        DRILL -> 3
        DYNAMITE -> 2
        LASER -> 1
        WRECKING_BALL -> 2
    }

    companion object {
        fun parse(name: String): ToolType = valueOf(name.uppercase())
    }
}

/** A cell coordinate. y = 0 is the ground row, increasing upward. */
data class Cell(val x: Int, val y: Int)

/** A rigid polyomino piece. Cells are absolute board coordinates. */
data class PieceDef(
    val id: Int,
    val material: Material,
    val cells: List<Cell>,
)

/** Number of turns allowed for each star rating. three <= two <= max. */
data class TurnLimits(val three: Int, val two: Int, val max: Int) {
    fun starsFor(turnsUsed: Int): Int = when {
        turnsUsed <= three -> 3
        turnsUsed <= two -> 2
        turnsUsed <= max -> 1
        else -> 0
    }
}

/**
 * Complete, serializable level description. This is the unit the (future) level editor edits.
 * Tool charges: -1 = unlimited.
 */
data class LevelDef(
    val city: Int,
    val index: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val pieces: List<PieceDef>,
    val tools: Map<ToolType, Int>,
    val turns: TurnLimits,
    /** Optional tutorial line shown before the level starts. */
    val hint: String? = null,
    val seed: Long = 0,
) {
    val key: String get() = levelKey(city, index)

    companion object {
        fun levelKey(city: Int, index: Int) = "c${city + 1}_l${index + 1}"
    }
}
