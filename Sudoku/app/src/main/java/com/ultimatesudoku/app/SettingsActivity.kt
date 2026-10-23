package com.ultimatesudoku.app

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.ultimatesudoku.app.data.Settings
import com.ultimatesudoku.app.ui.Dialogs
import com.ultimatesudoku.app.ui.SectionHeader
import com.ultimatesudoku.app.ui.SegmentedView
import com.ultimatesudoku.app.ui.SquareToggle
import com.ultimatesudoku.app.ui.dp
import kotlin.reflect.KMutableProperty0

class SettingsActivity : BaseActivity() {

    private lateinit var list: LinearLayout
    private var limitRow: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreen(R.layout.activity_settings, getString(R.string.settings_title))
        list = findViewById(R.id.list)
        val s = app.settings

        section(R.string.settings_appearance)
        list.addView(
            SegmentedView(this).apply {
                options = listOf(
                    getString(R.string.settings_theme_system),
                    getString(R.string.settings_theme_light),
                    getString(R.string.settings_theme_dark),
                )
                selectedIndex = s.theme.ordinal
                onSelect = {
                    s.theme = Settings.Theme.entries[it]
                    recreate()
                }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) },
        )

        section(R.string.settings_gameplay)
        toggle(R.string.settings_check, R.string.settings_check_summary, s::checkMistakes) { updateLimitRow() }
        limitRow = toggle(R.string.settings_limit, R.string.settings_limit_summary, s::mistakeLimit)
        toggle(R.string.settings_auto_notes, R.string.settings_auto_notes_summary, s::autoRemoveNotes)
        updateLimitRow()

        section(R.string.settings_assists)
        toggle(R.string.settings_peers, R.string.settings_peers_summary, s::highlightPeers)
        toggle(R.string.settings_same, R.string.settings_same_summary, s::highlightSame)
        toggle(R.string.settings_hide_finished, R.string.settings_hide_finished_summary, s::hideFinished)

        section(R.string.settings_general)
        toggle(R.string.settings_timer, null, s::showTimer)
        toggle(R.string.settings_screen_on, R.string.settings_screen_on_summary, s::keepScreenOn)
        toggle(R.string.settings_haptics, R.string.settings_haptics_summary, s::haptics)

        section(R.string.settings_about)
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        list.addView(
            TextView(this, null, 0, R.style.Text_Body_Muted).apply {
                text = getString(R.string.settings_about_text, version)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) },
        )
        list.addView(
            TextView(this, null, 0, R.style.Button_Text).apply {
                text = getString(R.string.settings_font)
                isAllCaps = false
                setPadding(0, 0, 0, 0)
                setOnClickListener { showFontLicense() }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) },
        )
    }

    private fun section(title: Int) {
        list.addView(
            SectionHeader(this).apply { setText(title) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(28) },
        )
    }

    private fun toggle(
        title: Int,
        summary: Int?,
        property: KMutableProperty0<Boolean>,
        onChange: () -> Unit = {},
    ): View {
        val row = layoutInflater.inflate(R.layout.item_setting, list, false)
        row.findViewById<TextView>(R.id.name).setText(title)
        row.findViewById<TextView>(R.id.summary).apply {
            if (summary == null) visibility = View.GONE else setText(summary)
        }
        val toggle = row.findViewById<SquareToggle>(R.id.toggle)
        toggle.setChecked(property.get())
        toggle.contentDescription = getString(title)
        toggle.onChange = {
            property.set(it)
            onChange()
        }
        row.setOnClickListener { if (toggle.isEnabled) toggle.setChecked(!toggle.isChecked, animate = true, notify = true) }
        list.addView(row)
        return row
    }

    private fun updateLimitRow() {
        val row = limitRow ?: return
        val enabled = app.settings.checkMistakes
        row.isEnabled = enabled
        row.alpha = if (enabled) 1f else 0.45f
        row.findViewById<SquareToggle>(R.id.toggle).isEnabled = enabled
    }

    private fun showFontLicense() {
        val text = assets.open("licenses/roboto-OFL.txt").bufferedReader().use { it.readText() }
        Dialogs.show(this, "Roboto", text, listOf(Dialogs.Action(getString(R.string.ok), primary = true)))
    }
}
