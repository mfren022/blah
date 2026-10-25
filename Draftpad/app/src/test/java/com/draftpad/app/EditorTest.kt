package com.draftpad.app

import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.EditText
import com.draftpad.core.DraftJson
import com.draftpad.core.Label
import com.draftpad.core.Shape
import com.draftpad.core.ShapeKind
import com.draftpad.core.Stroke
import com.draftpad.core.Svg
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class EditorTest : ScreenTest() {

    private fun launch(intent: Intent? = null) =
        (if (intent == null) Robolectric.buildActivity(EditorActivity::class.java) else Robolectric.buildActivity(EditorActivity::class.java, intent))
            .setup()

    private val EditorActivity.density get() = resources.displayMetrics.density

    /** A fraction of the canvas, for a point in canvas units (at 100% zoom, not panned). */
    private fun EditorActivity.at(x: Float, y: Float) = x * density / sketch.width to y * density / sketch.height

    @Test
    fun opensOnABlankCanvas() {
        val a = launch().get()
        assertEquals(a.getString(R.string.untitled), a.findViewById<android.widget.TextView>(R.id.title).text)
        assertFalse(a.findViewById<android.view.View>(R.id.undo).isEnabled)
        assertTrue(a.toolButton(Tool.PEN).isSelected)
        capture(a, "01_editor_blank")
    }

    @Test
    fun drawsStrokesAndShapes() {
        val a = launch().get()
        drag(a.sketch, 0.2f to 0.2f, 0.4f to 0.3f, 0.6f to 0.2f)
        val stroke = a.history.current.single() as Stroke
        assertTrue(stroke.pointCount > 10)
        assertEquals(Pens.COLORS[0], stroke.color)
        assertEquals(Pens.WIDTHS[1], stroke.width)
        // Canvas units are dp.
        assertEquals(0.2f * a.sketch.width / a.density, stroke.points[0], 0.5f)

        a.selectTool(Tool.RECT)
        a.selectColor(5)
        drag(a.sketch, 0.1f to 0.5f, 0.5f to 0.7f)
        val rect = a.history.current[1] as Shape
        assertEquals(ShapeKind.RECT, rect.kind)
        assertEquals(Pens.COLORS[5], rect.color)
        // A tap with a shape tool draws nothing.
        tap(a.sketch, 0.8f, 0.8f)
        assertEquals(2, a.history.current.size)

        a.selectTool(Tool.ARROW)
        drag(a.sketch, 0.7f to 0.4f, 0.9f to 0.6f)
        assertEquals(ShapeKind.ARROW, (a.history.current[2] as Shape).kind)

        a.undo()
        assertEquals(2, a.history.current.size)
        assertTrue(a.findViewById<android.view.View>(R.id.redo).isEnabled)
        a.redo()
        assertEquals(3, a.history.current.size)
        capture(a, "02_editor_drawn")
    }

    @Test
    fun aTapWithThePenLeavesADot() {
        val a = launch().get()
        tap(a.sketch, 0.5f, 0.5f)
        assertEquals(1, (a.history.current.single() as Stroke).pointCount)
    }

    @Test
    fun textToolAddsEditsAndDeletesLabels() {
        val a = launch().get()
        a.selectTool(Tool.TEXT)
        tap(a.sketch, 0.3f, 0.4f)
        latestDialog().findViewById<EditText>(R.id.text).setText("Play")
        latestDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()
        val label = a.history.current.single() as Label
        assertEquals("Play", label.text)
        assertEquals(Pens.TEXT_SIZES[1], label.size)

        val (fx, fy) = a.at(label.x + 8f, label.y - 6f)
        tap(a.sketch, fx, fy)
        val edit = latestDialog()
        assertEquals("Play", edit.findViewById<EditText>(R.id.text).text.toString())
        edit.findViewById<EditText>(R.id.text).setText("Play now")
        edit.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()
        assertEquals("Play now", (a.history.current.single() as Label).text)

        tap(a.sketch, fx, fy)
        latestDialog().getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
        idle()
        assertTrue(a.history.current.isEmpty())
    }

    @Test
    fun eraserRemovesOnlyWhatItTouches() {
        val a = launch().get()
        drag(a.sketch, 0.1f to 0.2f, 0.9f to 0.2f)
        drag(a.sketch, 0.1f to 0.6f, 0.9f to 0.6f)
        a.selectTool(Tool.ERASE)
        drag(a.sketch, 0.5f to 0.1f, 0.5f to 0.3f)
        val left = a.history.current.single() as Stroke
        assertEquals(0.6f * a.sketch.height / a.density, left.points[1], 1f)
        a.undo()
        assertEquals(2, a.history.current.size)
    }

    @Test
    fun moveToolDragsElementsAndPansEmptySpace() {
        val a = launch().get()
        a.selectTool(Tool.RECT)
        val (x1, y1) = a.at(50f, 100f)
        val (x2, y2) = a.at(150f, 200f)
        drag(a.sketch, x1 to y1, x2 to y2)
        a.selectTool(Tool.MOVE)
        // Grab the left edge and move it 40 units right.
        val (gx, gy) = a.at(50f, 150f)
        val (dx, dy) = a.at(90f, 150f)
        drag(a.sketch, gx to gy, dx to dy)
        val moved = a.history.current.single() as Shape
        assertEquals(90f, moved.x1, 1f)
        assertEquals(100f, moved.y1, 1f)

        // Dragging empty canvas pans the view instead.
        val before = a.sketch.visibleArea()
        drag(a.sketch, 0.8f to 0.8f, 0.6f to 0.8f)
        assertEquals(1, a.history.current.size)
        assertTrue(a.sketch.visibleArea().left > before.left + 50f)
    }

    @Test
    fun twoFingersZoomWithoutDrawing() {
        val a = launch().get()
        pinch(a.sketch, listOf(0.4f to 0.5f, 0.6f to 0.5f), listOf(0.2f to 0.5f, 0.8f to 0.5f))
        assertTrue(a.history.current.isEmpty())
        assertEquals(3f, a.sketch.zoom, 0.2f)
        // The point under the fingers stays put.
        assertEquals(a.sketch.width / 2f / a.density, a.sketch.toCanvasX(a.sketch.width / 2f) * 1f, 1f)
    }

    @Test
    fun keepsTheDraftBetweenLaunches() {
        val first = launch()
        drag(first.get().sketch, 0.2f to 0.2f, 0.6f to 0.6f)
        val id = first.get().draft.id
        first.pause().stop().destroy()
        settle()
        assertTrue(app.store.exists(id))
        assertTrue(app.store.thumbnail(id).isFile)

        val second = launch().get()
        assertEquals(id, second.draft.id)
        assertEquals(1, second.history.current.size)
    }

    @Test
    fun emptyDraftsAreNotKept() {
        launch().pause().stop().destroy()
        settle()
        assertTrue(app.store.list().isEmpty())
    }

    @Test
    fun opensASavedSketch() {
        app.store.save(Samples.levelSelect())
        val a = launch(Intent(app, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_DRAFT, "20261025-101500")).get()
        assertEquals("Level select", a.findViewById<android.widget.TextView>(R.id.title).text)
        capture(a, "03_editor_sketch")
    }

    @Test
    fun sharesThePictureWithTheNotes() {
        app.store.save(Samples.levelSelect())
        val a = launch(Intent(app, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_DRAFT, "20261025-101500")).get()
        a.onMenu(EditorActivity.MENU_SHARE_PNG)
        settle()
        val chooser = shadowOf(a).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("image/png", send.type)
        assertEquals("Level select\n\nThree rows of levels. The bottom row is still locked.", send.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content://com.draftpad.app.share/level-select.png", uri.toString())
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)

        // The provider serves the picture, with its name and size.
        val png = a.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)
        assertEquals(Renderer.EXPORT_SIDE, maxOf(bitmap.width, bitmap.height))
        a.contentResolver.query(uri, null, null, null, null)!!.use {
            assertTrue(it.moveToFirst())
            assertEquals("level-select.png", it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)))
            assertEquals(png.size.toLong(), it.getLong(it.getColumnIndexOrThrow(OpenableColumns.SIZE)))
        }
        assertEquals("image/png", a.contentResolver.getType(uri))
        write(bitmap, "04_shared_picture")
    }

    @Test
    fun sharesTheSvgAsWell() {
        app.store.save(Samples.levelSelect())
        val a = launch(Intent(app, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_DRAFT, "20261025-101500")).get()
        a.onMenu(EditorActivity.MENU_SHARE_SVG)
        settle()
        @Suppress("DEPRECATION")
        val send = shadowOf(a).nextStartedActivity.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND_MULTIPLE, send.action)
        @Suppress("DEPRECATION")
        val uris = send.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals(listOf("level-select.png", "level-select.svg"), uris.map { it.lastPathSegment })
        val svg = a.contentResolver.openInputStream(uris[1])!!.use { it.readBytes().toString(Charsets.UTF_8) }
        assertTrue(svg.contains("<title>Level select</title>"))
        assertTrue(svg.contains(">Locked levels</tspan>"))
        File(screenshots, "04_shared_sketch.svg").writeText(svg)
    }

    @Test
    fun nothingToShareOnABlankCanvas() {
        val a = launch().get()
        a.onMenu(EditorActivity.MENU_SHARE_PNG)
        settle()
        assertEquals(a.getString(R.string.nothing_to_share), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun pushesToGitHubAndRemembersTheFolder() {
        val github = RecordingGitHub()
        app.http = github
        app.github.repoText = "mfren022/blah"
        app.github.token = "github_pat_test"
        app.store.save(Samples.levelSelect())
        val a = launch(Intent(app, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_DRAFT, "20261025-101500")).get()
        a.onMenu(EditorActivity.MENU_PUSH)
        settle()

        val folder = "drafts/2025-10-25-level-select"
        assertEquals(
            listOf("README.md", "sketch.png", "sketch.svg", "draft.json").map { "$folder/$it" },
            github.pushed.keys.toList(),
        )
        assertTrue(String(github.pushed.getValue("$folder/README.md")).startsWith("# Level select\n\nThree rows of levels."))
        assertEquals(folder, DraftJson.decode(String(github.pushed.getValue("$folder/draft.json"))).remotePath)
        assertEquals(folder, a.draft.remotePath)
        settle()
        assertEquals(folder, app.store.load("20261025-101500")!!.remotePath)

        captureDialog(a, "06_pushed")
        latestDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()
        val clipboard = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertTrue(clipboard.primaryClip!!.getItemAt(0).text.toString().startsWith("Have a look at my sketch in $folder"))
    }

    @Test
    fun firstPushAsksForTheRepository() {
        app.http = RecordingGitHub()
        val a = launch().get()
        drag(a.sketch, 0.2f to 0.2f, 0.6f to 0.6f)
        a.onMenu(EditorActivity.MENU_PUSH)
        val dialog = latestDialog()
        dialog.findViewById<EditText>(R.id.repo).setText("https://github.com/mfren022/blah")
        dialog.findViewById<EditText>(R.id.token).setText("github_pat_test")
        captureDialog(a, "05_github_setup")
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        settle()
        assertEquals("mfren022/blah", app.github.repoText)
        assertEquals("drafts", app.github.folder)
        assertNotNull(a.draft.remotePath)
    }

    @Test
    fun explainsAFailedPush() {
        app.http = RecordingGitHub(failWith = 401)
        app.github.repoText = "mfren022/blah"
        app.github.token = "expired"
        val a = launch().get()
        drag(a.sketch, 0.2f to 0.2f, 0.6f to 0.6f)
        a.onMenu(EditorActivity.MENU_PUSH)
        settle()
        assertTrue(shadowOf(latestDialog()).message.toString().contains("didn't accept the token"))
    }

    @Test
    fun sketchesOverASharedImage() {
        // A stand-in screenshot: a dark bar over a grey page with two cards.
        val shot = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        Canvas(shot).apply {
            drawColor(Color.rgb(236, 238, 242))
            val p = Paint()
            p.color = Color.rgb(40, 44, 60)
            drawRect(0f, 0f, 1080f, 260f, p)
            p.color = Color.WHITE
            drawRect(60f, 360f, 1020f, 900f, p)
            drawRect(60f, 980f, 1020f, 1520f, p)
        }
        val file = File(app.cacheDir, "shot.png").apply { outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val share = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
        val a = launch(share).get()
        settle()
        val background = a.draft.background!!
        assertTrue(app.store.file(a.draft.id, background.file).isFile)
        assertEquals(1080f / 2400f, background.width / background.height, 0.01f)

        a.selectTool(Tool.ARROW)
        a.selectColor(2)
        drag(a.sketch, 0.75f to 0.15f, 0.55f to 0.3f)
        a.selectTool(Tool.TEXT)
        tap(a.sketch, 0.55f, 0.08f)
        latestDialog().findViewById<EditText>(R.id.text).setText("Bigger title")
        latestDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()
        capture(a, "07_over_image")
        a.onMenu(EditorActivity.MENU_SHARE_PNG)
        settle()
        @Suppress("DEPRECATION")
        val uri = shadowOf(a).nextStartedActivity.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        write(a.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }, "08_over_image_shared")
    }

    @Test
    fun menusOfferTheirActions() {
        val a = launch().get()
        a.onMenu(EditorActivity.MENU_GRID)
        assertFalse(a.sketch.showGrid)
        assertFalse(app.prefs.grid)
        drag(a.sketch, 0.2f to 0.2f, 0.6f to 0.6f)
        a.onMenu(EditorActivity.MENU_CLEAR)
        assertTrue(a.history.current.isEmpty())
        a.undo()
        assertEquals(1, a.history.current.size)
        a.onMenu(EditorActivity.MENU_NOTES)
        latestDialog().findViewById<EditText>(R.id.title).setText("Castle keep")
        latestDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()
        assertEquals("Castle keep", a.draft.title)
        val old = a.draft.id
        a.onMenu(EditorActivity.MENU_NEW)
        assertTrue(a.draft.id != old)
        assertTrue(a.history.current.isEmpty())
        settle()
        assertEquals("Castle keep", app.store.load(old)!!.title)
    }

    @Test
    fun svgAndPictureAgree() {
        val draft = Samples.levelSelect()
        val picture = Renderer.picture(draft, null, Renderer.EXPORT_SIDE)
        val svg = Svg.write(draft, picture.frame)
        assertTrue(svg.contains("width=\"${com.draftpad.core.Json.num(picture.frame.width)}\""))
        write(picture.bitmap, "09_export")
        File(screenshots, "09_export.svg").writeText(svg)
    }
}
