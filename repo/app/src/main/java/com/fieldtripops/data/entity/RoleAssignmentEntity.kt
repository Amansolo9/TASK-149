package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "role_assignments",
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
data class RoleAssignmentEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val role: String,
    val assignedAt: Long,
    val assignedBy: String
)
