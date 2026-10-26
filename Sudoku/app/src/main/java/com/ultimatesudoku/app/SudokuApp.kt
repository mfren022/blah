package com.ultimatesudoku.app

import android.app.Application
import android.content.Context
import com.ultimatesudoku.app.data.GameStore
import com.ultimatesudoku.app.data.History
import com.ultimatesudoku.app.data.Settings
import com.ultimatesudoku.app.data.Stats
import com.ultimatesudoku.app.data.StoryProgress
import com.ultimatesudoku.app.data.StrategyProgress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SudokuApp : Application() {
    lateinit var settings: Settings
        private set
    lateinit var stats: Stats
        private set
    lateinit var store: GameStore
        private set
    lateinit var history: History
        private set
    lateinit var story: StoryProgress
        private set
    lateinit var strategies: StrategyProgress
        private set

    /** One background thread for disk work, so writes happen in order. */
    val io: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "disk-io").apply { isDaemon = true }
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("sudoku", Context.MODE_PRIVATE)
        settings = Settings(prefs)
        stats = Stats(prefs)
        store = GameStore(prefs, stats)
        history = History(this)
        story = StoryProgress(prefs)
        strategies = StrategyProgress(prefs)
    }
}
