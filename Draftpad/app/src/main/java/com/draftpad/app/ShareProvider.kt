package com.draftpad.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Hands exported files to the apps they are shared with, read-only: content://com.draftpad.app.share/<name>
 * serves cache/share/<name>. A small stand-in for AndroidX's FileProvider, which the app doesn't use.
 */
class ShareProvider : ContentProvider() {

    override fun onCreate() = true

    override fun getType(uri: Uri): String = typeOf(uri.lastPathSegment.orEmpty())

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Shared files are read-only")
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val file = fileFor(uri)
        val columns = projection?.filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }
            ?: listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns.toTypedArray(), 1).apply {
            addRow(columns.map { if (it == OpenableColumns.SIZE) file.length() else file.name })
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    private fun fileFor(uri: Uri): File {
        val name = uri.pathSegments.singleOrNull()
        // One plain file name, so a crafted URI can't reach outside the share folder.
        if (name == null || name.startsWith(".") || '/' in name) throw FileNotFoundException(uri.toString())
        val file = File(dir(context!!), name)
        if (!file.isFile) throw FileNotFoundException(uri.toString())
        return file
    }

    companion object {
        const val AUTHORITY = "com.draftpad.app.share"

        fun dir(context: Context) = File(context.cacheDir, "share")

        fun uriFor(file: File): Uri = Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(file.name).build()

        fun typeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "svg" -> "image/svg+xml"
            "jpg", "jpeg" -> "image/jpeg"
            "json" -> "application/json"
            "md" -> "text/markdown"
            else -> "application/octet-stream"
        }
    }
}
