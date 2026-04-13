package com.fieldtripops.attachment

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageDecoderTest {

    @Test
    fun `inSampleSize 1 when source already fits`() {
        val s = ImageDecoder.computeInSampleSize(800, 600, 1024)
        assertThat(s).isEqualTo(1)
    }

    @Test
    fun `inSampleSize grows for oversize images`() {
        val s = ImageDecoder.computeInSampleSize(4000, 3000, 1024)
        // 4000/s <= 1024 => s >= 4
        assertThat(s).isAtLeast(4)
    }

    @Test
    fun `inSampleSize returns power of two`() {
        val s = ImageDecoder.computeInSampleSize(8000, 6000, 1024)
        assertThat(s and (s - 1)).isEqualTo(0)
    }

    @Test
    fun `degenerate inputs return 1`() {
        assertThat(ImageDecoder.computeInSampleSize(0, 0, 1024)).isEqualTo(1)
        assertThat(ImageDecoder.computeInSampleSize(100, 100, 0)).isEqualTo(1)
    }
}
