package com.ultimatesudoku.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.ultimatesudoku.app.ui.Palette
import com.ultimatesudoku.app.ui.fitSystemBars

/** Applies the light or dark theme from settings and wires the shared top bar. */
abstract class BaseActivity : Activity() {
    protected val app: SudokuApp get() = application as SudokuApp
    protected lateinit var palette: Palette
        private set
    private var darkTheme = false

    override fun onCreate(savedInstanceState: Bundle?) {
        darkTheme = app.settings.isDark(this)
        setTheme(if (darkTheme) R.style.Theme_Sudoku_Dark else R.style.Theme_Sudoku)
        super.onCreate(savedInstanceState)
        palette = Palette(this)
    }

    override fun onResume() {
        super.onResume()
        // The theme may have been changed in Settings while this screen was in the back stack.
        if (app.settings.isDark(this) != darkTheme) recreate()
    }

    /** Sets the content, keeps it clear of the system bars and fills in the standard top bar. */
    protected fun setScreen(layout: Int, title: String? = null) {
        setContentView(layout)
        fitSystemBars(findViewById(R.id.root))
        if (title != null) findViewById<TextView>(R.id.title).text = title
        findViewById<android.view.View>(R.id.back)?.setOnClickListener { finish() }
    }
}
