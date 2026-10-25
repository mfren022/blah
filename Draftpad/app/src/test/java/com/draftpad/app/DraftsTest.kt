package com.draftpad.app

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.widget.GridView
import com.draftpad.core.Shape
import com.draftpad.core.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class DraftsTest : ScreenTest() {

    private fun saveSamples() {
        val drafts = listOf(
            Samples.levelSelect("20261025-090000").copy(modified = 1761383000000, remotePath = "drafts/2025-10-25-level-select"),
            Samples.levelSelect("20261025-091500").copy(
                title = "Castle keep",
                modified = 1761384000000,
                elements = listOf(
                    Shape(ShapeKind.RECT, 0f, 80f, 120f, 200f, Pens.COLORS[0], 4f),
                    Shape(ShapeKind.RECT, 40f, 20f, 80f, 80f, Pens.COLORS[0], 4f),
                    Shape(ShapeKind.ARROW, 160f, 40f, 90f, 40f, Pens.COLORS[2], 4f),
                ),
            ),
            Samples.levelSelect("20261025-093000").copy(title = "", modified = 1761385000000),
        )
        for (d in drafts) {
            app.store.save(d)
            app.store.saveThumbnail(d.id, Renderer.picture(d, null, Renderer.THUMBNAIL_SIDE).bitmap)
        }
    }

    private fun launch() = Robolectric.buildActivity(DraftsActivity::class.java).setup().also { settle() }

    @Test
    fun listsDraftsNewestFirst() {
        saveSamples()
        val a = launch().get()
        val grid = a.findViewById<GridView>(R.id.grid)
        assertEquals(3, grid.adapter.count)
        assertEquals(listOf("20261025-093000", "20261025-091500", "20261025-090000"), (0 until 3).map { (grid.adapter.getItem(it) as com.draftpad.core.Draft).id })
        capture(a, "10_drafts")
    }

    @Test
    fun saysSoWhenThereAreNone() {
        val a = launch().get()
        assertEquals(android.view.View.VISIBLE, a.findViewById<android.view.View>(R.id.empty).visibility)
        capture(a, "11_drafts_empty")
    }

    @Test
    fun tappingADraftOpensIt() {
        saveSamples()
        val a = launch().get()
        val grid = a.findViewById<GridView>(R.id.grid)
        grid.performItemClick(grid.getChildAt(1), 1, 1)
        assertTrue(a.isFinishing)
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        assertEquals("20261025-091500", shadowOf(a).resultIntent.getStringExtra(DraftsActivity.EXTRA_OPEN))
    }

    @Test
    fun deletesAfterAsking() {
        saveSamples()
        val a = launch().get()
        a.delete(app.store.load("20261025-091500")!!)
        latestDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        settle()
        assertFalse(app.store.exists("20261025-091500"))
        assertEquals(2, a.findViewById<GridView>(R.id.grid).adapter.count)
        assertEquals(listOf("20261025-091500"), shadowOf(a).resultIntent.getStringArrayExtra(DraftsActivity.EXTRA_DELETED)!!.toList())
    }

    @Test
    fun editorFollowsWhatHappenedInDrafts() {
        saveSamples()
        val editor = Robolectric.buildActivity(
            EditorActivity::class.java,
            Intent(app, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_DRAFT, "20261025-091500"),
        ).setup().get()
        editor.findViewById<android.view.View>(R.id.drafts).performClick()
        val request = shadowOf(editor).nextStartedActivityForResult
        assertEquals(DraftsActivity::class.java.name, request.intent.component!!.className)

        // The open draft was deleted there: the editor moves to the newest one left.
        app.store.delete("20261025-091500")
        shadowOf(editor).receiveResult(request.intent, Activity.RESULT_OK, Intent().putExtra(DraftsActivity.EXTRA_DELETED, arrayOf("20261025-091500")))
        settle()
        assertEquals("20261025-093000", editor.draft.id)

        // "New" there starts a blank draft here.
        shadowOf(editor).receiveResult(request.intent, Activity.RESULT_OK, Intent().putExtra(DraftsActivity.EXTRA_NEW, true))
        settle()
        assertTrue(editor.history.current.isEmpty())
        assertFalse(app.store.exists(editor.draft.id))
    }
}
