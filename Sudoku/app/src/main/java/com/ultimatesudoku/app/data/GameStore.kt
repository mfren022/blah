package com.ultimatesudoku.app.data

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.ultimatesudoku.core.Game
import com.ultimatesudoku.core.GameMode
import java.util.concurrent.Executors

/**
 * Saved games and puzzle generation. There are two save slots so a daily challenge in progress
 * survives starting a random game, and vice versa.
 */
class GameStore(private val prefs: SharedPreferences, private val stats: Stats) {

    enum class Slot { MAIN, DAILY }

    private val loaded = HashMap<Slot, Game?>()
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "puzzle-generator").apply { isDaemon = true }
    }
    private var listener: ((Game) -> Unit)? = null

    /** Identifies the latest request; results of superseded requests are dropped. */
    private var request = 0

    /** A puzzle is being generated for [generatingSlot]. */
    var isGenerating = false
        private set
    var generatingSlot = Slot.MAIN
        private set

    fun game(slot: Slot): Game? = loaded.getOrPut(slot) {
        prefs.getString(key(slot), null)?.let { Game.deserialize(it) }
    }

    fun save(slot: Slot, game: Game) {
        loaded[slot] = game
        prefs.edit().putString(key(slot), game.serialize()).apply()
    }

    fun clear(slot: Slot) {
        loaded[slot] = null
        prefs.edit().remove(key(slot)).apply()
    }

    /**
     * Builds a new game off the main thread with [make], saves it in [slot] and hands it to the
     * current listener. A game still in progress in that slot counts as abandoned. Starting another
     * generation before this one finishes replaces it.
     */
    fun generate(slot: Slot, make: () -> Game, onReady: (Game) -> Unit) {
        val id = ++request
        isGenerating = true
        generatingSlot = slot
        listener = onReady
        worker.execute {
            val game = make()
            main.post {
                if (id != request) return@post
                isGenerating = false
                start(slot, game)
                val callback = listener
                listener = null
                callback?.invoke(game)
            }
        }
    }

    /** Saves [game] as a freshly started game in [slot] and updates the statistics. */
    fun start(slot: Slot, game: Game) {
        val previous = game(slot)
        if (previous != null && previous.isPlaying && previous.mode != GameMode.CUSTOM && slot == Slot.MAIN) {
            stats.lost(previous.difficulty)
        }
        if (game.mode != GameMode.CUSTOM) stats.started(game.difficulty)
        save(slot, game)
    }

    /** Re-attaches a recreated screen to a generation still in progress. */
    fun listen(onReady: (Game) -> Unit) {
        listener = onReady
    }

    fun stopListening() {
        listener = null
    }

    private fun key(slot: Slot) = "game.${slot.name.lowercase()}"
}
