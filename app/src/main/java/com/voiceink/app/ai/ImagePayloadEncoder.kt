package com.voiceink.app.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import com.voiceink.app.core.applyExifOrientation
import com.voiceink.app.core.readExifOrientation
import java.io.ByteArrayOutputStream
import java.io.File

/** 将私有目录中的图片压缩为视觉 API 可接受的 data URL。 */
object ImagePayloadEncoder {
    const val MAX_IMAGES = 4
    const val MAX_LONG_EDGE = 1_600
    const val JPEG_QUALITY = 85
    const val MAX_TOTAL_BASE64_BYTES = 8 * 1024 * 1024

    fun encode(file: File, _originalMimeType: String): LlmImage? {
        if (!file.isFile || file.length() <= 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val oriented = applyExifOrientation(bitmap, readExifOrientation(file.absolutePath))
        val prepared = flattenAlpha(oriented)
        return try {
            val bytes = ByteArrayOutputStream()
            if (!prepared.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bytes)) return null
            LlmImage("image/jpeg", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP))
        } finally {
            prepared.recycle()
        }
    }

    fun encodeAll(files: List<Pair<File, String>>): List<LlmImage> {
        val result = mutableListOf<LlmImage>()
        var total = 0
        for ((file, mime) in files.take(MAX_IMAGES)) {
            val image = encode(file, mime) ?: continue
            if (total + image.base64.length > MAX_TOTAL_BASE64_BYTES) break
            result += image
            total += image.base64.length
        }
        return result
    }

    private fun flattenAlpha(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(flattened).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        bitmap.recycle()
        return flattened
    }

    private fun sampleSize(width: Int, height: Int): Int {
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / sample > MAX_LONG_EDGE) sample *= 2
        return sample
    }
}
