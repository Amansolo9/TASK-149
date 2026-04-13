package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "booking_orders",
    indices = [
        Index(value = ["state", "updatedAt"]),
        Index(value = ["travelerId", "createdAt"]),
        Index(value = ["state", "lastActivityAt"]),
        Index(value = ["itineraryId"]),
        Index(value = ["inventorySlotId"])
    ]
)
data class BookingOrderEntity(
    @PrimaryKey val id: String,
    val itineraryId: String,
    val travelerId: String,
    val inventorySlotId: String,
    val partySize: Int,
    val state: String,
    val createdAt: Long,
    val updatedAt: Long,
    val confirmedAt: Long?,
    val confirmedBy: String?,
    val cancelledAt: Long?,
    val cancelReason: String?,
    val lastActivityAt: Long,

    /**
     * Authoritative trip start/end instants copied from the inventory slot at
     * booking submission time. These drive claim eligibility (§9.6 7-day
     * window) and refund band computation (§9.5), and must NOT change if the
     * booking row is updated for an unrelated reason.
     */
    val tripStartAt: Long,
    val tripEndAt: Long,

    /**
     * Total amount paid by the traveler, in USD cents. Written atomically at
     * confirm (as the sum of the stored fee line items) and never from caller
     * input. `ApproveRefundUseCase` reads THIS value rather than trusting a
     * caller-supplied `paidTotal`.
     */
    val paidTotalCents: Long
)
