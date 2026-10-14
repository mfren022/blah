package io.github.mfren022.drivein

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaDescription
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns

data class VideoItem(
    val mediaId: String,
    val uri: Uri,
    val title: String,
    val subtitle: String,
    val durationMs: Long = 0,
    val thumbnailUri: Uri? = null,
) {
    fun toDescription(context: Context): MediaDescription = MediaDescription.Builder()
        .setMediaId(mediaId)
        .setTitle(title)
        .setSubtitle(listOfNotNull(durationMs.takeIf { it > 0 }?.let(::formatDuration), subtitle.ifBlank { null }).joinToString(" · "))
        .setIconUri(VideoLibrary.resourceUri(context, R.drawable.ic_movie))
        .build()
}

/**
 * Where videos come from: the phone's own video collection, a few free sample films, and
 * anything opened by file or URL. Media IDs are prefixed with their source so Android Auto
 * can hand one back to [find] later.
 */
object VideoLibrary {
    const val ROOT = "root"
    const val CATEGORY_DEVICE = "category:device"
    const val CATEGORY_SAMPLES = "category:samples"

    private const val DEVICE_PREFIX = "device:"
    private const val SAMPLE_PREFIX = "sample:"
    private const val URI_PREFIX = "uri:"

    private const val SAMPLE_BASE = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/"

    private val samples = listOf(
        sample("BigBuckBunny", "Big Buck Bunny", "Blender Foundation", 596_000),
        sample("ElephantsDream", "Elephants Dream", "Blender Foundation", 653_000),
        sample("Sintel", "Sintel", "Blender Foundation", 888_000),
        sample("TearsOfSteel", "Tears of Steel", "Blender Foundation", 734_000),
        sample("ForBiggerBlazes", "For Bigger Blazes", "Google", 15_000),
        sample("ForBiggerEscapes", "For Bigger Escapes", "Google", 15_000),
    )

    private fun sample(file: String, title: String, studio: String, durationMs: Long) = VideoItem(
        mediaId = SAMPLE_PREFIX + file,
        uri = Uri.parse("$SAMPLE_BASE$file.mp4"),
        title = title,
        subtitle = studio,
        durationMs = durationMs,
        thumbnailUri = Uri.parse("${SAMPLE_BASE}images/$file.jpg"),
    )

    val videoPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(context: Context) =
        context.checkSelfPermission(videoPermission) == PackageManager.PERMISSION_GRANTED

    /** The items in a browse category. Queries MediaStore, so call it off the main thread. */
    fun children(context: Context, categoryId: String): List<VideoItem> = when (categoryId) {
        CATEGORY_DEVICE -> deviceVideos(context)
        CATEGORY_SAMPLES -> samples
        else -> emptyList()
    }

    /** The queue a media ID belongs to and its position in it, or null if it no longer exists. */
    fun find(context: Context, mediaId: String): Pair<List<VideoItem>, Int>? {
        val queue = when {
            mediaId.startsWith(DEVICE_PREFIX) -> deviceVideos(context)
            mediaId.startsWith(SAMPLE_PREFIX) -> samples
            mediaId.startsWith(URI_PREFIX) ->
                return listOf(itemForUri(context, Uri.parse(mediaId.removePrefix(URI_PREFIX)))) to 0
            else -> return null
        }
        val index = queue.indexOfFirst { it.mediaId == mediaId }
        return if (index >= 0) queue to index else null
    }

    fun search(context: Context, query: String): List<VideoItem> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        return (deviceVideos(context) + samples).filter { item ->
            val haystack = "${item.title} ${item.subtitle}".lowercase()
            words.all { it in haystack }
        }
    }

    /** A one-off item for a file or URL the user opened directly. */
    fun itemForUri(context: Context, uri: Uri): VideoItem {
        var title: String? = null
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) title = it.getString(0) }
            }
        }
        val name = title ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        return VideoItem(
            mediaId = URI_PREFIX + uri,
            uri = uri,
            title = name ?: uri.toString(),
            subtitle = uri.host ?: "",
        )
    }

    fun resourceUri(context: Context, resId: Int): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.packageName)
        .appendPath(context.resources.getResourceTypeName(resId))
        .appendPath(context.resources.getResourceEntryName(resId))
        .build()

    private fun deviceVideos(context: Context): List<VideoItem> {
        if (!hasPermission(context)) return emptyList()
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        )
        val items = mutableListOf<VideoItem>()
        context.contentResolver.query(
            collection, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val folderColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                items += VideoItem(
                    mediaId = DEVICE_PREFIX + id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = cursor.getString(titleColumn) ?: cursor.getString(nameColumn) ?: "Video $id",
                    subtitle = cursor.getString(folderColumn) ?: "",
                    durationMs = cursor.getLong(durationColumn),
                )
            }
        }
        return items
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
