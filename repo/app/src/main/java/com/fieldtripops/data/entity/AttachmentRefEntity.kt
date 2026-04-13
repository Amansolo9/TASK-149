package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachment_refs",
    indices = [Index(value = ["ownerEntityType", "ownerEntityId"])]
)
data class AttachmentRefEntity(
    @PrimaryKey val id: String,
    val ownerEntityType: String,
    val ownerEntityId: String,
    val fileName: String,
    val mimeType: String,
    val storagePath: String,
    val sizeBytes: Long,
    val createdAt: Long
)
