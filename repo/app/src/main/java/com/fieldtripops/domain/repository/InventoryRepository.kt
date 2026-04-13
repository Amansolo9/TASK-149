package com.fieldtripops.domain.repository

import com.fieldtripops.domain.model.InventorySlot
import com.fieldtripops.domain.model.QuotaLedgerEntry
import com.fieldtripops.domain.model.QuotaOperation

interface InventoryRepository {
    suspend fun findSlot(slotId: String): InventorySlot?
    suspend fun findAvailableSlots(itineraryType: String, partySize: Int): List<InventorySlot>
    suspend fun saveSlot(slot: InventorySlot)

    /**
     * Transactionally applies a quota operation:
     * - RESERVE: increment reservedCount
     * - CONFIRM: decrement reservedCount, increment bookedCount
     * - RELEASE: decrement reservedCount OR bookedCount depending on prior state
     * - EXCEPTION: records exception booking without normal availability check
     * Appends an audit entry to the quota ledger.
     * @throws IllegalStateException on negative availability
     */
    suspend fun applyQuotaOperation(
        slotId: String,
        operation: QuotaOperation,
        units: Int,
        bookingOrderId: String,
        actor: String,
        reason: String? = null
    ): QuotaLedgerEntry

    suspend fun getLedgerForBooking(bookingOrderId: String): List<QuotaLedgerEntry>
}
