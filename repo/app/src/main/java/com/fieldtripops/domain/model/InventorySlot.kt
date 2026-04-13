package com.fieldtripops.domain.model

import java.time.LocalDate

/**
 * Local inventory unit per service/itinerary type and date window.
 * Quota availability = totalQuota - reservedCount - bookedCount.
 * Per PRD §9.2.
 */
data class InventorySlot(
    val id: String,
    val itineraryType: String,
    val serviceName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalQuota: Int,
    val reservedCount: Int,
    val bookedCount: Int,
    val allowExceptionBooking: Boolean
) {
    val availableCount: Int
        get() = (totalQuota - reservedCount - bookedCount).coerceAtLeast(0)

    fun canAccept(partySize: Int): Boolean = availableCount >= partySize
}
