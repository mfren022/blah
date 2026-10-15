package com.topplecorp.game.data

import android.content.Context
import com.topplecorp.core.LevelDef
import com.topplecorp.core.LevelGenerator
import com.topplecorp.core.LevelJson
import java.io.File

/**
 * Loads campaign levels. Lookup order:
 *  1. A user override in `files/levels/<key>.json` (written by the level editor).
 *  2. The shipped level in `assets/levels/<key>.json`.
 *  3. Procedural generation as a last resort.
 */
class LevelRepository(private val context: Context) {
    private val cache = HashMap<String, LevelDef>()
    private val overrideDir get() = File(context.filesDir, "levels").apply { mkdirs() }

    fun load(city: Int, index: Int): LevelDef {
        val key = LevelDef.levelKey(city, index)
        cache[key]?.let { return it }
        val level = readOverride(key) ?: readAsset(key) ?: LevelGenerator.generate(city, index)
        cache[key] = level
        return level
    }

    private fun readOverride(key: String): LevelDef? = runCatching {
        val f = File(overrideDir, "$key.json")
        if (f.exists()) LevelJson.decode(f.readText()) else null
    }.getOrNull()

    private fun readAsset(key: String): LevelDef? = runCatching {
        context.assets.open("levels/$key.json").bufferedReader().use { LevelJson.decode(it.readText()) }
    }.getOrNull()

    // ---- Editor hooks

    /** Saves an edited level; it will be used instead of the shipped version. */
    fun saveOverride(level: LevelDef) {
        File(overrideDir, "${level.key}.json").writeText(LevelJson.encode(level))
        cache[level.key] = level
    }

    fun hasOverride(city: Int, index: Int) = File(overrideDir, "${LevelDef.levelKey(city, index)}.json").exists()

    /** Reverts a level to the shipped version. */
    fun clearOverride(city: Int, index: Int) {
        val key = LevelDef.levelKey(city, index)
        File(overrideDir, "$key.json").delete()
        cache.remove(key)
    }
}
