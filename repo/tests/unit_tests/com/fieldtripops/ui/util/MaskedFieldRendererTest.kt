package com.fieldtripops.ui.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MaskedFieldRendererTest {

    @Test
    fun `maskPhone shows last 4 digits`() {
        assertThat(MaskedFieldRenderer.maskPhone("(555) 123-4567")).isEqualTo("(***) ***-4567")
    }

    @Test
    fun `maskPhone handles plain digits`() {
        assertThat(MaskedFieldRenderer.maskPhone("5551234567")).isEqualTo("(***) ***-4567")
    }

    @Test
    fun `maskPhone handles short input`() {
        assertThat(MaskedFieldRenderer.maskPhone("12")).isEqualTo("****")
    }

    @Test
    fun `maskEmail preserves first char and domain`() {
        assertThat(MaskedFieldRenderer.maskEmail("john@example.com")).isEqualTo("j***@example.com")
    }

    @Test
    fun `maskEmail handles single char before @`() {
        assertThat(MaskedFieldRenderer.maskEmail("j@test.com")).isEqualTo("j***@test.com")
    }

    @Test
    fun `maskEmail handles no @`() {
        assertThat(MaskedFieldRenderer.maskEmail("noemail")).isEqualTo("***")
    }

    @Test
    fun `maskGeneric default shows last 4`() {
        assertThat(MaskedFieldRenderer.maskGeneric("1234567890")).isEqualTo("******7890")
    }

    @Test
    fun `maskGeneric custom visible length`() {
        assertThat(MaskedFieldRenderer.maskGeneric("ABCDEFGH", 2)).isEqualTo("******GH")
    }

    @Test
    fun `maskGeneric short input returned as-is`() {
        assertThat(MaskedFieldRenderer.maskGeneric("AB", 4)).isEqualTo("AB")
    }

    @Test
    fun `maskName masks all but first char of each part`() {
        assertThat(MaskedFieldRenderer.maskName("John Doe")).isEqualTo("J*** D**")
    }

    @Test
    fun `maskName handles single character name`() {
        assertThat(MaskedFieldRenderer.maskName("J")).isEqualTo("J")
    }

    @Test
    fun `maskName handles empty string`() {
        assertThat(MaskedFieldRenderer.maskName("")).isEqualTo("***")
    }
}
