package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_audits",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class SessionAuditEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val startedAt: Long,
    val lastActiveAt: Long,
    val endedAt: Long?,
    val endReason: String?
)
