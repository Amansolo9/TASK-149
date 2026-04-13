package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "consent_records",
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
data class ConsentRecordEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val consentType: String,
    val granted: Boolean,
    val grantedAt: Long,
    val revokedAt: Long?,
    val policyVersion: String
)
