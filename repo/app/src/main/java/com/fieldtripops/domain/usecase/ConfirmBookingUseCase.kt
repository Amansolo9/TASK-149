package com.fieldtripops.domain.usecase

import androidx.room.withTransaction
import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.domain.booking.FeeCalculator
import com.fieldtripops.domain.model.BookingOrder
import com.fieldtripops.domain.model.BookingState
import com.fieldtripops.domain.model.FeeItem
import com.fieldtripops.domain.model.QuotaOperation
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.repository.FeeItemRepository
import com.fieldtripops.domain.repository.InventoryRepository
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import java.time.Instant

/**
 * Confirms a PendingConfirmation booking. Per PRD §9.2 and §9.4:
 *  - Requires Agent or Administrator role (enforced via SessionManager + AccessControl).
 *  - Acting identity is taken from the authenticated session, never from caller args.
 *  - Fee items, quota CONFIRM, and state transition occur in a single Room transaction.
 */
class ConfirmBookingUseCase(
    private val database: FieldTripDatabase,
    private val bookingRepository: BookingRepository,
    private val inventoryRepository: InventoryRepository,
    private val feeItemRepository: FeeItemRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) {

    sealed class Result {
        data class Confirmed(val order: BookingOrder, val total: java.math.BigDecimal) : Result()
        object OrderNotFound : Result()
        data class InvalidState(val currentState: BookingState) : Result()
        data class FeeValidationFailed(val errors: List<String>) : Result()
    }

    suspend fun execute(orderId: String, feeItems: List<FeeItem>): Result {
        val session = sessionManager.requireSession()
        AccessControl.requireAgentOrAdmin(session, operation = "booking.confirm")

        val order = bookingRepository.findById(orderId) ?: return Result.OrderNotFound
        if (!BookingState.canTransition(order.state, BookingState.Booked)) {
            return Result.InvalidState(order.state)
        }

        when (val v = FeeCalculator.validate(feeItems)) {
            is FeeCalculator.ValidationResult.Invalid ->
                return Result.FeeValidationFailed(v.reasons)
            else -> {}
        }

        val breakdown = FeeCalculator.calculate(feeItems)
        val actor = session.userId

        val confirmedOrder = database.withTransaction {
            val now = Instant.now()
            feeItemRepository.replaceForBooking(orderId, feeItems)
            inventoryRepository.applyQuotaOperation(
                slotId = order.inventorySlotId,
                operation = QuotaOperation.CONFIRM,
                units = order.partySize,
                bookingOrderId = orderId,
                actor = actor,
                reason = "Booking confirmation"
            )
            bookingRepository.updateState(orderId, BookingState.Booked, now, actor, null)
            // Write authoritative paidTotal derived from the confirmed fee items.
            // ApproveRefundUseCase reads THIS value — callers cannot spoof it.
            val paidCents = breakdown.grandTotal
                .setScale(2, java.math.RoundingMode.HALF_EVEN)
                .movePointRight(2).toLong()
            bookingRepository.setPaidTotal(orderId, paidCents)
            bookingRepository.findById(orderId)!!
        }

        auditLogger.log(actor, AuditAction.BOOKING_CONFIRMED, "BookingOrder", orderId,
            "Confirmed total $${breakdown.grandTotal} by ${session.displayName}")
        auditLogger.log(actor, AuditAction.FEE_ITEMS_UPDATED, "BookingOrder", orderId,
            "Base=${breakdown.baseFareTotal}, Taxes=${breakdown.taxFeeTotal}, Adj=${breakdown.adjustmentTotal}")

        return Result.Confirmed(confirmedOrder, breakdown.grandTotal)
    }
}
