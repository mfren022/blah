package com.draftpad.app

import android.graphics.Bitmap
import com.draftpad.core.Draft
import com.draftpad.core.DraftJson
import com.draftpad.core.Names
import java.io.File
import java.io.OutputStream
import java.time.ZoneId

/**
 * Drafts on disk, one folder each: drafts/<id>/draft.json, thumb.png and the background image if
 * there is one. Every method does file work, so call them off the main thread where it matters.
 */
class DraftStore(private val root: File) {
    /** Ids handed out by [newId], saved or not yet, so two new drafts never share one. */
    private val issued = HashSet<String>()

    fun dir(id: String) = File(root, id)

    fun exists(id: String) = File(dir(id), DRAFT).isFile

    /** The draft, or null if it is missing or unreadable. */
    fun load(id: String): Draft? = try {
        DraftJson.decode(File(dir(id), DRAFT).readText())
    } catch (e: Exception) {
        null
    }

    /** All drafts, most recently changed first. */
    fun list(): List<Draft> =
        root.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { load(it.name) }.sortedByDescending { it.modified }

    fun save(draft: Draft) {
        val dir = dir(draft.id).apply { mkdirs() }
        write(File(dir, DRAFT)) { it.write(DraftJson.encode(draft).toByteArray()) }
    }

    fun thumbnail(id: String) = File(dir(id), THUMBNAIL)

    fun saveThumbnail(id: String, bitmap: Bitmap) {
        write(thumbnail(id)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Where a draft keeps its background image [name]. */
    fun file(id: String, name: String) = File(dir(id), name)

    fun delete(id: String) {
        dir(id).deleteRecursively()
    }

    @Synchronized
    fun newId(): String =
        Names.newId(System.currentTimeMillis(), ZoneId.systemDefault()) { it in issued || dir(it).exists() }.also { issued += it }

    companion object {
        const val DRAFT = "draft.json"
        const val THUMBNAIL = "thumb.png"

        /** Writes through a temporary file, so a crash mid-write never leaves half a file. */
        fun write(file: File, body: (OutputStream) -> Unit) {
            file.parentFile?.mkdirs()
            val part = File(file.path + ".part")
            part.outputStream().buffered().use(body)
            if (!part.renameTo(file)) {
                file.delete()
                check(part.renameTo(file)) { "Couldn't write $file" }
            }
        }
    }
}
