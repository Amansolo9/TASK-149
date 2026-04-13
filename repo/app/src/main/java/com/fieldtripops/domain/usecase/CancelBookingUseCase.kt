package com.fieldtripops.domain.usecase

import androidx.room.withTransaction
import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.domain.model.BookingState
import com.fieldtripops.domain.model.QuotaOperation
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.repository.InventoryRepository
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import java.time.Instant

/**
 * Cancels a booking. Travelers may cancel their OWN bookings; Agents and
 * Administrators may cancel any booking. Quota release and state transition
 * occur in a single Room transaction.
 */
class CancelBookingUseCase(
    private val database: FieldTripDatabase,
    private val bookingRepository: BookingRepository,
    private val inventoryRepository: InventoryRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) {

    sealed class Result {
        object Cancelled : Result()
        object OrderNotFound : Result()
        data class InvalidState(val currentState: BookingState) : Result()
    }

    suspend fun execute(orderId: String, reason: String): Result {
        val session = sessionManager.requireSession()
        val order = bookingRepository.findById(orderId) ?: return Result.OrderNotFound

        AccessControl.requireOwnerOrRole(
            session, order.travelerId, "BookingOrder", orderId,
            Role.Agent, Role.Administrator
        )

        if (!BookingState.canTransition(order.state, BookingState.Cancelled)) {
            return Result.InvalidState(order.state)
        }

        val actor = session.userId
        database.withTransaction {
            val now = Instant.now()
            if (order.state == BookingState.PendingConfirmation ||
                order.state == BookingState.Booked ||
                order.state == BookingState.ReschedulePending
            ) {
                inventoryRepository.applyQuotaOperation(
                    slotId = order.inventorySlotId,
                    operation = QuotaOperation.RELEASE,
                    units = order.partySize,
                    bookingOrderId = orderId,
                    actor = actor,
                    reason = "Booking cancelled: $reason"
                )
            }
            bookingRepository.updateState(orderId, BookingState.Cancelled, now, actor, reason)
        }

        auditLogger.log(actor, AuditAction.BOOKING_CANCELLED, "BookingOrder", orderId,
            "Cancelled from ${order.state.name} by ${session.displayName}: $reason")
        return Result.Cancelled
    }
}
