package io.github.mfren022.drivein

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

/** The home screen: the phone's videos and the samples, adapting to phone and car screens. */
class LibraryActivity : Activity(), PlayerEngine.Listener {

    private lateinit var grid: GridView
    private lateinit var tabDevice: TextView
    private lateinit var tabSamples: TextView
    private lateinit var emptyView: View
    private lateinit var emptyText: TextView
    private lateinit var grantButton: View
    private lateinit var nowPlaying: View
    private lateinit var nowPlayingTitle: TextView
    private lateinit var nowPlayingToggle: ImageButton

    private val adapter = VideoAdapter()
    private var category = VideoLibrary.CATEGORY_DEVICE
    private var loadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsets.CONSUMED
        }

        grid = findViewById(R.id.grid)
        tabDevice = findViewById(R.id.tab_device)
        tabSamples = findViewById(R.id.tab_samples)
        emptyView = findViewById(R.id.empty)
        emptyText = findViewById(R.id.empty_text)
        grantButton = findViewById(R.id.grant)
        nowPlaying = findViewById(R.id.now_playing)
        nowPlayingTitle = findViewById(R.id.now_playing_title)
        nowPlayingToggle = findViewById(R.id.now_playing_toggle)

        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            engine.play(adapter.items, position)
            openPlayer()
        }
        tabDevice.setOnClickListener { showCategory(VideoLibrary.CATEGORY_DEVICE) }
        tabSamples.setOnClickListener { showCategory(VideoLibrary.CATEGORY_SAMPLES) }
        grantButton.setOnClickListener { requestPermissions(arrayOf(VideoLibrary.videoPermission), REQUEST_PERMISSION) }
        findViewById<View>(R.id.open_file).setOnClickListener { openFile() }
        findViewById<View>(R.id.play_url).setOnClickListener { askForUrl() }
        nowPlaying.setOnClickListener { openPlayer() }
        nowPlayingToggle.setOnClickListener { engine.togglePlayPause() }

        category = savedInstanceState?.getString(STATE_CATEGORY)
            ?: if (VideoLibrary.hasPermission(this)) VideoLibrary.CATEGORY_DEVICE else VideoLibrary.CATEGORY_SAMPLES
    }

    override fun onStart() {
        super.onStart()
        engine.addListener(this)
        onPlayerStateChanged()
        refresh()
    }

    override fun onStop() {
        engine.removeListener(this)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CATEGORY, category)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION) refresh()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQUEST_OPEN_FILE || resultCode != RESULT_OK || uri == null) return
        // Keep access so the video can be resumed later, even after a restart.
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        playSingle(uri)
    }

    override fun onPlayerStateChanged() {
        val item = engine.currentItem
        nowPlaying.visibility = if (item != null) View.VISIBLE else View.GONE
        nowPlayingTitle.text = item?.title
        nowPlayingToggle.setImageResource(if (engine.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        nowPlayingToggle.contentDescription = getString(if (engine.isPlaying) R.string.pause else R.string.play)
    }

    private fun showCategory(newCategory: String) {
        if (newCategory == category) return
        category = newCategory
        refresh()
    }

    private fun refresh() {
        val generation = ++loadGeneration
        val showing = category
        tabDevice.isSelected = showing == VideoLibrary.CATEGORY_DEVICE
        tabSamples.isSelected = showing == VideoLibrary.CATEGORY_SAMPLES
        for (tab in listOf(tabDevice, tabSamples)) {
            tab.setTextColor(getColor(if (tab.isSelected) R.color.accent else R.color.on_background_muted))
        }

        if (showing == VideoLibrary.CATEGORY_DEVICE && !VideoLibrary.hasPermission(this)) {
            adapter.items = emptyList()
            showEmpty(getString(R.string.permission_rationale), showGrant = true)
            return
        }
        if (adapter.items.isEmpty()) showEmpty(getString(R.string.loading), showGrant = false)
        Background.load({ VideoLibrary.children(applicationContext, showing) }) { items ->
            if (generation != loadGeneration || isDestroyed) return@load
            adapter.items = items
            if (items.isEmpty()) showEmpty(getString(R.string.no_videos), showGrant = false) else emptyView.visibility = View.GONE
        }
    }

    private fun showEmpty(message: String, showGrant: Boolean) {
        emptyView.visibility = View.VISIBLE
        emptyText.text = message
        grantButton.visibility = if (showGrant) View.VISIBLE else View.GONE
    }

    private fun openFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("video/*")
        startActivityForResult(intent, REQUEST_OPEN_FILE)
    }

    private fun askForUrl() {
        val input = EditText(this).apply {
            hint = getString(R.string.url_dialog_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        val padding = resources.getDimensionPixelSize(R.dimen.gutter)
        val container = android.widget.FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.url_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.play) { _, _ ->
                val uri = Uri.parse(input.text.toString().trim())
                if (uri.scheme == "http" || uri.scheme == "https") {
                    playSingle(uri)
                } else {
                    Toast.makeText(this, R.string.url_invalid, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun playSingle(uri: Uri) {
        engine.play(listOf(VideoLibrary.itemForUri(this, uri)), 0)
        openPlayer()
    }

    private fun openPlayer() {
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private inner class VideoAdapter : BaseAdapter() {
        var items: List<VideoItem> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false)
            val item = items[position]
            view.findViewById<TextView>(R.id.title).text = item.title
            view.findViewById<TextView>(R.id.subtitle).text = item.subtitle
            view.findViewById<TextView>(R.id.duration).apply {
                text = formatDuration(item.durationMs)
                visibility = if (item.durationMs > 0) View.VISIBLE else View.GONE
            }
            Thumbnails.into(view.findViewById<ImageView>(R.id.thumbnail), item)
            return view
        }
    }

    private companion object {
        const val REQUEST_PERMISSION = 1
        const val REQUEST_OPEN_FILE = 2
        const val STATE_CATEGORY = "category"
    }
}
