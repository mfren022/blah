package com.draftpad.app

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import com.draftpad.core.Draft
import com.draftpad.core.Names
import com.draftpad.core.SketchPusher
import com.draftpad.core.Svg
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/** Turns a draft into files to share, save or push. Rendering and encoding: call off the main thread. */
object Exports {

    /** The PNG of [draft], as shared and pushed. */
    fun picture(draft: Draft, background: Bitmap?): SketchPusher.Picture {
        val p = Renderer.picture(draft, background, Renderer.EXPORT_SIDE)
        val png = ByteArrayOutputStream().also { p.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val picture = SketchPusher.Picture(png, p.bitmap.width, p.bitmap.height, p.frame)
        p.bitmap.recycle()
        return picture
    }

    /**
     * Writes [draft] as a PNG, and as an SVG too if [withSvg], into the share folder. The SVG embeds the
     * background image ([backgroundFile]) so it stands alone.
     */
    fun shareFiles(context: Context, draft: Draft, background: Bitmap?, backgroundFile: File?, withSvg: Boolean): List<File> {
        val dir = ShareProvider.dir(context).apply { mkdirs() }
        val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        dir.listFiles()?.forEach { if (it.lastModified() < dayAgo) it.delete() }

        val name = Names.slug(draft.title)
        val picture = picture(draft, background)
        val png = File(dir, "$name.png").apply { writeBytes(picture.png) }
        if (!withSvg) return listOf(png)
        val href = backgroundFile?.takeIf { it.isFile }?.let {
            "data:image/jpeg;base64," + Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)
        }
        val svg = File(dir, "$name.svg").apply { writeText(Svg.write(draft, picture.frame, href)) }
        return listOf(png, svg)
    }

    /** The share sheet for [files], with the draft's title and notes as the message. */
    fun shareIntent(context: Context, draft: Draft, files: List<File>): Intent {
        val uris = files.map(ShareProvider::uriFor)
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
        }
        message(draft)?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
        if (draft.title.isNotBlank()) send.putExtra(Intent.EXTRA_SUBJECT, draft.title.trim())
        // The grant travels with the clip data, through the chooser to the app that is picked.
        val clip = ClipData.newUri(context.contentResolver, files[0].name, uris[0])
        for (uri in uris.drop(1)) clip.addItem(ClipData.Item(uri))
        send.clipData = clip
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, context.getString(R.string.share_title))
    }

    /** What goes with a shared picture: the title and notes, or null if there are neither. */
    fun message(draft: Draft): String? =
        listOf(draft.title.trim(), draft.notes.trim()).filter { it.isNotEmpty() }.joinToString("\n\n").ifEmpty { null }

    /** Saves the picture to Pictures/Draftpad. Android 10 and newer only, where it needs no permission. */
    fun saveToPictures(context: Context, draft: Draft, background: Bitmap?): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val png = picture(draft, background).png
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${Names.slug(draft.title)}-${System.currentTimeMillis() / 1000}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Draftpad")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { it.write(png) } ?: throw IOException("No stream for $uri")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
