package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fieldtripops.data.entity.ExportPackageEntity
import com.fieldtripops.data.entity.OfflineQueueItemEntity

@Dao
interface OfflineQueueItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: OfflineQueueItemEntity)

    @Query("SELECT * FROM offline_queue_items WHERE id = :id")
    suspend fun findById(id: String): OfflineQueueItemEntity?

    @Query("SELECT * FROM offline_queue_items WHERE state = :state AND scheduledAt <= :now ORDER BY scheduledAt ASC")
    suspend fun findDue(state: String, now: Long): List<OfflineQueueItemEntity>

    @Query("UPDATE offline_queue_items SET state = :state, updatedAt = :at, lastError = :err, attempts = attempts + :bumpAttempts WHERE id = :id")
    suspend fun updateState(id: String, state: String, at: Long, err: String?, bumpAttempts: Int)
}

@Dao
interface ExportPackageDao {
    @Insert
    suspend fun insert(pkg: ExportPackageEntity)

    @Query("SELECT * FROM export_packages WHERE id = :id")
    suspend fun findById(id: String): ExportPackageEntity?

    @Query("SELECT * FROM export_packages WHERE generatedBy = :userId ORDER BY generatedAt DESC")
    suspend fun getByUser(userId: String): List<ExportPackageEntity>

    @Query("SELECT * FROM export_packages ORDER BY generatedAt DESC")
    suspend fun getAll(): List<ExportPackageEntity>
}
