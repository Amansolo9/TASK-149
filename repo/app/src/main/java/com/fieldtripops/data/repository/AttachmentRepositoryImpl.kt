package com.fieldtripops.data.repository

import com.fieldtripops.attachment.AttachmentStorage
import com.fieldtripops.attachment.PendingAttachment
import com.fieldtripops.data.dao.AttachmentRefDao
import com.fieldtripops.data.entity.AttachmentRefEntity
import com.fieldtripops.domain.model.AttachmentRef
import com.fieldtripops.domain.repository.AttachmentRepository
import java.time.Instant

class AttachmentRepositoryImpl(
    private val attachmentRefDao: AttachmentRefDao,
    private val attachmentStorage: AttachmentStorage
) : AttachmentRepository {

    override suspend fun save(ref: AttachmentRef, data: ByteArray) {
        val storagePath = attachmentStorage.store(ref.id, data)
        val entityWithPath = ref.copy(storagePath = storagePath)
        attachmentRefDao.insert(entityWithPath.toEntity())
    }

    override suspend fun getRef(id: String): AttachmentRef? =
        attachmentRefDao.getById(id)?.toDomain()

    override suspend fun getRefsForEntity(entityType: String, entityId: String): List<AttachmentRef> =
        attachmentRefDao.getByOwner(entityType, entityId).map { it.toDomain() }

    override suspend fun delete(id: String) {
        val ref = attachmentRefDao.getById(id)
        if (ref != null) {
            attachmentStorage.delete(ref.storagePath)
            attachmentRefDao.deleteById(id)
        }
    }

    /**
     * Stage refs INSIDE the calling Room transaction. The storage path is the
     * deterministic per-id path that `commitPayloads` will write to. If the
     * outer transaction rolls back, the inserted ref rows are undone with it
     * and no on-disk artifact ever existed (we deferred the disk write).
     */
    override suspend fun stageInTransaction(
        ownerEntityType: String,
        ownerEntityId: String,
        pending: List<PendingAttachment>
    ): List<AttachmentRef> {
        val now = Instant.now()
        return pending.map { p ->
            val storagePath = attachmentStorage.pathFor(p.id)
            val ref = AttachmentRef(
                id = p.id,
                ownerEntityType = ownerEntityType,
                ownerEntityId = ownerEntityId,
                fileName = p.fileName,
                mimeType = p.mimeType,
                storagePath = storagePath,
                sizeBytes = p.sizeBytes,
                createdAt = now
            )
            attachmentRefDao.insert(ref.toEntity())
            ref
        }
    }

    override suspend fun commitPayloads(
        refs: List<AttachmentRef>, payloads: List<PendingAttachment>
    ) {
        val byId = payloads.associateBy { it.id }
        for (ref in refs) {
            val payload = byId[ref.id]
                ?: error("Internal: no buffered payload for ref ${ref.id}")
            attachmentStorage.store(ref.id, payload.data)
        }
    }

    private fun AttachmentRefEntity.toDomain(): AttachmentRef = AttachmentRef(
        id = id, ownerEntityType = ownerEntityType, ownerEntityId = ownerEntityId,
        fileName = fileName, mimeType = mimeType, storagePath = storagePath,
        sizeBytes = sizeBytes, createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun AttachmentRef.toEntity(): AttachmentRefEntity = AttachmentRefEntity(
        id = id, ownerEntityType = ownerEntityType, ownerEntityId = ownerEntityId,
        fileName = fileName, mimeType = mimeType, storagePath = storagePath,
        sizeBytes = sizeBytes, createdAt = createdAt.toEpochMilli()
    )
}
