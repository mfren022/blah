package com.topplecorp.game.data

/** Store items. These are placeholders: they can be listed but are not purchasable yet. */
data class StoreItem(
    val id: String,
    val category: Category,
    val name: String,
    val price: Int,
    /** Preview colours for the card swatch. */
    val colors: List<Int>,
    val available: Boolean = false,
) {
    enum class Category(val title: String) { TILESETS("Tilesets"), CREW("Crew Skins") }
}

object StoreCatalog {
    val items = listOf(
        StoreItem("tiles_classic", StoreItem.Category.TILESETS, "Classic", 0,
            listOf(0xFFC07A3E.toInt(), 0xFF9A98A6.toInt(), 0xFF7D9BB8.toInt()), available = true),
        StoreItem("tiles_candy", StoreItem.Category.TILESETS, "Candy Land", 2500,
            listOf(0xFFFF8FC8.toInt(), 0xFFFFE07A.toInt(), 0xFF9AF0E0.toInt())),
        StoreItem("tiles_neon", StoreItem.Category.TILESETS, "Neon Grid", 4000,
            listOf(0xFF2A1E5C.toInt(), 0xFFFF4FD8.toInt(), 0xFF5CF2FF.toInt())),
        StoreItem("tiles_castle", StoreItem.Category.TILESETS, "Old Castle", 4000,
            listOf(0xFF7A7468.toInt(), 0xFF5C8A4A.toInt(), 0xFFB09060.toInt())),
        StoreItem("tiles_ice", StoreItem.Category.TILESETS, "Glacier", 6000,
            listOf(0xFFCFF2FF.toInt(), 0xFF7CC8F0.toInt(), 0xFFFFFFFF.toInt())),
        StoreItem("tiles_lava", StoreItem.Category.TILESETS, "Volcano", 8000,
            listOf(0xFF3A2626.toInt(), 0xFFFF5A1F.toInt(), 0xFFFFC93C.toInt())),

        StoreItem("crew_default", StoreItem.Category.CREW, "Topple Crew", 0,
            listOf(0xFFFFC93C.toInt(), 0xFFE8484F.toInt(), 0xFF4BA3F2.toInt()), available = true),
        StoreItem("crew_space", StoreItem.Category.CREW, "Astronauts", 3000,
            listOf(0xFFFFFFFF.toInt(), 0xFFFF8A3D.toInt(), 0xFF2A3A5C.toInt())),
        StoreItem("crew_pirate", StoreItem.Category.CREW, "Pirates", 3000,
            listOf(0xFF2B2B2B.toInt(), 0xFFE8484F.toInt(), 0xFFFFE89C.toInt())),
        StoreItem("crew_robot", StoreItem.Category.CREW, "Robots", 5000,
            listOf(0xFFA9B4C8.toInt(), 0xFF5CF2FF.toInt(), 0xFF54566E.toInt())),
        StoreItem("crew_chef", StoreItem.Category.CREW, "Chefs", 5000,
            listOf(0xFFFFFFFF.toInt(), 0xFFFF6FAE.toInt(), 0xFF8C6A55.toInt())),
        StoreItem("crew_ninja", StoreItem.Category.CREW, "Ninjas", 7500,
            listOf(0xFF1B1726.toInt(), 0xFF8A5CF0.toInt(), 0xFFE8484F.toInt())),
    )

    fun of(category: StoreItem.Category) = items.filter { it.category == category }
}
