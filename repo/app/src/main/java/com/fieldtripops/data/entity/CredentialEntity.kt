package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "credentials",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CredentialEntity(
    @PrimaryKey val userId: String,
    val passwordHash: String,
    val salt: String,
    val failedAttempts: Int,
    val lockedUntil: Long?,
    val lastLoginAt: Long?
)
