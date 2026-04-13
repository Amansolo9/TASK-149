package com.fieldtripops.domain.booking

import com.fieldtripops.domain.model.AttachmentRef
import java.time.Duration
import java.time.Instant

/**
 * Validates claim filing per PRD §9.6:
 * - Must be filed within 7 calendar days of trip end
 * - At least one proof attachment required
 * - Attachment size <= 10 MB, type photo or PDF
 */
object ClaimValidator {

    const val FILING_WINDOW_DAYS = 7L
    const val MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024
    val ALLOWED_MIME_TYPES = setOf(
        "image/jpeg", "image/jpg", "image/png", "image/heic", "image/webp",
        "application/pdf"
    )

    sealed class Result {
        object Valid : Result()
        data class Invalid(val errors: List<String>) : Result()
    }

    fun validate(
        tripEnd: Instant,
        filedAt: Instant,
        attachments: List<AttachmentRef>,
        description: String
    ): Result {
        val errors = mutableListOf<String>()

        val filingLimit = tripEnd.plus(Duration.ofDays(FILING_WINDOW_DAYS))
        if (filedAt.isAfter(filingLimit)) {
            errors += "Claim must be filed within $FILING_WINDOW_DAYS days of trip end"
        }

        if (attachments.isEmpty()) {
            errors += "At least one proof attachment is required"
        } else {
            attachments.forEach { att ->
                if (att.sizeBytes > MAX_ATTACHMENT_BYTES) {
                    errors += "Attachment '${att.fileName}' exceeds 10 MB limit"
                }
                if (!ALLOWED_MIME_TYPES.contains(att.mimeType.lowercase())) {
                    errors += "Attachment '${att.fileName}' has unsupported type ${att.mimeType}"
                }
            }
        }

        if (description.isBlank()) {
            errors += "Claim description is required"
        }

        return if (errors.isEmpty()) Result.Valid else Result.Invalid(errors)
    }
}
