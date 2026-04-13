package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reschedule_requests",
    indices = [Index(value = ["bookingOrderId"])]
)
data class RescheduleRequestEntity(
    @PrimaryKey val id: String,
    val bookingOrderId: String,
    val requestedBy: String,
    val requestedAt: Long,
    val originalStartDateEpochDay: Long,
    val originalEndDateEpochDay: Long,
    val newStartDateEpochDay: Long,
    val newEndDateEpochDay: Long,
    val exceptionReason: String?,
    val approvedBy: String?,
    val approvedAt: Long?,
    val status: String
)
