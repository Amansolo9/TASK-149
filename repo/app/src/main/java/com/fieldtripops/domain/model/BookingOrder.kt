package com.fieldtripops.domain.model

import java.math.BigDecimal
import java.time.Instant

data class BookingOrder(
    val id: String,
    val itineraryId: String,
    val travelerId: String,
    val inventorySlotId: String,
    val partySize: Int,
    val state: BookingState,
    val createdAt: Instant,
    val updatedAt: Instant,
    val confirmedAt: Instant?,
    val confirmedBy: String?,
    val cancelledAt: Instant?,
    val cancelReason: String?,
    val lastActivityAt: Instant,

    /** Authoritative trip window. Drives refund bands and claim eligibility. */
    val tripStartAt: Instant,
    val tripEndAt: Instant,

    /** Authoritative paid total in USD (scale 2). Written only from confirmed fee items. */
    val paidTotal: BigDecimal
)
