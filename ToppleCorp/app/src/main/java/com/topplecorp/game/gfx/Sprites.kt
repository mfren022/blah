package com.topplecorp.game.gfx

import android.graphics.Bitmap
import com.topplecorp.core.Material
import com.topplecorp.core.ToolType

/** Hand-made pixel art. All sprites are built once from string art. */
object Sprites {

    // ---------------------------------------------------------------- Crew

    /** Palette for one crew member. Skins in the store can later swap these. */
    data class CrewLook(
        val name: String,
        val role: String,
        val helmet: Int,
        val helmetShade: Int,
        val skin: Int,
        val skinShade: Int,
        val shirt: Int,
        val shirtShade: Int,
        val stripe: Int,
        val pants: Int,
        val pantsShade: Int,
        val boots: Int,
        val hair: Int,
        val head: Int, // which head template
        val tool: ToolType,
    )

    val crewLooks = listOf(
        CrewLook("Bruno", "Foreman", 0xFFFFC93C.toInt(), 0xFFC7861C.toInt(), 0xFFF2B98A.toInt(), 0xFFC98A63.toInt(),
            0xFFFF8A3D.toInt(), 0xFFC2551E.toInt(), 0xFFFFF4DC.toInt(), 0xFF3F64B5.toInt(), 0xFF2A4380.toInt(),
            0xFF4A2F27.toInt(), 0xFF5A3A28.toInt(), 0, ToolType.HAMMER),
        CrewLook("Dyna", "Blaster", 0xFFE8484F.toInt(), 0xFF9E2A3A.toInt(), 0xFFD9946A.toInt(), 0xFFA8694A.toInt(),
            0xFF4E4B5E.toInt(), 0xFF34323F.toInt(), 0xFFFFC93C.toInt(), 0xFF5B4636.toInt(), 0xFF3E2F25.toInt(),
            0xFF241F1C.toInt(), 0xFFB8452B.toInt(), 1, ToolType.DYNAMITE),
        CrewLook("Gizmo", "Engineer", 0xFF4BA3F2.toInt(), 0xFF2A5FB0.toInt(), 0xFF8D5A3B.toInt(), 0xFF67402A.toInt(),
            0xFF5BD16B.toInt(), 0xFF2E8F4C.toInt(), 0xFFFFF4DC.toInt(), 0xFF46506B.toInt(), 0xFF2F364A.toInt(),
            0xFF2B2B2B.toInt(), 0xFF1E1A18.toInt(), 2, ToolType.DRILL),
    )

    private val HEADS = listOf(
        // Bruno: hard hat with brim and a moustache
        listOf(
            ".....HHHH.....",
            "....HHHHHH....",
            "...HHHhHHHH...",
            "..hhhhhhhhhh..",
            "....SSSSSS....",
            "....SESSES....",
            "....SSSSSS....",
            "....SRRRRS....",
            ".....ssss.....",
        ),
        // Dyna: helmet with goggles, ponytail
        listOf(
            ".....HHHH.....",
            "....HHHHHH....",
            "...HGGHHGGH...",
            "..hhhhhhhhhh..",
            "...RSSSSSS....",
            "..RRSESSES....",
            "..R.SSSSSS....",
            "..R.SSssSS....",
            ".....ssss.....",
        ),
        // Gizmo: cap with visor, glasses
        listOf(
            "..............",
            ".....HHHH.....",
            "....HHHHHH....",
            "....hhhhhhhh..",
            "....SSSSSS....",
            "....EEWEEW....",
            "....SSSSSS....",
            "....SSssSS....",
            ".....ssss.....",
        ),
    )

    private val BODY = listOf(
        "....VVWWVV....",
        "...VVVWWVVV...",
        "...VVVWWVVV...",
        "...VWWWWWWV...",
        "...VVVVVVVV...",
        "...PPPPPPPP...",
        "...PPP..PPP...",
        "...PPP..PPP...",
        "...ppp..ppp...",
        "..BBBB..BBBB..",
    )

    class Crew(val look: CrewLook, val body: Bitmap, val blink: Bitmap)

    fun buildCrew(look: CrewLook): Crew {
        val pal = mapOf(
            'H' to look.helmet, 'h' to look.helmetShade, 'S' to look.skin, 's' to look.skinShade,
            'E' to Pal.INK, 'V' to look.shirt, 'v' to look.shirtShade, 'W' to look.stripe,
            'P' to look.pants, 'p' to look.pantsShade, 'B' to look.boots, 'R' to look.hair,
            'G' to 0xFF9EE6FF.toInt(),
        )
        val rows = HEADS[look.head] + BODY
        val blinkRows = rows.map { if (it.contains('E') && look.head != 2) it.replace('E', 's') else it }
        return Crew(look, Sprite.build(rows, pal), Sprite.build(blinkRows, pal))
    }

    val crew: List<Crew> by lazy { crewLooks.map { buildCrew(it) } }

    // ---------------------------------------------------------------- Tools

