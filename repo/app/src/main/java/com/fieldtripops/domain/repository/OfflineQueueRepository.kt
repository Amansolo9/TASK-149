package com.fieldtripops.domain.repository

import com.fieldtripops.domain.model.ExportPackage
import com.fieldtripops.domain.model.OfflineQueueItem
import com.fieldtripops.domain.model.QueueItemState
import java.time.Instant

interface OfflineQueueRepository {
    suspend fun enqueue(item: OfflineQueueItem)
    suspend fun findById(id: String): OfflineQueueItem?
    suspend fun findDue(state: QueueItemState, now: Instant): List<OfflineQueueItem>
    suspend fun updateState(id: String, state: QueueItemState, at: Instant, err: String?, bumpAttempts: Boolean)
}

interface ExportPackageRepository {
    suspend fun save(pkg: ExportPackage)
    suspend fun findById(id: String): ExportPackage?
    suspend fun getByUser(userId: String): List<ExportPackage>
    suspend fun getAll(): List<ExportPackage>
}
