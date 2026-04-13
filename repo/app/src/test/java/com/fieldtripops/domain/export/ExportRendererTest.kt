package com.fieldtripops.domain.export

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExportRendererTest {

    @Test
    fun `csv has headers and rows`() {
        val r = ExportRenderer.renderCsv(
            headers = listOf("BookingId", "State"),
            rows = listOf(listOf("b1", "Booked"), listOf("b2", "Cancelled")),
            profile = ExportRenderer.MaskingProfile.ADMINISTRATOR
        )
        assertThat(r.csvContent.lines().first()).isEqualTo("BookingId,State")
        assertThat(r.rowCount).isEqualTo(2)
    }

    @Test
    fun `phone masked for non-admin profile`() {
        val r = ExportRenderer.renderCsv(
            headers = listOf("Phone"),
            rows = listOf(listOf("(555) 123-4567")),
            profile = ExportRenderer.MaskingProfile.AGENT
        )
        assertThat(r.csvContent).contains("(***) ***-4567")
    }

    @Test
    fun `admin profile shows full phone`() {
        val r = ExportRenderer.renderCsv(
            headers = listOf("Phone"),
            rows = listOf(listOf("(555) 123-4567")),
            profile = ExportRenderer.MaskingProfile.ADMINISTRATOR
        )
        assertThat(r.csvContent).contains("(555) 123-4567")
    }

    @Test
    fun `csv escapes commas with quotes`() {
        val r = ExportRenderer.renderCsv(
            headers = listOf("Description"),
            rows = listOf(listOf("Hello, World")),
            profile = ExportRenderer.MaskingProfile.ADMINISTRATOR
        )
        assertThat(r.csvContent).contains("\"Hello, World\"")
    }

    @Test
    fun `checksum is deterministic for same input`() {
        val args = Triple(
            listOf("A"),
            listOf(listOf("x")),
            ExportRenderer.MaskingProfile.ADMINISTRATOR
        )
        val r1 = ExportRenderer.renderCsv(args.first, args.second, args.third)
        val r2 = ExportRenderer.renderCsv(args.first, args.second, args.third)
        assertThat(r1.checksum).isEqualTo(r2.checksum)
    }

    @Test
    fun `email masked for traveler profile`() {
        val r = ExportRenderer.renderCsv(
            headers = listOf("Email"),
            rows = listOf(listOf("john@example.com")),
            profile = ExportRenderer.MaskingProfile.TRAVELER
        )
        assertThat(r.csvContent).contains("j***@example.com")
    }

    @Test
    fun `requires matching row size`() {
        try {
            ExportRenderer.renderCsv(
                headers = listOf("A", "B"),
                rows = listOf(listOf("only-one")),
                profile = ExportRenderer.MaskingProfile.ADMINISTRATOR
            )
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
