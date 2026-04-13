package com.fieldtripops.data.repository

import com.fieldtripops.data.dao.ExportPackageDao
import com.fieldtripops.data.dao.OfflineQueueItemDao
import com.fieldtripops.data.entity.ExportPackageEntity
import com.fieldtripops.data.entity.OfflineQueueItemEntity
import com.fieldtripops.domain.model.ExportPackage
import com.fieldtripops.domain.model.OfflineQueueItem
import com.fieldtripops.domain.model.QueueItemState
import com.fieldtripops.domain.repository.ExportPackageRepository
import com.fieldtripops.domain.repository.OfflineQueueRepository
import java.time.Instant

class OfflineQueueRepositoryImpl(
    private val dao: OfflineQueueItemDao
) : OfflineQueueRepository {

    override suspend fun enqueue(item: OfflineQueueItem) {
        dao.upsert(item.toEntity())
    }

    override suspend fun findById(id: String): OfflineQueueItem? = dao.findById(id)?.toDomain()

    override suspend fun findDue(state: QueueItemState, now: Instant): List<OfflineQueueItem> =
        dao.findDue(state.name, now.toEpochMilli()).map { it.toDomain() }

    override suspend fun updateState(
        id: String, state: QueueItemState, at: Instant, err: String?, bumpAttempts: Boolean
    ) {
        dao.updateState(id, state.name, at.toEpochMilli(), err, if (bumpAttempts) 1 else 0)
    }

    private fun OfflineQueueItemEntity.toDomain() = OfflineQueueItem(
        id = id, jobType = jobType, payloadJson = payloadJson,
        state = QueueItemState.valueOf(state),
        attempts = attempts, maxAttempts = maxAttempts,
        scheduledAt = Instant.ofEpochMilli(scheduledAt),
        lastError = lastError,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )

    private fun OfflineQueueItem.toEntity() = OfflineQueueItemEntity(
        id = id, jobType = jobType, payloadJson = payloadJson, state = state.name,
        attempts = attempts, maxAttempts = maxAttempts,
        scheduledAt = scheduledAt.toEpochMilli(),
        lastError = lastError,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli()
    )
}

class ExportPackageRepositoryImpl(
    private val dao: ExportPackageDao
) : ExportPackageRepository {

    override suspend fun save(pkg: ExportPackage) {
        dao.insert(pkg.toEntity())
    }

    override suspend fun findById(id: String): ExportPackage? = dao.findById(id)?.toDomain()

    override suspend fun getByUser(userId: String): List<ExportPackage> =
        dao.getByUser(userId).map { it.toDomain() }

    override suspend fun getAll(): List<ExportPackage> = dao.getAll().map { it.toDomain() }

    private fun ExportPackageEntity.toDomain() = ExportPackage(
        id = id, exportType = exportType, filePath = filePath,
        rowCount = rowCount, checksum = checksum,
        generatedBy = generatedBy,
        generatedAt = Instant.ofEpochMilli(generatedAt),
        maskingProfile = maskingProfile
    )

    private fun ExportPackage.toEntity() = ExportPackageEntity(
        id = id, exportType = exportType, filePath = filePath,
        rowCount = rowCount, checksum = checksum,
        generatedBy = generatedBy,
        generatedAt = generatedAt.toEpochMilli(),
        maskingProfile = maskingProfile
    )
}
