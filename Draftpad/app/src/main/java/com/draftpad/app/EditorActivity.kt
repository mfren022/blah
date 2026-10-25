package com.draftpad.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import com.draftpad.core.Background
import com.draftpad.core.Box
import com.draftpad.core.Draft
import com.draftpad.core.Element
import com.draftpad.core.GitHub
import com.draftpad.core.History
import com.draftpad.core.Label
import com.draftpad.core.SketchPusher
import java.time.ZoneId
import kotlin.math.min

/**
 * The sketch editor, and the app's home screen: it reopens the last draft. Top bar: drafts, title
 * (tap for title and notes), undo, redo, share/push and more. Bottom: pen colours, widths and tools.
 */
class EditorActivity : Activity(), SketchView.Listener {
    private val app get() = application as DraftpadApp

    internal lateinit var sketch: SketchView
        private set
    private lateinit var titleView: TextView
    private lateinit var undoButton: View
    private lateinit var redoButton: View
    private val toolButtons = mutableListOf<ImageButton>()
    private val swatches = mutableListOf<SwatchView>()
    private val widthViews = mutableListOf<WidthView>()

    /** The open draft. Its elements live in [history]; [draft]'s own copy is from the last save. */
    internal lateinit var draft: Draft
        private set
    internal val history = History()
    private var backgroundBitmap: Bitmap? = null
    private var dirty = false
    private val saveSoon = Runnable { save() }
    private var widthIndex = 1
    private var progress: android.app.Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        fitSystemBars(findViewById(R.id.root))
        sketch = findViewById(R.id.sketch)
        sketch.listener = this
        titleView = findViewById(R.id.title)
        undoButton = findViewById(R.id.undo)
        redoButton = findViewById(R.id.redo)
        titleView.setOnClickListener { editNotes() }
        undoButton.setOnClickListener { undo() }
        redoButton.setOnClickListener { redo() }
        findViewById<View>(R.id.drafts).setOnClickListener { showDrafts() }
        findViewById<View>(R.id.send).setOnClickListener { showSendMenu(it) }
        findViewById<View>(R.id.more).setOnClickListener { showMoreMenu(it) }
        buildPalette()

        val prefs = app.prefs
        selectTool(prefs.tool)
        selectColor(prefs.colorIndex)
        selectWidth(prefs.widthIndex)
        sketch.showGrid = prefs.grid

