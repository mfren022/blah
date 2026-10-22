package com.ultimatesudoku.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import com.ultimatesudoku.app.ui.Dialogs
import com.ultimatesudoku.app.ui.MiniBoardView
import com.ultimatesudoku.app.ui.SegmentedView
import com.ultimatesudoku.app.ui.Snack
import com.ultimatesudoku.app.ui.formatTime
import com.ultimatesudoku.app.ui.modeName
import com.ultimatesudoku.core.GameMode
import com.ultimatesudoku.core.SolvedBoard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Every completed board, newest first, with its starting combination and solve time. */
class HistoryActivity : BaseActivity() {

    private var filter = 0
    private var boards: List<SolvedBoard> = emptyList()
    private val adapter = BoardsAdapter()
    private val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreen(R.layout.activity_history, getString(R.string.history_title))
        filter = savedInstanceState?.getInt(STATE_FILTER) ?: 0

        val list = findViewById<ListView>(R.id.list)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            startActivity(SolvedBoardActivity.open(this, boards[position].id))
        }
        findViewById<SegmentedView>(R.id.filter).apply {
            options = listOf(
                getString(R.string.history_all),
                getString(R.string.history_random),
                getString(R.string.history_daily),
                getString(R.string.history_custom),
            )
            selectedIndex = filter
            onSelect = {
                filter = it
                load()
            }
        }
        findViewById<View>(R.id.more).apply {
            visibility = View.VISIBLE
            setOnClickListener { showMenu() }
        }
        label(R.id.summary_count, R.string.history_solved)
        label(R.id.summary_total, R.string.history_total)
        label(R.id.summary_average, R.string.history_average)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_FILTER, filter)
    }

    private fun load() {
        val mode = FILTERS[filter]
        app.io.execute {
            val loaded = app.history.all(mode)
            runOnUiThread {
                if (isDestroyed || mode != FILTERS[filter]) return@runOnUiThread
                boards = loaded
                adapter.notifyDataSetChanged()
                renderSummary()
                // Only now, so "no boards yet" doesn't flash while the list loads.
                findViewById<ListView>(R.id.list).emptyView = findViewById(R.id.empty)
            }
        }
    }

    private fun renderSummary() {
        val total = boards.sumOf { it.timeMs }
        value(R.id.summary_count, boards.size.toString())
        value(R.id.summary_total, formatTime(total))
        value(R.id.summary_average, if (boards.isEmpty()) "—" else formatTime(total / boards.size))
    }

    private fun label(tile: Int, text: Int) {
        findViewById<View>(tile).findViewById<TextView>(R.id.label).setText(text)
    }

    private fun value(tile: Int, text: String) {
        findViewById<View>(tile).findViewById<TextView>(R.id.value).text = text
    }

    private fun showMenu() {
        val items = ArrayList<Pair<String, () -> Unit>>()
        if (boards.isNotEmpty()) {
            items += getString(R.string.history_menu_copy) to { copyCsv() }
            items += getString(R.string.history_menu_share) to { shareCsv() }
            items += getString(R.string.history_menu_clear) to { confirmClear() }
        }
        if (items.isEmpty()) return
        Dialogs.menu(this, getString(R.string.history_title), items)
    }

    /** The shown boards as CSV, oldest first, keeping to [MAX_EXPORT] characters for the clipboard and share sheet. */
    private fun exportCsv(): Pair<String, Int> {
        var kept = boards
        var csv = SolvedBoard.toCsv(kept.reversed())
        while (csv.length > MAX_EXPORT && kept.size > 1) {
            kept = kept.take(kept.size * 3 / 4)
            csv = SolvedBoard.toCsv(kept.reversed())
        }
        if (kept.size < boards.size) {
            Snack.show(this, getString(R.string.history_truncated, resources.getQuantityString(R.plurals.boards, kept.size, kept.size)))
        }
        return csv to kept.size
    }

    private fun copyCsv() {
        val (csv, count) = exportCsv()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.history_share_subject), csv))
        Snack.show(this, getString(R.string.history_copied, resources.getQuantityString(R.plurals.boards, count, count)))
    }

    private fun shareCsv() {
        val (csv, _) = exportCsv()
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.history_share_subject))
            .putExtra(Intent.EXTRA_TEXT, csv)
        startActivity(Intent.createChooser(send, getString(R.string.history_menu_share)))
    }

    private fun confirmClear() {
        Dialogs.show(
            this,
            getString(R.string.history_clear_title),
            getString(R.string.history_clear_message),
            listOf(
                Dialogs.Action(getString(R.string.history_clear_confirm), primary = true) {
                    app.io.execute {
                        app.history.clear()
                        runOnUiThread { load() }
                    }
                },
                Dialogs.Action(getString(R.string.cancel)),
            ),
        )
    }

    private inner class BoardsAdapter : BaseAdapter() {
        override fun getCount() = boards.size
        override fun getItem(position: Int) = boards[position]
        override fun getItemId(position: Int) = boards[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: layoutInflater.inflate(R.layout.item_solved, parent, false)
            val board = boards[position]
            row.findViewById<MiniBoardView>(R.id.mini).givens = board.givens
            row.findViewById<TextView>(R.id.title).text =
                "${board.difficulty.title} · ${modeName(board.mode, board.tag)}"
            val date = Instant.ofEpochMilli(board.completedAt).atZone(ZoneId.systemDefault()).format(dateFormat)
            row.findViewById<TextView>(R.id.subtitle).text =
                "$date · ${getString(R.string.history_clues, board.clues)}"
            row.findViewById<TextView>(R.id.time).text = formatTime(board.timeMs)
            row.findViewById<TextView>(R.id.detail).apply {
                if (board.isPerfect) {
                    text = getString(R.string.history_perfect)
                    setTextColor(palette.userDigit)
                } else {
                    text = listOfNotNull(
                        board.mistakes.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.mistakes, it, it) },
                        board.hints.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.hints, it, it) },
                    ).joinToString(" · ")
                    setTextColor(palette.inkMuted)
                }
            }
            row.contentDescription = "${board.difficulty.title}, ${modeName(board.mode, board.tag)}, " +
                "solved $date in ${formatTime(board.timeMs)}"
            return row
        }
    }

    private companion object {
        const val STATE_FILTER = "filter"
        const val MAX_EXPORT = 400_000
        val FILTERS = listOf(null, GameMode.RANDOM, GameMode.DAILY, GameMode.CUSTOM)
    }
}
