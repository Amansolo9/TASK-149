package com.fieldtripops.data.repository

import com.fieldtripops.data.dao.TransactionCheckpointDao
import com.fieldtripops.data.entity.TransactionCheckpointEntity
import com.fieldtripops.domain.model.TransactionCheckpoint
import com.fieldtripops.domain.repository.CheckpointRepository
import java.time.Instant
import java.util.UUID

class CheckpointRepositoryImpl(
    private val dao: TransactionCheckpointDao
) : CheckpointRepository {

    override suspend fun createCheckpoint(
        label: String, entityType: String, entityId: String, snapshotJson: String
    ): TransactionCheckpoint {
        val checkpoint = TransactionCheckpoint(
            id = UUID.randomUUID().toString(), label = label,
            entityType = entityType, entityId = entityId,
            snapshotJson = snapshotJson, createdAt = Instant.now(),
            rolledBack = false
        )
        dao.insert(checkpoint.toEntity())
        return checkpoint
    }

    override suspend fun findLastValid(entityType: String, entityId: String): TransactionCheckpoint? =
        dao.findLastValid(entityType, entityId)?.toDomain()

    override suspend fun markRolledBack(checkpointId: String) {
        dao.markRolledBack(checkpointId)
    }

    override suspend fun getHistory(entityType: String, entityId: String): List<TransactionCheckpoint> =
        dao.getByEntity(entityType, entityId).map { it.toDomain() }

    private fun TransactionCheckpointEntity.toDomain() = TransactionCheckpoint(
        id = id, label = label, entityType = entityType, entityId = entityId,
        snapshotJson = snapshotJson, createdAt = Instant.ofEpochMilli(createdAt),
        rolledBack = rolledBack
    )

    private fun TransactionCheckpoint.toEntity() = TransactionCheckpointEntity(
        id = id, label = label, entityType = entityType, entityId = entityId,
        snapshotJson = snapshotJson, createdAt = createdAt.toEpochMilli(),
        rolledBack = rolledBack
    )
}
