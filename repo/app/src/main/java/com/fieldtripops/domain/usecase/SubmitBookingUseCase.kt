package com.fieldtripops.domain.usecase

import androidx.room.withTransaction
import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.domain.booking.ItineraryValidator
import com.fieldtripops.domain.model.BookingOrder
import com.fieldtripops.domain.model.BookingState
import com.fieldtripops.domain.model.QuotaOperation
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.repository.InventoryRepository
import com.fieldtripops.domain.repository.ItineraryRepository
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Submits an itinerary for booking. Per PRD §9.1 / §9.2:
 *   - Looks up the inventory slot to verify availability BEFORE reserving.
 *   - Copies the slot's trip window into the new BookingOrder row as the
 *     authoritative source for claim eligibility and refund band computation.
 *   - Traveler can only submit for their OWN itinerary (Agent/Admin override).
 *   - Inventory reserve + booking insert + itinerary mark-submitted all in one
 *     Room transaction — rollback leaves no partial state.
 */
class SubmitBookingUseCase(
    private val database: FieldTripDatabase,
    private val itineraryRepository: ItineraryRepository,
    private val bookingRepository: BookingRepository,
    private val inventoryRepository: InventoryRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) {

    sealed class Result {
        data class Submitted(val order: BookingOrder) : Result()
        data class ValidationFailed(val errors: List<String>) : Result()
        object ItineraryNotFound : Result()
        object InventoryNotFound : Result()
        /** Available seats < partySize. Exposes remaining count for the UI. */
        data class QuotaUnavailable(val remaining: Int, val requested: Int) : Result()
        /** Booking already exists in a non-terminal state for this traveler/itinerary. */
        object DuplicateSubmission : Result()
    }

    suspend fun execute(itineraryId: String, inventorySlotId: String): Result {
        val session = sessionManager.requireSession()
        val itinerary = itineraryRepository.findById(itineraryId) ?: return Result.ItineraryNotFound

        AccessControl.requireOwnerOrRole(
            session, itinerary.travelerId, "ItineraryDraft", itineraryId,
            Role.Agent, Role.Administrator
        )

        when (val v = ItineraryValidator.validate(itinerary)) {
            is ItineraryValidator.Result.Invalid -> return Result.ValidationFailed(v.errors)
            else -> {}
        }

        val slot = inventoryRepository.findSlot(inventorySlotId)
            ?: return Result.InventoryNotFound
        val remaining = slot.availableCount
        if (remaining < itinerary.partySize) {
            return Result.QuotaUnavailable(remaining, itinerary.partySize)
        }

        // Dedup: if the traveler already has a non-terminal booking for this
        // itinerary, don't create a second one. Repeated submit taps are safe.
        val existing = bookingRepository.findByTraveler(itinerary.travelerId).firstOrNull {
            it.itineraryId == itinerary.id && !BookingState.isTerminal(it.state)
        }
        if (existing != null) return Result.DuplicateSubmission

        val actor = session.userId
        return try {
            val order = database.withTransaction {
                val now = Instant.now()
                val orderId = UUID.randomUUID().toString()

                inventoryRepository.applyQuotaOperation(
                    slotId = inventorySlotId,
                    operation = QuotaOperation.RESERVE,
                    units = itinerary.partySize,
                    bookingOrderId = orderId,
                    actor = actor,
                    reason = "Booking submission"
                )

                // Trip window derived from the slot's date range. Start-of-day
                // and end-of-day in the device zone so timing-boundary refund
                // rules evaluate predictably regardless of the submission time.
                val zone = ZoneId.systemDefault()
                val tripStartAt = slot.startDate.atStartOfDay(zone).toInstant()
                val tripEndAt = slot.endDate.atTime(LocalTime.MAX).atZone(zone).toInstant()

                val newOrder = BookingOrder(
                    id = orderId,
                    itineraryId = itinerary.id,
                    travelerId = itinerary.travelerId,
                    inventorySlotId = inventorySlotId,
                    partySize = itinerary.partySize,
                    state = BookingState.PendingConfirmation,
                    createdAt = now, updatedAt = now,
                    confirmedAt = null, confirmedBy = null,
                    cancelledAt = null, cancelReason = null,
                    lastActivityAt = now,
                    tripStartAt = tripStartAt,
                    tripEndAt = tripEndAt,
                    paidTotal = BigDecimal.ZERO.setScale(2)
                )
                bookingRepository.save(newOrder)
                itineraryRepository.markSubmitted(itinerary.id)
                newOrder
            }

            auditLogger.log(
                actor, AuditAction.BOOKING_SUBMITTED, "BookingOrder", order.id,
                "party=${order.partySize}, slot=$inventorySlotId, " +
                    "trip=${order.tripStartAt}..${order.tripEndAt}, by=${session.displayName}"
            )
            Result.Submitted(order)
        } catch (e: IllegalStateException) {
            if (e.message?.contains("Insufficient quota") == true) {
                // Should be rare — pre-check caught most cases, but a race from
                // a concurrent submit could still consume the last seats.
                val fresh = inventoryRepository.findSlot(inventorySlotId)
                Result.QuotaUnavailable(fresh?.availableCount ?: 0, itinerary.partySize)
            } else throw e
        }
    }
}
