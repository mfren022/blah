package com.topplecorp.game.data

import android.content.Context
import com.topplecorp.core.Campaign
import com.topplecorp.core.LevelDef

/** Player progress and settings, persisted in SharedPreferences. */
class SaveData(context: Context) {
    private val prefs = context.getSharedPreferences("topple_corp_save", Context.MODE_PRIVATE)

    var gold: Int
        get() = prefs.getInt("gold", 0)
        set(v) = prefs.edit().putInt("gold", v).apply()

    var soundOn: Boolean
        get() = prefs.getBoolean("sound_on", true)
        set(v) = prefs.edit().putBoolean("sound_on", v).apply()

    var volume: Float
        get() = prefs.getFloat("volume", 0.8f)
        set(v) = prefs.edit().putFloat("volume", v).apply()

    var vibration: Boolean
        get() = prefs.getBoolean("vibration", true)
        set(v) = prefs.edit().putBoolean("vibration", v).apply()

    var showHints: Boolean
        get() = prefs.getBoolean("hints", true)
        set(v) = prefs.edit().putBoolean("hints", v).apply()

    var lastCity: Int
        get() = prefs.getInt("last_city", 0)
        set(v) = prefs.edit().putInt("last_city", v).apply()

    fun stars(city: Int, index: Int): Int = prefs.getInt("stars_" + LevelDef.levelKey(city, index), 0)

    fun goldBlock(city: Int, index: Int): Boolean = prefs.getBoolean("goldblock_" + LevelDef.levelKey(city, index), false)

    fun cityStars(city: Int) = (0 until Campaign.LEVELS_PER_CITY).sumOf { stars(city, it) }

    fun cityCleared(city: Int) = (0 until Campaign.LEVELS_PER_CITY).count { stars(city, it) > 0 }

    fun isCityUnlocked(city: Int) = city == 0 || cityCleared(city - 1) == Campaign.LEVELS_PER_CITY

    fun isLevelUnlocked(city: Int, index: Int) =
        isCityUnlocked(city) && (index == 0 || stars(city, index - 1) > 0)

    data class Reward(
        val starGold: Int,
        val goldBlockGold: Int,
        val bonusGold: Int,
        val newBest: Boolean,
        val firstGoldBlock: Boolean,
    ) {
        val total get() = starGold + goldBlockGold + bonusGold
    }

    /**
     * Records a completed level and pays out. Star gold is paid up to the best tier reached
     * (1 star = 200, 2 = 300, 3 = 500), so replaying for more stars pays the difference.
     * The gold block pays 500 once per level; vault bonuses pay on the first clear.
     */
    fun completeLevel(city: Int, index: Int, stars: Int, goldBlock: Boolean, vaultGold: Int): Reward {
        val key = LevelDef.levelKey(city, index)
        val prev = stars(city, index)
        val firstClear = prev == 0
        val starGold = (tierGold(stars) - tierGold(prev)).coerceAtLeast(0)
        val firstGoldBlock = goldBlock && !goldBlock(city, index)
        val gbGold = if (firstGoldBlock) GOLD_BLOCK_REWARD else 0
        val bonus = if (firstClear) vaultGold else 0
        val e = prefs.edit()
        if (stars > prev) e.putInt("stars_$key", stars)
        if (firstGoldBlock) e.putBoolean("goldblock_$key", true)
        e.putInt("gold", gold + starGold + gbGold + bonus)
        e.apply()
        return Reward(starGold, gbGold, bonus, stars > prev, firstGoldBlock)
    }

    // ---- Store

    fun owns(itemId: String) = prefs.getBoolean("own_$itemId", false)

    fun equipped(slot: String): String? = prefs.getString("equip_$slot", null)

    fun equip(slot: String, itemId: String) = prefs.edit().putString("equip_$slot", itemId).apply()

    fun buy(itemId: String, price: Int): Boolean {
        if (owns(itemId) || gold < price) return false
        prefs.edit().putInt("gold", gold - price).putBoolean("own_$itemId", true).apply()
        return true
    }

    fun resetProgress() {
        val keep = mapOf("sound_on" to soundOn, "vibration" to vibration, "hints" to showHints)
        val vol = volume
        prefs.edit().clear().apply()
        prefs.edit().apply {
            keep.forEach { (k, v) -> putBoolean(k, v) }
            putFloat("volume", vol)
        }.apply()
    }

    companion object {
        const val GOLD_BLOCK_REWARD = 500

        fun tierGold(stars: Int) = when (stars) {
            1 -> 200
            2 -> 300
            3 -> 500
            else -> 0
        }
    }
}
