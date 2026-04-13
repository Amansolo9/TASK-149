package com.fieldtripops.domain.model

import java.time.Instant

/**
 * Append-only ledger of quota operations.
 * Each entry represents a reservation, confirmation, or release tied to a booking.
 */
data class QuotaLedgerEntry(
    val id: String,
    val inventorySlotId: String,
    val bookingOrderId: String,
    val operation: QuotaOperation,
    val units: Int,
    val actor: String,
    val timestamp: Instant,
    val reason: String?
)

enum class QuotaOperation {
    RESERVE,     // Pending Confirmation holds quota
    CONFIRM,     // Move from reserved to booked
    RELEASE,     // Return to available (cancel/auto-close)
    EXCEPTION    // Overbooking with agent override
}
