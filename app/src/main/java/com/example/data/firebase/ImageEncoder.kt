package com.example.data.firebase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ImageEncoder {
    private const val DATA_URI_PREFIX = "data:image/jpeg;base64,"
    private val bitmapCache = LruCache<Int, ImageBitmap>(32)

    suspend fun uriToCompressedDataUri(
        context: Context,
        uri: Uri,
        maxDimensionPx: Int = 640,
        maxChars: Int = 200_000
    ): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return@withContext null

            var sampleSize = 1
            while (max(boundsOpts.outWidth / sampleSize, boundsOpts.outHeight / sampleSize) > maxDimensionPx * 2) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                ?: return@withContext null

            val scale = maxDimensionPx.toFloat() / max(decoded.width, decoded.height).toFloat()
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).roundToInt().coerceAtLeast(1),
                    (decoded.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }

            var quality = 72
            var encoded: String
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                encoded = "$DATA_URI_PREFIX$b64"
                quality -= 12
            } while (encoded.length > maxChars && quality >= 24)

            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()

            if (encoded.length <= maxChars) encoded else null
        } catch (_: Exception) {
            null
        }
    }

    fun decodeDataUriToImageBitmap(dataUri: String?): ImageBitmap? {
        if (dataUri.isNullOrBlank() || !dataUri.startsWith("data:image/")) return null
        val key = dataUri.hashCode()
        bitmapCache.get(key)?.let { return it }
        return try {
            val commaIndex = dataUri.indexOf(',')
            if (commaIndex < 0) return null
            val b64 = dataUri.substring(commaIndex + 1)
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            bitmap.asImageBitmap().also { bitmapCache.put(key, it) }
        } catch (_: Exception) {
            null
        }
    }
}
