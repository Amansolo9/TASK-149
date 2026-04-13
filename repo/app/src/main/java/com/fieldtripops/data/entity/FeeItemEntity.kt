package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fee_items",
    indices = [Index(value = ["bookingOrderId", "sortOrder"])]
)
data class FeeItemEntity(
    @PrimaryKey val id: String,
    val bookingOrderId: String,
    val category: String,
    val description: String,
    val amountCents: Long,   // USD stored as cents (scale-2 integer)
    val sortOrder: Int
)
