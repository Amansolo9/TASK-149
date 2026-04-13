package com.fieldtripops.attachment

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Bounded LRU cache for decoded attachment previews. Size is measured in
 * kilobytes of the underlying bitmap pixel data so the cache cannot blow past
 * its configured cap regardless of the number of items.
 *
 * Default cap: 16 MB — comfortably under the PRD §18 budget of "peak additional
 * image memory under 20 MB" even accounting for bitmaps currently in flight to
 * the view but not yet cached.
 */
class AttachmentImageCache(
    maxSizeKb: Int = DEFAULT_MAX_SIZE_KB
) {
    companion object {
        const val DEFAULT_MAX_SIZE_KB = 16 * 1024 // 16 MB
    }

    private val cache = object : LruCache<String, Bitmap>(maxSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            // byteCount is bytes; LruCache expects a unit consistent with maxSize —
            // we chose KB, so divide and round up.
            val kb = (value.byteCount + 1023) / 1024
            return kb.coerceAtLeast(1)
        }
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }
    fun remove(key: String) { cache.remove(key) }
    fun evictAll() { cache.evictAll() }

    /** Current used size in KB. */
    fun sizeKb(): Int = cache.size()
    /** Configured cap in KB. */
    fun maxSizeKb(): Int = cache.maxSize()
}
