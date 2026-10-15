package com.topplecorp.core.tools

import com.topplecorp.core.Campaign
import com.topplecorp.core.LevelGenerator
import com.topplecorp.core.LevelJson
import java.io.File

/** Writes every campaign level to `<outDir>/c<city>_l<level>.json`. */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "levels")
    out.mkdirs()
    val only = args.getOrNull(1)?.toIntOrNull()
    for (city in 0 until Campaign.cityCount) {
        if (only != null && only != city) continue
        for (i in 0 until Campaign.LEVELS_PER_CITY) {
            val t0 = System.currentTimeMillis()
            val level = LevelGenerator.generate(city, i)
            File(out, "${level.key}.json").writeText(LevelJson.encode(level))
            println("${level.name}: ${level.width}x${level.height} pieces=${level.pieces.size} " +
                "turns=${level.turns} tools=${level.tools} (${System.currentTimeMillis() - t0} ms)")
        }
    }
}
