package com.fieldtripops.domain.repository

import com.fieldtripops.attachment.PendingAttachment
import com.fieldtripops.domain.model.AttachmentRef

interface AttachmentRepository {
    suspend fun save(ref: AttachmentRef, data: ByteArray)
    suspend fun getRef(id: String): AttachmentRef?
    suspend fun getRefsForEntity(entityType: String, entityId: String): List<AttachmentRef>
    suspend fun delete(id: String)

    /**
     * Inserts attachment ref rows for [pending] within the caller's open Room
     * transaction. The byte payloads are written to disk only AFTER the
     * transaction successfully commits via [commitPayloads]. If the
     * transaction rolls back the row inserts are undone and no on-disk
     * artifacts exist.
     */
    suspend fun stageInTransaction(
        ownerEntityType: String,
        ownerEntityId: String,
        pending: List<PendingAttachment>
    ): List<AttachmentRef>

    /**
     * Writes the buffered bytes for previously staged attachments to disk.
     * Call this ONLY after the database transaction containing the staged
     * refs has successfully committed.
     */
    suspend fun commitPayloads(refs: List<AttachmentRef>, payloads: List<PendingAttachment>)
}
