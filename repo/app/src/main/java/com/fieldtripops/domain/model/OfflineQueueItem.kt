package com.fieldtripops.domain.model

import java.time.Instant

data class OfflineQueueItem(
    val id: String,
    val jobType: String,
    val payloadJson: String,
    val state: QueueItemState,
    val attempts: Int,
    val maxAttempts: Int,
    val scheduledAt: Instant,
    val lastError: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

enum class QueueItemState {
    Pending, Running, Succeeded, Failed, RetryScheduled, Cancelled, Archived;

    companion object {
        private val TRANSITIONS: Map<QueueItemState, Set<QueueItemState>> = mapOf(
            Pending to setOf(Running, Cancelled),
            Running to setOf(Succeeded, Failed, RetryScheduled),
            RetryScheduled to setOf(Running, Failed),
            Failed to setOf(RetryScheduled, Cancelled),
            Succeeded to setOf(Archived),
            Cancelled to setOf(Archived),
            Archived to emptySet()
        )

        fun canTransition(from: QueueItemState, to: QueueItemState): Boolean =
            TRANSITIONS[from]?.contains(to) ?: false
    }
}

data class ExportPackage(
    val id: String,
    val exportType: String,
    val filePath: String,
    val rowCount: Int,
    val checksum: String,
    val generatedBy: String,
    val generatedAt: Instant,
    val maskingProfile: String
)
