package com.draftpad.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.format.DateUtils
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import com.draftpad.core.Draft

/**
 * Every draft on the phone, newest first. Tap one to open it, long-press to edit its title and
 * notes or delete it. Returns what happened to [EditorActivity]: a draft to open, a new draft, or
 * drafts that were deleted.
 */
class DraftsActivity : Activity() {
    private val app get() = application as DraftpadApp
    private lateinit var grid: GridView
    private lateinit var empty: View
    private val adapter = Adapter()
    private val deleted = ArrayList<String>()

    /** Decoded thumbnails, up to about 16 MB of them. */
    private val thumbnails = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drafts)
        fitSystemBars(findViewById(R.id.root))
        grid = findViewById(R.id.grid)
        empty = findViewById(R.id.empty)
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.new_draft).setOnClickListener { finishWith(Intent().putExtra(EXTRA_NEW, true)) }
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ -> finishWith(Intent().putExtra(EXTRA_OPEN, adapter.getItem(position).id)) }
        grid.setOnItemLongClickListener { _, view, position, _ ->
            showMenu(view, adapter.getItem(position))
            true
        }
        reload()
    }

    internal fun reload() {
        app.io.execute {
            val drafts = app.store.list()
            app.main.post {
                adapter.drafts = drafts
                empty.visibility = if (drafts.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showMenu(anchor: View, draft: Draft) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, R.string.menu_notes)
        popup.menu.add(0, 2, 1, R.string.delete)
        popup.setOnMenuItemClickListener {
            if (it.itemId == 1) editNotes(draft) else delete(draft)
            true
        }
        popup.show()
    }

    internal fun editNotes(draft: Draft) {
        Dialogs.notes(this, draft.title, draft.notes) { title, notes ->
            app.io.execute {
                app.store.load(draft.id)?.let { app.store.save(it.copy(title = title, notes = notes)) }
            }
            setResult(RESULT_OK, result())
            reload()
        }
    }

    internal fun delete(draft: Draft) {
        val name = draft.title.ifEmpty { getString(R.string.untitled) }
        Dialogs.confirm(this, getString(R.string.delete_title), getString(R.string.delete_message, name), R.string.delete) {
            deleted += draft.id
            thumbnails.remove(draft.id)
            app.io.execute { app.store.delete(draft.id) }
            setResult(RESULT_OK, result())
            reload()
        }
    }

    private fun result() = Intent().putExtra(EXTRA_DELETED, deleted.toTypedArray())

    private fun finishWith(extras: Intent) {
        setResult(RESULT_OK, result().putExtras(extras))
        finish()
    }

    private fun thumbnail(draft: Draft): Bitmap? {
        val key = "${draft.id}@${draft.modified}"
        return thumbnails.get(key) ?: Images.load(app.store.thumbnail(draft.id))?.also { thumbnails.put(key, it) }
    }

    private inner class Adapter : BaseAdapter() {
        var drafts: List<Draft> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun getCount() = drafts.size

        override fun getItem(position: Int) = drafts[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_draft, parent, false).also {
                it.findViewById<View>(R.id.thumb).clipToOutline = true
            }
            val draft = drafts[position]
            view.findViewById<ImageView>(R.id.thumb).setImageBitmap(thumbnail(draft))
            view.findViewById<TextView>(R.id.name).text = draft.title.ifEmpty { getString(R.string.untitled) }
            val age = DateUtils.getRelativeTimeSpanString(draft.modified, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            view.findViewById<TextView>(R.id.meta).text =
                if (draft.remotePath != null) "$age · ${getString(R.string.on_github)}" else age
            return view
        }
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val EXTRA_NEW = "new"
        const val EXTRA_DELETED = "deleted"
    }
}
