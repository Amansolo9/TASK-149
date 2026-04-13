package com.fieldtripops.domain.booking

import com.fieldtripops.domain.model.AttachmentRef
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ClaimValidatorTest {

    private fun attachment(
        mime: String = "image/jpeg",
        size: Long = 1024L,
        name: String = "proof.jpg"
    ) = AttachmentRef(
        id = "a1", ownerEntityType = "ClaimTicket", ownerEntityId = "t1",
        fileName = name, mimeType = mime, storagePath = "/tmp/$name",
        sizeBytes = size, createdAt = Instant.now()
    )

    @Test
    fun `valid claim within window with one photo passes`() {
        val tripEnd = Instant.now().minus(Duration.ofDays(2))
        val r = ClaimValidator.validate(
            tripEnd = tripEnd, filedAt = Instant.now(),
            attachments = listOf(attachment()), description = "Missing service"
        )
        assertThat(r).isEqualTo(ClaimValidator.Result.Valid)
    }

    @Test
    fun `past 7 day window fails`() {
        val tripEnd = Instant.now().minus(Duration.ofDays(10))
        val r = ClaimValidator.validate(
            tripEnd, Instant.now(), listOf(attachment()), "desc"
        ) as ClaimValidator.Result.Invalid
        assertThat(r.errors.any { it.contains("7 days") }).isTrue()
    }

    @Test
    fun `no attachments fails`() {
        val tripEnd = Instant.now().minus(Duration.ofDays(1))
        val r = ClaimValidator.validate(tripEnd, Instant.now(), emptyList(), "desc")
                as ClaimValidator.Result.Invalid
        assertThat(r.errors.any { it.contains("proof attachment") }).isTrue()
    }

    @Test
    fun `over 10 MB attachment fails`() {
        val tripEnd = Instant.now().minus(Duration.ofDays(1))
        val oversize = attachment(size = 11L * 1024 * 1024)
        val r = ClaimValidator.validate(tripEnd, Instant.now(), listOf(oversize), "desc")
                as ClaimValidator.Result.Invalid
        assertThat(r.errors.any { it.contains("10 MB") }).isTrue()
    }

    @Test
    fun `unsupported mime type fails`() {
        val tripEnd = Instant.now().minus(Duration.ofDays(1))
        val bad = attachment(mime = "text/plain", name = "note.txt")
        val r = ClaimValidator.validate(tripEnd, Instant.now(), listOf(bad), "desc")
                as ClaimValidator.Result.Invalid
        assertThat(r.errors.any { it.contains("text/plain") }).isTrue()
    }

    @Test
    fun `pdf attachment allowed`() {
        val tripEnd = Instant.now().minus(Duration.ofDays(1))
        val pdf = attachment(mime = "application/pdf", name = "receipt.pdf")
        assertThat(ClaimValidator.validate(tripEnd, Instant.now(), listOf(pdf), "desc"))
            .isEqualTo(ClaimValidator.Result.Valid)
    }

    @Test
    fun `blank description fails`() {
        val tripEnd = Instant.now().minus(Duration.ofDays(1))
        val r = ClaimValidator.validate(tripEnd, Instant.now(), listOf(attachment()), "")
                as ClaimValidator.Result.Invalid
        assertThat(r.errors.any { it.contains("description") }).isTrue()
    }
}
