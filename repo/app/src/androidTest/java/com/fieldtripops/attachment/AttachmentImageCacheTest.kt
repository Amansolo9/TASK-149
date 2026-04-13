package com.fieldtripops.attachment

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachmentImageCacheTest {

    private fun bitmap(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    @Test
    fun `put then get returns same bitmap`() {
        val cache = AttachmentImageCache(maxSizeKb = 4 * 1024)
        val bm = bitmap(64, 64)
        cache.put("a", bm)
        assertThat(cache.get("a")).isSameInstanceAs(bm)
    }

    @Test
    fun `cache evicts older entries past size cap`() {
        // Small cap so one 2 MB bitmap fills most of it.
        val cache = AttachmentImageCache(maxSizeKb = 2 * 1024)
        val big = bitmap(512, 512)         // ~1 MB
        val bigger = bitmap(1024, 1024)    // ~4 MB — forces eviction
        cache.put("small", big)
        cache.put("big", bigger)
        // "small" should have been evicted to make room.
        assertThat(cache.get("small")).isNull()
    }

    @Test
    fun `size does not exceed configured cap`() {
        val cap = 1024
        val cache = AttachmentImageCache(maxSizeKb = cap)
        repeat(10) { cache.put("k$it", bitmap(256, 256)) }
        assertThat(cache.sizeKb()).isAtMost(cap)
    }
}
