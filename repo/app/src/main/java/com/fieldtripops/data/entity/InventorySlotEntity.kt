package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_slots",
    indices = [Index(value = ["itineraryType", "startDateEpochDay"])]
)
data class InventorySlotEntity(
    @PrimaryKey val id: String,
    val itineraryType: String,
    val serviceName: String,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long,
    val totalQuota: Int,
    val reservedCount: Int,
    val bookedCount: Int,
    val allowExceptionBooking: Boolean
)
