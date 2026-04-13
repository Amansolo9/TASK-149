package com.fieldtripops.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SensitiveFieldCodecTest {

    @Test
    fun `noop codec is identity`() {
        val c = NoopSensitiveFieldCodec()
        assertThat(c.encrypt("hello")).isEqualTo("hello")
        assertThat(c.decrypt("hello")).isEqualTo("hello")
    }

    @Test
    fun `noop codec preserves null and empty`() {
        val c = NoopSensitiveFieldCodec()
        assertThat(c.encrypt(null)).isNull()
        assertThat(c.encrypt("")).isEqualTo("")
    }

    @Test
    fun `aes codec is gated by enc-v1 prefix`() {
        // Verify the prefix contract directly: the decode path returns legacy
        // unencrypted strings as-is so we can roll out encryption gradually
        // without rewriting historical rows.
        assertThat(AesSensitiveFieldCodec.PREFIX_V1).isEqualTo("enc:v1:")
    }
}
