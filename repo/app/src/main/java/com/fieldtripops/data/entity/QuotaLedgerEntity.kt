package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quota_ledger",
    indices = [
        Index(value = ["inventorySlotId", "timestamp"]),
        Index(value = ["bookingOrderId"])
    ]
)
data class QuotaLedgerEntity(
    @PrimaryKey val id: String,
    val inventorySlotId: String,
    val bookingOrderId: String,
    val operation: String,
    val units: Int,
    val actor: String,
    val timestamp: Long,
    val reason: String?
)
