package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "refund_decisions",
    indices = [Index(value = ["bookingOrderId", "decidedAt"])]
)
data class RefundDecisionEntity(
    @PrimaryKey val id: String,
    val bookingOrderId: String,
    val paidTotalCents: Long,
    val refundAmountCents: Long,
    val refundPercent: Int,
    val ruleUsed: String,
    val approverUserId: String,
    val approverName: String,
    val decidedAt: Long,
    val manualOverrideReason: String?
)
