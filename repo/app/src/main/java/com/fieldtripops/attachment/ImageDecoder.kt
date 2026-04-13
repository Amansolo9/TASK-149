package com.fieldtripops.attachment

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Decodes attachment images with explicit downsampling per PRD §18
 * ("large images downsampled with an LRU cache; peak additional image memory
 * under 20 MB"). The decoder:
 *
 *  1. Reads only image bounds first (inJustDecodeBounds=true) — never loads
 *     the full byte stream into memory.
 *  2. Computes an integer `inSampleSize` so the decoded bitmap's longest side
 *     fits within [maxDimensionPx]. Powers-of-two are chosen as BitmapFactory
 *     optimizes them.
 *  3. Decodes with ARGB_8888; [maxDimensionPx] = 1024 keeps a preview under
 *     ~4 MB (1024 × 1024 × 4 bytes).
 *
 * All IO happens on [Dispatchers.IO]. Callers must not invoke this from the
 * main thread.
 */
object ImageDecoder {

    const val DEFAULT_MAX_DIMENSION_PX = 1024

    /**
     * Exposed as `internal` for tests; production code should use [decodeFromPath].
     * Computes the smallest power-of-two `inSampleSize` such that the scaled
     * image fits under [maxDimensionPx] on both axes.
     */
    internal fun computeInSampleSize(
        srcWidth: Int, srcHeight: Int, maxDimensionPx: Int
    ): Int {
        if (srcWidth <= 0 || srcHeight <= 0 || maxDimensionPx <= 0) return 1
        var inSample = 1
        val halfW = srcWidth / 2
        val halfH = srcHeight / 2
        while ((halfW / inSample) >= maxDimensionPx &&
            (halfH / inSample) >= maxDimensionPx
        ) {
            inSample *= 2
        }
        // Also shrink aggressively when only one axis is too large.
        while (srcWidth / inSample > maxDimensionPx ||
            srcHeight / inSample > maxDimensionPx
        ) {
            inSample *= 2
        }
        return inSample
    }

    suspend fun decodeFromPath(
        path: String, maxDimensionPx: Int = DEFAULT_MAX_DIMENSION_PX
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeFile(path, opts)
    }

    suspend fun decodeFromBytes(
        bytes: ByteArray, maxDimensionPx: Int = DEFAULT_MAX_DIMENSION_PX
    ): Bitmap? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
