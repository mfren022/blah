package io.github.mfren022.drivein

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL

/** Loads and caches video thumbnails for the library grid, notifications and Android Auto. */
object Thumbnails {
    private const val TARGET_WIDTH = 480
    private const val TARGET_HEIGHT = 270

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun load(context: Context, item: VideoItem, onLoaded: (Bitmap?) -> Unit) {
        cache.get(item.mediaId)?.let { return onLoaded(it) }
        val appContext = context.applicationContext
        Background.load({
            runCatching { decode(appContext, item) }.getOrNull()?.also { cache.put(item.mediaId, it) }
        }, onLoaded)
    }

    fun into(view: ImageView, item: VideoItem) {
        view.tag = item.mediaId
        view.setImageDrawable(null)
        load(view.context, item) { bitmap ->
            if (view.tag == item.mediaId) view.setImageBitmap(bitmap)
        }
    }

    private fun decode(context: Context, item: VideoItem): Bitmap? {
        val remote = item.thumbnailUri
        return when {
            remote != null && remote.scheme in setOf("http", "https") -> download(URL(remote.toString()))
            item.uri.scheme == ContentResolver.SCHEME_CONTENT ->
                context.contentResolver.loadThumbnail(item.uri, Size(TARGET_WIDTH, TARGET_HEIGHT), null)
            else -> null
        }
    }

    private fun download(url: URL): Bitmap? {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        val bytes = try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= TARGET_WIDTH) sampleSize *= 2
        return BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize },
        )
    }
}