        val shared = if (savedInstanceState == null) sharedImage(intent) else null
        val id = savedInstanceState?.getString(STATE_DRAFT) ?: intent.getStringExtra(EXTRA_DRAFT)
            ?: prefs.lastDraft.takeIf { shared == null }
        val existing = id?.let(app.store::load)
        open(existing ?: newDraft(id.takeIf { savedInstanceState != null }))
        if (shared != null) importImage(shared)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_DRAFT, draft.id)
    }

    override fun onPause() {
        super.onPause()
        save()
    }

    override fun onDestroy() {
        super.onDestroy()
        app.main.removeCallbacks(saveSoon)
        progress?.dismiss()
    }

    // ---- Drafts

    private fun newDraft(id: String? = null): Draft {
        val now = System.currentTimeMillis()
        return Draft(id = id ?: app.store.newId(), created = now, modified = now)
    }

    private fun open(d: Draft) {
        app.main.removeCallbacks(saveSoon)
        draft = d
        dirty = false
        history.reset(d.elements)
        sketch.elements = d.elements
        sketch.resetView()
        backgroundBitmap = null
        sketch.setBackgroundImage(null, null)
        if (app.store.exists(d.id)) app.prefs.lastDraft = d.id
        d.background?.let { bg ->
            app.io.execute {
                val bitmap = Images.load(app.store.file(d.id, bg.file))
                app.main.post {
                    if (bitmap != null && draft.id == d.id && draft.background == bg) {
                        backgroundBitmap = bitmap
                        sketch.setBackgroundImage(bitmap, bg)
                    }
                }
            }
        }
        // Bring a sketch that doesn't fit the screen into view, once the canvas has its size.
        afterLayout(sketch) {
            val content = d.contentBounds(sketch.renderer.metrics)
            if (content != null && !sketch.visibleArea().contains(content)) sketch.fitTo(content.outset(Renderer.PADDING))
        }
        updateTitle()
        updateUndo()
    }

    /** The draft as it is now. */
    internal fun current(): Draft = draft.copy(elements = history.current)

    /** Writes the draft and its thumbnail if anything changed. A new draft is kept once it has something in it. */
    internal fun save() {
        app.main.removeCallbacks(saveSoon)
        if (!dirty) return
        dirty = false
        val d = current().copy(modified = System.currentTimeMillis())
        if (d.isEmpty && d.title.isEmpty() && d.notes.isEmpty() && !app.store.exists(d.id)) return
        draft = d
        app.prefs.lastDraft = d.id
        val shown = backgroundBitmap
        app.io.execute {
            app.store.save(d)
            val thumbnail = Renderer.picture(d, backgroundOf(d, shown), Renderer.THUMBNAIL_SIDE).bitmap
            app.store.saveThumbnail(d.id, thumbnail)
            thumbnail.recycle()
        }
    }

    /**
     * The background image to draw [d] with: the one on screen ([shown]), or read from disk if it is
     * still loading. Call off the main thread.
     */
    private fun backgroundOf(d: Draft, shown: Bitmap?): Bitmap? =
        shown ?: d.background?.let { Images.load(app.store.file(d.id, it.file)) }

    private fun changed() {
        dirty = true
        app.main.removeCallbacks(saveSoon)
        app.main.postDelayed(saveSoon, SAVE_DELAY_MS)
        updateUndo()
    }

    private fun showDrafts() {
        save()
        startActivityForResult(Intent(this, DraftsActivity::class.java), REQUEST_DRAFTS)
    }

    private fun openFromDrafts(data: Intent?) {
        data ?: return
        val deleted = data.getStringArrayExtra(DraftsActivity.EXTRA_DELETED).orEmpty()
        val chosen = data.getStringExtra(DraftsActivity.EXTRA_OPEN)
        val fresh = data.getBooleanExtra(DraftsActivity.EXTRA_NEW, false)
        val id = draft.id
        // On the disk thread, so the saves and deletes the drafts screen asked for have happened.
        app.io.execute {
            val next = when {
                fresh -> null
                chosen != null && chosen != id -> app.store.load(chosen)
                id in deleted -> app.store.list().firstOrNull()
                else -> {
                    // Same draft: only its title or notes may have been edited there.
                    val stored = app.store.load(id) ?: return@execute
                    app.main.post {
                        if (draft.id == id) {
                            draft = draft.copy(title = stored.title, notes = stored.notes)
                            updateTitle()
                        }
                    }
                    return@execute
                }
            }
            app.main.post { if (draft.id == id) open(next ?: newDraft()) }
        }
    }

    private fun editNotes() {
        Dialogs.notes(this, draft.title, draft.notes) { title, notes ->
            if (title != draft.title || notes != draft.notes) {
                draft = draft.copy(title = title, notes = notes)
                updateTitle()
                changed()
            }
        }
    }

    private fun updateTitle() {
        titleView.text = draft.title.ifEmpty { getString(R.string.untitled) }
        titleView.setTextColor(getColor(if (draft.title.isEmpty()) R.color.ink_muted else R.color.ink))
    }

    // ---- Drawing

    override fun onSketchChanged(elements: List<Element>) {
        if (history.commit(elements)) changed()
        sketch.elements = history.current
    }

    override fun onTextTap(x: Float, y: Float, label: Label?) {
        if (label == null) {
            Dialogs.text(this, "", onDone = { text ->
                if (text.isNotBlank()) {
                    val size = Pens.TEXT_SIZES[widthIndex]
                    // The tap marks the middle of the first line.
                    onSketchChanged(history.current + Label(text.trimEnd(), x, y + size * 0.35f, size, sketch.color))
                }
            })
        } else {
            val without = { history.current.filterNot { it === label } }
            Dialogs.text(this, label.text, onDone = { text ->
                onSketchChanged(
                    if (text.isBlank()) without() else history.current.map { if (it === label) label.copy(text = text.trimEnd()) else it },
                )
            }, onDelete = { onSketchChanged(without()) })
        }
    }

    internal fun undo() {
        if (history.undo()) {
            sketch.elements = history.current
            changed()
        }
    }

    internal fun redo() {
        if (history.redo()) {
            sketch.elements = history.current
            changed()
        }
    }

    private fun updateUndo() {
        undoButton.isEnabled = history.canUndo
        redoButton.isEnabled = history.canRedo
    }

    private fun buildPalette() {
        val tools = findViewById<LinearLayout>(R.id.tools)
        for (tool in Tool.entries) {
            val button = ImageButton(this).apply {
                setImageResource(tool.icon)
                contentDescription = getString(tool.label)
                background = getDrawable(R.drawable.bg_tool)
                imageTintList = getColorStateList(R.color.icon_tint)
                setOnClickListener { selectTool(tool) }
            }
            tools.addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            toolButtons += button
        }
        val colors = findViewById<LinearLayout>(R.id.colors)
        Pens.COLORS.forEachIndexed { i, color ->
            val swatch = SwatchView(this, color).apply {
                contentDescription = getString(Pens.COLOR_NAMES[i])
                setOnClickListener { selectColor(i, pickPen = true) }
            }
            colors.addView(swatch, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            swatches += swatch
        }
        val widths = findViewById<LinearLayout>(R.id.widths)
        Pens.WIDTHS.forEachIndexed { i, width ->
            val view = WidthView(this, width).apply {
                contentDescription = getString(Pens.WIDTH_NAMES[i])
                setOnClickListener { selectWidth(i) }
            }
            widths.addView(view, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            widthViews += view
        }
    }

    internal fun toolButton(tool: Tool): View = toolButtons[tool.ordinal]

    internal fun selectTool(tool: Tool) {
        sketch.tool = tool
        app.prefs.tool = tool
        toolButtons.forEachIndexed { i, b -> b.isSelected = i == tool.ordinal }
    }

    /** Picks a colour. Picking one while moving or erasing goes back to the pen ([pickPen]). */
    internal fun selectColor(index: Int, pickPen: Boolean = false) {
        sketch.color = Pens.COLORS[index]
        app.prefs.colorIndex = index
        swatches.forEachIndexed { i, s -> s.isSelected = i == index }
        if (pickPen && (sketch.tool == Tool.MOVE || sketch.tool == Tool.ERASE)) selectTool(Tool.PEN)
    }

    internal fun selectWidth(index: Int) {
        widthIndex = index
        sketch.lineWidth = Pens.WIDTHS[index]
        app.prefs.widthIndex = index
        widthViews.forEachIndexed { i, v -> v.isSelected = i == index }
    }

    // ---- Menus

    private fun showSendMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val menu = popup.menu
        menu.add(0, MENU_SHARE_PNG, 0, R.string.menu_share_png)
        menu.add(0, MENU_SHARE_SVG, 1, R.string.menu_share_svg)
        val github = app.github
        menu.add(0, MENU_PUSH, 2, if (github.isReady) getString(R.string.menu_push, github.repo.toString()) else getString(R.string.menu_push_setup))
        if (Build.VERSION.SDK_INT >= 29) menu.add(0, MENU_SAVE, 3, R.string.menu_save)
        popup.setOnMenuItemClickListener { onMenu(it.itemId) }
        popup.show()
    }

    private fun showMoreMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val menu = popup.menu
        menu.add(0, MENU_NEW, 0, R.string.menu_new)
        menu.add(0, MENU_NOTES, 1, R.string.menu_notes)
        menu.add(0, MENU_IMAGE, 2, if (draft.background == null) R.string.menu_insert_image else R.string.menu_replace_image)
        if (draft.background != null) menu.add(0, MENU_REMOVE_IMAGE, 3, R.string.menu_remove_image)
        menu.add(0, MENU_FIT, 4, R.string.menu_fit)
        menu.add(0, MENU_GRID, 5, R.string.menu_grid).isCheckable = true
        menu.findItem(MENU_GRID).isChecked = sketch.showGrid
        menu.add(0, MENU_CLEAR, 6, R.string.menu_clear).isEnabled = history.current.isNotEmpty()
        menu.add(0, MENU_GITHUB, 7, R.string.menu_github)
        popup.setOnMenuItemClickListener { onMenu(it.itemId) }
        popup.show()
    }

    internal fun onMenu(id: Int): Boolean {
        when (id) {
            MENU_SHARE_PNG -> share(withSvg = false)
            MENU_SHARE_SVG -> share(withSvg = true)
            MENU_PUSH -> push()
            MENU_SAVE -> saveToPictures()
            MENU_NEW -> {
                save()
                open(newDraft())
            }
            MENU_NOTES -> editNotes()
            MENU_IMAGE -> pickImage()
            MENU_REMOVE_IMAGE -> removeImage()
            MENU_FIT -> sketch.fitTo(current().contentBounds(sketch.renderer.metrics)?.outset(Renderer.PADDING))
            MENU_GRID -> {
                sketch.showGrid = !sketch.showGrid
                app.prefs.grid = sketch.showGrid
            }
            MENU_CLEAR -> if (history.current.isNotEmpty()) {
                onSketchChanged(emptyList())
                toast(R.string.cleared)
            }
            MENU_GITHUB -> Dialogs.github(this)
            else -> return false
        }
        return true
    }

    // ---- Sharing and pushing

    private fun share(withSvg: Boolean) {
        val d = current()
        if (d.isEmpty) return toast(R.string.nothing_to_share)
        save()
        val shown = backgroundBitmap
        val backgroundFile = d.background?.let { app.store.file(d.id, it.file) }
        val context = applicationContext
        app.io.execute {
            val files = Exports.shareFiles(context, d, backgroundOf(d, shown), backgroundFile, withSvg)
            app.main.post { if (!isDestroyed) startActivity(Exports.shareIntent(this, d, files)) }
        }
    }

    private fun saveToPictures() {
        val d = current()
        if (d.isEmpty) return toast(R.string.nothing_to_share)
        val shown = backgroundBitmap
        val context = applicationContext
        app.io.execute {
            val saved = Exports.saveToPictures(context, d, backgroundOf(d, shown))
            app.main.post { context.toast(if (saved) R.string.saved_to_pictures else R.string.save_failed) }
        }
    }

    /** Pushes the sketch to GitHub, asking for the repository and token first if there are none yet. */
    private fun push() {
        val settings = app.github
        val repo = settings.repo
        if (repo == null || settings.token.isEmpty()) {
            Dialogs.github(this) { push() }
            return
        }
        val d = current()
        if (d.isEmpty) return toast(R.string.nothing_to_share)
        save()
        val token = settings.token
        val branch = settings.branch
        val folder = settings.folder
        val shown = backgroundBitmap
        val backgroundFile = d.background?.let { app.store.file(d.id, it.file) }
        progress = Dialogs.progress(this, getString(R.string.pushing, repo.toString()))
        app.net.execute {
            val result = runCatching {
                // Through the disk thread, which may still be writing a just-added background image.
                val (background, backgroundBytes) = app.io.submit<Pair<Bitmap?, ByteArray?>> {
                    backgroundOf(d, shown) to backgroundFile?.takeIf { it.isFile }?.readBytes()
                }.get()
                val picture = Exports.picture(d, background)
                SketchPusher(GitHub(token, app.http))
                    .push(repo, branch, folder, d, picture, backgroundBytes, ZoneId.systemDefault(), Renderer().metrics)
            }
            app.main.post {
                result.onSuccess { rememberFolder(d.id, it.folder) }
                if (isDestroyed) return@post
                progress?.dismiss()
                progress = null
                result.onSuccess { Dialogs.pushed(this, it) }
                    .onFailure { Dialogs.message(this, getString(R.string.push_failed_title), it.message ?: it.toString()) }
            }
        }
    }

    /** Keeps the folder a draft was pushed to, so pushing it again updates that folder. */
    private fun rememberFolder(id: String, folder: String) {
        if (!isDestroyed && draft.id == id) {
            if (draft.remotePath != folder) {
                draft = draft.copy(remotePath = folder)
                dirty = true
                save()
            }
        } else {
            app.io.execute {
                app.store.load(id)?.takeIf { it.remotePath != folder }?.let { app.store.save(it.copy(remotePath = folder)) }
            }
        }
    }

    // ---- Background image

    private fun pickImage() {
        val pick = Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        try {
            startActivityForResult(pick, REQUEST_IMAGE)
        } catch (e: ActivityNotFoundException) {
            toast(R.string.image_failed)
        }
    }

    @Suppress("DEPRECATION") // getParcelableExtra(String, Class) needs Android 13.
    private fun sharedImage(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return null
        return intent.getParcelableExtra(Intent.EXTRA_STREAM) ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }

    /** Reads the image at [uri] and puts it under the drawing, fitted to the screen. */
    private fun importImage(uri: Uri) {
        val id = draft.id
        app.io.execute {
            val bitmap = Images.read(contentResolver, uri)
            app.main.post {
                when {
                    bitmap == null -> toast(R.string.image_failed)
                    // Still on the draft it was picked for.
                    draft.id == id -> {
                        val area = if (sketch.width > 0) sketch.visibleArea() else screenArea()
                        val placement = fit(bitmap.width, bitmap.height, area)
                        backgroundBitmap = bitmap
                        draft = draft.copy(background = placement)
                        sketch.setBackgroundImage(bitmap, placement)
                        val file = app.store.file(id, placement.file)
                        // Queued before the draft's next save, so the image is on disk when the draft is.
                        app.io.execute { Images.saveJpeg(bitmap, file) }
                        changed()
                    }
                }
            }
        }
    }

    private fun removeImage() {
        val background = draft.background ?: return
        val file = app.store.file(draft.id, background.file)
        draft = draft.copy(background = null)
        backgroundBitmap = null
        sketch.setBackgroundImage(null, null)
        changed()
        app.io.execute { file.delete() }
    }

    /** The whole screen in canvas units, for when the canvas hasn't been laid out yet. */
    private fun screenArea(): Box {
        val metrics = resources.displayMetrics
        return Box(0f, 0f, metrics.widthPixels / metrics.density, metrics.heightPixels / metrics.density * 0.7f)
    }

    private fun fit(width: Int, height: Int, area: Box, margin: Float = 16f): Background {
        val scale = min((area.width - 2 * margin) / width, (area.height - 2 * margin) / height)
        val w = width * scale
        val h = height * scale
        return Background(SketchPusher.BACKGROUND, area.left + (area.width - w) / 2, area.top + (area.height - h) / 2, w, h)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_IMAGE -> if (resultCode == RESULT_OK) data?.data?.let(::importImage)
            REQUEST_DRAFTS -> openFromDrafts(data)
        }
    }

    companion object {
        const val EXTRA_DRAFT = "draft"
        private const val STATE_DRAFT = "draft"
        private const val REQUEST_DRAFTS = 1
        private const val REQUEST_IMAGE = 2
        private const val SAVE_DELAY_MS = 1500L

        internal const val MENU_SHARE_PNG = 1
        internal const val MENU_SHARE_SVG = 2
        internal const val MENU_PUSH = 3
        internal const val MENU_SAVE = 4
        internal const val MENU_NEW = 10
        internal const val MENU_NOTES = 11
        internal const val MENU_IMAGE = 12
        internal const val MENU_REMOVE_IMAGE = 13
        internal const val MENU_FIT = 14
        internal const val MENU_GRID = 15
        internal const val MENU_CLEAR = 16
        internal const val MENU_GITHUB = 17
    }
}

/** Runs [action] once [view] has been laid out with a size. */
fun afterLayout(view: View, action: () -> Unit) {
    if (view.isLaidOut && view.width > 0) {
        action()
        return
    }
    view.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
        override fun onLayoutChange(
            v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int,
        ) {
            if (v.width == 0) return
            v.removeOnLayoutChangeListener(this)
            action()
        }
    })
}
