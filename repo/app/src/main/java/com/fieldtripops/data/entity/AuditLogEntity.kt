package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["actor"]),
        Index(value = ["entityType", "entityId"]),
        Index(value = ["timestamp"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val actor: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val timestamp: Long,
    val details: String?,
    val checksum: String
)
