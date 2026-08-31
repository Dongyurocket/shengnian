package com.voiceink.app.ui.detail

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.voiceink.app.core.applyExifOrientation
import com.voiceink.app.core.readExifOrientation
import java.io.File

internal fun decodeFileThumbnail(path: String, maxEdge: Int = 480): Bitmap? {
    val file = File(path)
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    val orientation = readExifOrientation(path)
    return BitmapFactory.decodeFile(path, options)?.let { bitmap ->
        applyExifOrientation(bitmap, orientation)
    }
}

internal fun decodeUriThumbnail(
    resolver: ContentResolver,
    uri: Uri,
    maxEdge: Int = 480
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    val orientation = resolver.openInputStream(uri)?.use(::readExifOrientation)
        ?: android.media.ExifInterface.ORIENTATION_NORMAL
    return resolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)?.let { bitmap ->
            applyExifOrientation(bitmap, orientation)
        }
    }
}

private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
    val edge = maxOf(width, height)
    var sample = 1
    while (edge / sample > maxEdge) sample *= 2
    return sample
}
