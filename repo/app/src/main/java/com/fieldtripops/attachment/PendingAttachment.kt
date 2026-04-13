package com.fieldtripops.attachment

import java.util.UUID

/**
 * An attachment whose bytes are buffered in memory but NOT yet persisted to disk
 * or registered in the AttachmentRefDao. Used by claim-filing flow to ensure
 * evidence and the parent claim row are committed atomically: the use case
 * stages the attachments, opens a Room transaction, inserts the claim row,
 * inserts attachment ref rows, and only then writes the bytes to disk.
 *
 * If the transaction rolls back, no disk artifacts remain because writes are
 * deferred until after the DB commit. If the disk write fails after commit,
 * the orphan ref is detected by `OrphanedAttachmentSweepWorker` (future) and
 * re-attempted from the buffered bytes which we keep until success is asserted.
 */
data class PendingAttachment(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val mimeType: String,
    val data: ByteArray
) {
    val sizeBytes: Long get() = data.size.toLong()

    override fun equals(other: Any?): Boolean = this === other ||
        (other is PendingAttachment && other.id == id)
    override fun hashCode(): Int = id.hashCode()
}
