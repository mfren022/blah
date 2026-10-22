package com.ultimatesudoku.app

import android.app.Application
import android.content.Context
import com.ultimatesudoku.app.data.GameStore
import com.ultimatesudoku.app.data.Settings
import com.ultimatesudoku.app.data.Stats

class SudokuApp : Application() {
    lateinit var settings: Settings
        private set
    lateinit var stats: Stats
        private set
    lateinit var store: GameStore
        private set

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("sudoku", Context.MODE_PRIVATE)
        settings = Settings(prefs)
        stats = Stats(prefs)
        store = GameStore(prefs, stats)
    }
}
