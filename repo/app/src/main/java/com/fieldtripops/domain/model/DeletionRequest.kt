package com.fieldtripops.domain.model

import java.time.Instant

enum class DeletionState { Requested, Approved, Rejected, Executed, Failed }

enum class DeletionScope {
    /** Remove PII and dissociate user-owned writable records; keep audit. */
    ANONYMIZE,
    /** Remove user row and all dependent non-audit rows. */
    HARD_DELETE
}

data class DeletionRequest(
    val id: String,
    val targetUserId: String,
    val requestedBy: String,
    val requestedAt: Instant,
    val reason: String?,
    val state: DeletionState,
    val approvedBy: String?,
    val approvedAt: Instant?,
    val executedBy: String?,
    val executedAt: Instant?,
    val failureReason: String?,
    val scope: DeletionScope
)
