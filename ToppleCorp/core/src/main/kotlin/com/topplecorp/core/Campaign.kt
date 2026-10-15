package com.topplecorp.core

data class City(
    val id: Int,
    val name: String,
    val tagline: String,
)

/**
 * Static campaign structure: 5 cities x 30 levels, and the order in which
 * tools, materials and special blocks are introduced.
 */
object Campaign {
    const val LEVELS_PER_CITY = 30

    val cities = listOf(
        City(0, "Maple Hollow", "Where every crew starts out"),
        City(1, "Rustport", "Brick warehouses by the bay"),
        City(2, "Neon Bay", "Steel towers that never sleep"),
        City(3, "Dune City", "Sun-baked high-rises"),
        City(4, "Sky Capital", "The tallest towers on Earth"),
    )

    val cityCount get() = cities.size

    fun globalIndex(city: Int, index: Int) = city * LEVELS_PER_CITY + index

    /** Global level index at which each tool becomes available. */
    val toolUnlock = linkedMapOf(
        ToolType.HAMMER to 0,
        ToolType.DRILL to 6,
        ToolType.DYNAMITE to 30,
        ToolType.LASER to 60,
        ToolType.WRECKING_BALL to 90,
    )

    /** Global level index at which each material / special first appears. */
    val materialUnlock = linkedMapOf(
        Material.WOOD to 0,
        Material.GLASS to 2,
        Material.GOLD to 3,
        Material.PLASTIC to 10,
        Material.GENERATOR to 15,
        Material.AC_UNIT to 22,
        Material.BRICK to 32,
        Material.STONE to 40,
        Material.TNT to 45,
        Material.METAL to 62,
        Material.WATER_TANK to 70,
        Material.STEEL to 95,
        Material.VAULT to 100,
    )

    fun toolsAt(global: Int) = toolUnlock.filter { it.value <= global }.keys
    fun materialsAt(global: Int) = materialUnlock.filter { it.value <= global }.keys

    /** Tutorial text for a level that introduces something new, or null. */
    fun introFor(global: Int): String? {
        toolUnlock.entries.firstOrNull { it.value == global && it.value > 0 }?.let { (t, _) ->
            return "New tool: ${t.displayName}! ${t.description}"
        }
        materialUnlock.entries.firstOrNull { it.value == global && it.value > 0 }?.let { (m, _) ->
            return "New block: ${m.displayName}. ${m.description}"
        }
        return when (global) {
            0 -> "Tap the Hammer, then tap a block. Destroy every block before you run out of turns!"
            1 -> "Blocks that lose their support fall and crush what's below. Knock out the bottom!"
            else -> null
        }
    }
}
