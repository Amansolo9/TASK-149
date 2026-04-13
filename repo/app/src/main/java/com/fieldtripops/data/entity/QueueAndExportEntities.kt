package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "offline_queue_items",
    indices = [Index(value = ["state", "scheduledAt"])]
)
data class OfflineQueueItemEntity(
    @PrimaryKey val id: String,
    val jobType: String,
    val payloadJson: String,
    val state: String,
    val attempts: Int,
    val maxAttempts: Int,
    val scheduledAt: Long,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "export_packages",
    indices = [Index(value = ["generatedBy", "generatedAt"])]
)
data class ExportPackageEntity(
    @PrimaryKey val id: String,
    val exportType: String,
    val filePath: String,
    val rowCount: Int,
    val checksum: String,
    val generatedBy: String,
    val generatedAt: Long,
    val maskingProfile: String
)