    private val toolArt = mapOf(
        ToolType.HAMMER to listOf(
            "..GGGGGG....",
            ".GgggggGG...",
            ".GGGGGGGG...",
            "....WW......",
            "....WW......",
            "....WW......",
            "....WW......",
            "....ww......",
            "....WW......",
            "....ww......",
            "....WW......",
            "....ww......",
        ),
        ToolType.DRILL to listOf(
            "............",
            ".OOOOOO.....",
            "OOooooOGGg..",
            "OOOOOOOGGGgg",
            "OOooooOGGg..",
            ".OOOOOO.....",
            "..KK........",
            "..KK........",
            "..KKK.......",
            "..KKKK......",
            "..KKK.......",
            "............",
        ),
        ToolType.DYNAMITE to listOf(
            ".......y....",
            "......yY....",
            ".....K......",
            "....K.......",
            "..RRRRR.....",
            ".RrRRRRR....",
            ".RrRRRRR....",
            ".RCCCCCR....",
            ".RCCCCCR....",
            ".RrRRRRR....",
            ".RrRRRRR....",
            "..RRRRR.....",
        ),
        ToolType.LASER to listOf(
            ".....CC.....",
            ".....CC.....",
            ".....CC.....",
            "....BBBB....",
            "...BbbbbB...",
            "...BBBBBB...",
            "...BbCCbB...",
            "...BBBBBB...",
            "....GGGG....",
            "....GggG....",
            "....GGGG....",
            "...KKKKKK...",
        ),
        ToolType.WRECKING_BALL to listOf(
            "......K.....",
            "......K.....",
            "......K.....",
            "......K.....",
            "...KKKKKK...",
            "..KkkkkkKK..",
            ".KkKKKKKKKK.",
            ".KkKKKKKKKK.",
            ".KKKKKKKKKK.",
            ".KKKKKKKKKK.",
            "..KKKKKKKK..",
            "...KKKKKK...",
        ),
    )

    private val toolPal = mapOf(
        'G' to 0xFFA9B4C8.toInt(), 'g' to 0xFFE4EAF4.toInt(), 'W' to 0xFFB5733C.toInt(), 'w' to 0xFF8A5229.toInt(),
        'O' to 0xFFFF8A3D.toInt(), 'o' to 0xFFFFB27A.toInt(), 'K' to 0xFF3A3747.toInt(), 'k' to 0xFF6E6A85.toInt(),
        'R' to 0xFFE8484F.toInt(), 'r' to 0xFFFF8F8F.toInt(), 'C' to 0xFFFFF4DC.toInt(), 'Y' to 0xFFFFC93C.toInt(),
        'y' to 0xFFFFF27A.toInt(), 'B' to 0xFF8A5CF0.toInt(), 'b' to 0xFFBFA4FF.toInt(),
    )

    val tools: Map<ToolType, Bitmap> by lazy { toolArt.mapValues { Sprite.build(it.value, toolPal) } }

    // ---------------------------------------------------------------- UI icons

    private val iconPal = mapOf(
        'Y' to Pal.GOLD, 'y' to Pal.GOLD_DARK, 'W' to Pal.WHITE, 'K' to Pal.INK, 'G' to Pal.GRAY,
        'g' to Pal.GRAY_DARK, 'C' to Pal.CREAM, 'R' to Pal.RED, 'B' to Pal.BLUE, 'L' to 0xFFFFF8B0.toInt(),
    )

    private fun icon(vararg rows: String) = lazy { Sprite.build(rows.toList(), iconPal) }

