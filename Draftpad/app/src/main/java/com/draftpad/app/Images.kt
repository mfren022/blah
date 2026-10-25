package com.draftpad.app

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Background images: reading what the user picks or shares, and keeping it with the draft. */
object Images {
    /** Background images are kept at most this many pixels on their long side. */
    const val MAX_SIDE = 2048

    /**
     * The image at [uri], the right way up, at most [maxSide] pixels on its long side and flattened
     * onto white (it is kept as a JPEG). Null if it can't be read.
     */
    fun read(resolver: ContentResolver, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.let { decoded ->
                val rotation = try {
                    resolver.openInputStream(uri)?.use(::exifRotation) ?: 0
                } catch (e: Exception) {
                    0
                }
                flatten(decoded, rotation, maxSide)
            }
        }
    } catch (e: Exception) {
        null
    }

    fun load(file: File): Bitmap? = if (file.isFile) BitmapFactory.decodeFile(file.path) else null

    fun saveJpeg(bitmap: Bitmap, file: File) =
        DraftStore.write(file) { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }

    private fun exifRotation(input: InputStream): Int =
        when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    /** [src] turned by [degrees], scaled to fit [maxSide] and drawn on white. */
    private fun flatten(src: Bitmap, degrees: Int, maxSide: Int): Bitmap {
        val turned = degrees == 90 || degrees == 270
        val w = if (turned) src.height else src.width
        val h = if (turned) src.width else src.height
        val scale = min(1f, maxSide.toFloat() / max(w, h))
        val out = Bitmap.createBitmap(max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt()), Bitmap.Config.ARGB_8888)
        val m = Matrix().apply {
            postTranslate(-src.width / 2f, -src.height / 2f)
            postRotate(degrees.toFloat())
            postTranslate(w / 2f, h / 2f)
            postScale(scale, scale)
        }
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        src.recycle()
        return out
    }
}
