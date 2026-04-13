package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-user deletion workflow record. Every request goes through Requested →
 * (Approved|Rejected) → Executed / Failed. Administrator approval is required
 * for non-self deletions; self-requests may auto-approve per policy.
 */
@Entity(
    tableName = "deletion_requests",
    indices = [
        Index(value = ["targetUserId"]),
        Index(value = ["requestedBy"]),
        Index(value = ["state"])
    ]
)
data class DeletionRequestEntity(
    @PrimaryKey val id: String,
    val targetUserId: String,
    val requestedBy: String,
    val requestedAt: Long,
    val reason: String?,
    val state: String, // Requested, Approved, Rejected, Executed, Failed
    val approvedBy: String?,
    val approvedAt: Long?,
    val executedBy: String?,
    val executedAt: Long?,
    val failureReason: String?,
    val scope: String // HARD_DELETE or ANONYMIZE
)
