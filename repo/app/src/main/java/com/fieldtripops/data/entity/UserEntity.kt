package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
