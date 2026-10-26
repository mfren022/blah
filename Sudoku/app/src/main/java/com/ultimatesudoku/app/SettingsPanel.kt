package com.ultimatesudoku.app

import android.app.Activity
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

/**
 * The settings, filled into [list]: theme, gameplay, assists and general switches, and about. Each switch
 * saves as it's flipped; a new theme recreates [activity] to show it.
 */
class SettingsPanel(private val activity: Activity, private val list: LinearLayout) {

    private val settings = (activity.application as SudokuApp).settings
    private var limitRow: View? = null

    fun build() {
        list.removeAllViews()
        section(R.string.settings_appearance)
        list.addView(
            SegmentedView(activity).apply {
                options = listOf(
                    activity.getString(R.string.settings_theme_system),
                    activity.getString(R.string.settings_theme_light),
                    activity.getString(R.string.settings_theme_dark),
                )
                selectedIndex = settings.theme.ordinal
                onSelect = {
                    settings.theme = Settings.Theme.entries[it]
                    activity.recreate()
                }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = activity.dp(8) },
        )

        section(R.string.settings_gameplay)
        toggle(R.string.settings_check, R.string.settings_check_summary, settings::checkMistakes) { updateLimitRow() }
        limitRow = toggle(R.string.settings_limit, R.string.settings_limit_summary, settings::mistakeLimit)
        toggle(R.string.settings_auto_notes, R.string.settings_auto_notes_summary, settings::autoRemoveNotes)
        updateLimitRow()

        section(R.string.settings_assists)
        toggle(R.string.settings_peers, R.string.settings_peers_summary, settings::highlightPeers)
        toggle(R.string.settings_same, R.string.settings_same_summary, settings::highlightSame)
        toggle(R.string.settings_hide_finished, R.string.settings_hide_finished_summary, settings::hideFinished)

        section(R.string.settings_general)
        toggle(R.string.settings_timer, null, settings::showTimer)
        toggle(R.string.settings_screen_on, R.string.settings_screen_on_summary, settings::keepScreenOn)
        toggle(R.string.settings_haptics, R.string.settings_haptics_summary, settings::haptics)

        section(R.string.settings_about)
        val version = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: ""
        list.addView(
            TextView(activity, null, 0, R.style.Text_Body_Muted).apply {
                text = activity.getString(R.string.settings_about_text, version)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = activity.dp(8) },
        )
        list.addView(
            TextView(activity, null, 0, R.style.Button_Text).apply {
                text = activity.getString(R.string.settings_font)
                isAllCaps = false
                setPadding(0, 0, 0, 0)
                setOnClickListener { showFontLicense() }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = activity.dp(8) },
        )
    }

    private fun section(title: Int) {
        list.addView(
            SectionHeader(activity).apply { setText(title) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = activity.dp(28) },
        )
    }

    private fun toggle(
        title: Int,
        summary: Int?,
        property: KMutableProperty0<Boolean>,
        onChange: () -> Unit = {},
    ): View {
        val row = activity.layoutInflater.inflate(R.layout.item_setting, list, false)
        row.findViewById<TextView>(R.id.name).setText(title)
        row.findViewById<TextView>(R.id.summary).apply {
            if (summary == null) visibility = View.GONE else setText(summary)
        }
        val toggle = row.findViewById<SquareToggle>(R.id.toggle)
        toggle.setChecked(property.get())
        toggle.contentDescription = activity.getString(title)
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
        val enabled = settings.checkMistakes
        row.isEnabled = enabled
        row.alpha = if (enabled) 1f else 0.45f
        row.findViewById<SquareToggle>(R.id.toggle).isEnabled = enabled
    }

    private fun showFontLicense() {
        val text = activity.assets.open("licenses/roboto-OFL.txt").bufferedReader().use { it.readText() }
        Dialogs.show(activity, "Roboto", text, listOf(Dialogs.Action(activity.getString(R.string.ok), primary = true)))
    }
}
