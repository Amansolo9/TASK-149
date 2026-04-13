package com.fieldtripops.domain.usecase

import androidx.room.withTransaction
import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.domain.model.BookingState
import com.fieldtripops.domain.model.QuotaOperation
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.repository.InventoryRepository
import java.time.Duration
import java.time.Instant

/**
 * Auto-closes PendingConfirmation orders older than 30 minutes of inactivity.
 * Releases held quota per PRD §9.3.
 */
class AutoCloseStalePendingUseCase(
    private val database: FieldTripDatabase,
    private val bookingRepository: BookingRepository,
    private val inventoryRepository: InventoryRepository,
    private val auditLogger: AuditLogger
) {

    companion object {
        val INACTIVITY_THRESHOLD: Duration = Duration.ofMinutes(30)
    }

    data class Result(val closedCount: Int)

    suspend fun execute(): Result {
        val now = Instant.now()
        val threshold = now.minus(INACTIVITY_THRESHOLD)
        val stale = bookingRepository.findPendingConfirmationOlderThan(threshold)

        var closed = 0
        for (order in stale) {
            database.withTransaction {
                // Double-check state inside transaction to avoid race.
                val fresh = bookingRepository.findById(order.id) ?: return@withTransaction
                if (fresh.state != BookingState.PendingConfirmation) return@withTransaction

                inventoryRepository.applyQuotaOperation(
                    slotId = fresh.inventorySlotId,
                    operation = QuotaOperation.RELEASE,
                    units = fresh.partySize,
                    bookingOrderId = fresh.id,
                    actor = "system",
                    reason = "Pending confirmation timeout"
                )
                bookingRepository.updateState(
                    fresh.id, BookingState.Closed, now, "system",
                    "Auto-closed: timeout after 30 minutes of inactivity"
                )
                closed++
            }
            auditLogger.log(
                actor = "system",
                action = AuditAction.BOOKING_AUTO_CLOSED,
                entityType = "BookingOrder",
                entityId = order.id,
                details = "Auto-closed after 30-minute inactivity timeout"
            )
        }

        return Result(closed)
    }
}