    val star by icon(
        "....K....",
        "...KYK...",
        "...KYK...",
        "KKKKLYKKK",
        "KYYYLYYyK",
        ".KYYYYyK.",
        "..KYYYyK.",
        ".KYYKYYyK",
        ".KYK.KYyK",
        ".KK...KKK",
    )
    val starEmpty by icon(
        "....K....",
        "...KgK...",
        "...KgK...",
        "KKKKggKKK",
        "KgggggggK",
        ".KgggggK.",
        "..KgggK..",
        ".KggKggK.",
        ".KgK.KgK.",
        ".KK...KK.",
    )
    val coin by icon(
        "..KKKK..",
        ".KYYYYK.",
        "KYLYYyyK",
        "KYLYYyyK",
        "KYLYYyyK",
        "KYYYYyyK",
        ".KyyyyK.",
        "..KKKK..",
    )
    val goldBar by icon(
        "...KKKKKKK..",
        "..KLLYYYYK..",
        ".KLYYYYYyKK.",
        "KKKKKKKKKyyK",
        "KYYYYYYYKyK.",
        "KYYYYYYyKK..",
        "KKKKKKKKK...",
    )
    val goldBarGray by icon(
        "...KKKKKKK..",
        "..KWWGGGGK..",
        ".KWGGGGGgKK.",
        "KKKKKKKKKggK",
        "KGGGGGGGKgK.",
        "KGGGGGGgKK..",
        "KKKKKKKKK...",
    )
    val lock by icon(
        "..KKKK..",
        ".KGGGGK.",
        ".KG..GK.",
        "KKKKKKKK",
        "KYYYYYyK",
        "KYYKKYyK",
        "KYYKKYyK",
        "KyyyyyyK",
        "KKKKKKKK",
    )
    val pause by icon(
        "KK.KK",
        "WK.WK",
        "WK.WK",
        "WK.WK",
        "WK.WK",
        "WK.WK",
        "KK.KK",
    )
    val back by icon(
        "...K....",
        "..KW....",
        ".KWWKKKK",
        "KWWWWWWK",
        ".KWWKKKK",
        "..KW....",
        "...K....",
    )
    val play by icon(
        "KK....",
        "KWK...",
        "KWWK..",
        "KWWWK.",
        "KWWK..",
        "KWK...",
        "KK....",
    )
    val retry by icon(
        "..KKKK.K",
        ".KWWWWKW",
        "KWK..KWW",
        "KW...WWW",
        "KW......",
        "KWK..KK.",
        ".KWWWW..",
        "..KKKK..",
    )
    val gear by icon(
        "....KKKKK....",
        ".KK.KWWWK.KK.",
        "KWWKKWWWKKWWK",
        "KWWWWWWWWWWGK",
        ".KWWWKKKWWGK.",
        "KKWWKK.KKWGKK",
        "KWWWK...KWGGK",
        "KKWWKK.KKGGKK",
        ".KWWGKKKGGGK.",
        "KWWGGGGGGGGGK",
        "KWGKKGGGKKGGK",
        ".KK.KGGGK.KK.",
        "....KKKKK....",
    )
    val cart by icon(
        "KK.......",
        ".W.......",
        ".WWWWWWWW",
        ".WWWWWWW.",
        ".WWWWWW..",
        ".W.......",
        ".WWWWWWW.",
        "..K...K..",
    )
    val sound by icon(
        "...K....",
        "..KW..W.",
        "KKWW.W..",
        "KWWW.W.W",
        "KKWW.W..",
        "..KW..W.",
        "...K....",
    )
    val mute by icon(
        "...K....",
        "..KW....",
        "KKWW.R.R",
        "KWWW..R.",
        "KKWW.R.R",
        "..KW....",
        "...K....",
    )
    val check by icon(
        ".......K",
        "......KW",
        ".....KW.",
        "K...KW..",
        "WK.KW...",
        ".WKW....",
        "..W.....",
    )
    val list by icon(
        "WW.WWWWW",
        "........",
        "WW.WWWWW",
        "........",
        "WW.WWWWW",
    )
    val skip by icon(
        "K..K..",
        "WK.WK.",
        "WWKWWK",
        "WWWWWW",
        "WWKWWK",
        "WK.WK.",
        "K..K..",
    )

    // ---------------------------------------------------------------- Special block icons

    private val specialPal = mapOf(
        'Y' to Pal.GOLD, 'y' to Pal.GOLD_DARK, 'W' to Pal.WHITE, 'K' to Pal.INK, 'G' to 0xFFB9C2D6.toInt(),
        'g' to 0xFF6E7891.toInt(), 'B' to 0xFF6FC8FF.toInt(), 'b' to 0xFF2A7FC9.toInt(), 'R' to Pal.RED,
        'r' to Pal.RED_DARK, 'L' to 0xFFFFFAA0.toInt(),
    )

    private val specialArt = mapOf(
        Material.GENERATOR to listOf(
            "....KKK.",
            "...KLLK.",
            "..KLLK..",
            ".KLLLLK.",
            "KKKLLK..",
            "..KLK...",
            ".KLK....",
            ".KK.....",
        ),
        Material.AC_UNIT to listOf(
            ".KKKKKK.",
            "KgGGGGgK",
            "KG.GG.GK",
            "KGG..GGK",
            "KGG..GGK",
            "KG.GG.GK",
            "KgGGGGgK",
            ".KKKKKK.",
        ),
        Material.WATER_TANK to listOf(
            "...K....",
            "..KBK...",
            "..KBK...",
            ".KBBBK..",
            "KBBWBBK.",
            "KBWBBbK.",
            "KBBBbbK.",
            ".KKKKK..",
        ),
        Material.TNT to listOf(
            "KKKKKKKKKKK",
            "KWWWKWKWWWK",
            "KKWKKWWKWKK",
            "KKWKKWKWWKK",
            "KKWKKWKKWKK",
            "KKKKKKKKKKK",
        ),
        Material.VAULT to listOf(
            "..KKKK..",
            ".KGGGGK.",
            "KGgKKgGK",
            "KGKYYKGK",
            "KGKYYKGK",
            "KGgKKgGK",
            ".KGGGGK.",
            "..KKKK..",
        ),
        Material.GOLD to listOf(
            "........",
            "..L.....",
            ".LWL....",
            "..L.....",
            "........",
            ".....L..",
            "....LWL.",
            ".....L..",
        ),
    )

    val specials: Map<Material, Bitmap> by lazy { specialArt.mapValues { Sprite.build(it.value, specialPal) } }
}
