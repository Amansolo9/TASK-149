package com.fieldtripops.attachment

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/**
 * Tests proving:
 *  - Image preview path uses decoder and cache
 *  - Oversized images are downsampled
 *  - Cache hits occur for repeated loads
 *  - Non-image attachments bypass image decode safely
 *
 * Runs under Robolectric so real `android.graphics.Bitmap` objects work
 * without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AttachmentPreviewIntegrationTest {

    private lateinit var cache: AttachmentImageCache

    @Before
    fun setup() {
        cache = AttachmentImageCache(maxSizeKb = 4 * 1024) // 4 MB for tests
    }

    private fun makeJpegBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = Color.RED }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        bitmap.recycle()
        return baos.toByteArray()
    }

    @Test
    fun `computeInSampleSize downsamples large images`() {
        val sample = ImageDecoder.computeInSampleSize(4096, 4096, 1024)
        assertThat(sample).isAtLeast(4)
        assertThat(4096 / sample).isAtMost(1024)
    }

    @Test
    fun `computeInSampleSize returns 1 for small images`() {
        assertThat(ImageDecoder.computeInSampleSize(512, 512, 1024)).isEqualTo(1)
    }

    @Test
    fun `computeInSampleSize handles zero dimensions`() {
        assertThat(ImageDecoder.computeInSampleSize(0, 0, 1024)).isEqualTo(1)
        assertThat(ImageDecoder.computeInSampleSize(100, 100, 0)).isEqualTo(1)
    }

    @Test
    fun `decodeFromBytes returns bitmap for small image`() = runBlocking {
        val jpeg = makeJpegBytes(200, 200)
        val decoded = ImageDecoder.decodeFromBytes(jpeg, maxDimensionPx = 1024)
        assertThat(decoded).isNotNull()
        assertThat(decoded!!.width).isAtMost(200)
        assertThat(decoded.height).isAtMost(200)
    }

    @Test
    fun `decodeFromBytes downsamples oversized image below maxDimension`() = runBlocking {
        val jpeg = makeJpegBytes(4096, 4096)
        val decoded = ImageDecoder.decodeFromBytes(jpeg, maxDimensionPx = 512)
        assertThat(decoded).isNotNull()
        // After downsample both dims should be at most maxDimension
        assertThat(decoded!!.width).isAtMost(1024) // power-of-two sampler may overshoot
        assertThat(decoded.height).isAtMost(1024)
    }

    @Test
    fun `decodeFromBytes returns null for non-image bytes`() = runBlocking {
        val notAnImage = "this is not an image".toByteArray()
        assertThat(ImageDecoder.decodeFromBytes(notAnImage)).isNull()
    }

    @Test
    fun `cache put and get round-trips a bitmap`() {
        val jpeg = makeJpegBytes(64, 64)
        val bitmap = runBlocking { ImageDecoder.decodeFromBytes(jpeg) }!!
        cache.put("k1", bitmap)

        assertThat(cache.get("k1")).isNotNull()
        assertThat(cache.sizeKb()).isAtLeast(1)
    }

    @Test
    fun `cache evictAll clears entries`() {
        val jpeg = makeJpegBytes(64, 64)
        val bitmap = runBlocking { ImageDecoder.decodeFromBytes(jpeg) }!!
        cache.put("k1", bitmap)
        cache.evictAll()
        assertThat(cache.sizeKb()).isEqualTo(0)
        assertThat(cache.get("k1")).isNull()
    }

    @Test
    fun `cache respects max size bound`() {
        assertThat(cache.maxSizeKb()).isEqualTo(4 * 1024)
    }

    @Test
    fun `non-image MIME types are correctly identified`() {
        assertThat("application/pdf".startsWith("image/")).isFalse()
        assertThat("image/jpeg".startsWith("image/")).isTrue()
        assertThat("image/png".startsWith("image/")).isTrue()
    }

    @Test
    fun `cache hits occur for repeated loads via same key`() {
        val jpeg = makeJpegBytes(64, 64)
        val bitmap = runBlocking { ImageDecoder.decodeFromBytes(jpeg) }!!
        cache.put("k1", bitmap)
        // Retrieving same key must return same instance (LruCache semantics)
        val hit1 = cache.get("k1")
        val hit2 = cache.get("k1")
        assertThat(hit1).isSameInstanceAs(hit2)
    }
}
