package com.fieldtripops.domain.usecase

import androidx.room.withTransaction
import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.domain.booking.RescheduleValidator
import com.fieldtripops.domain.model.BookingState
import com.fieldtripops.domain.model.RescheduleRequest
import com.fieldtripops.domain.model.RescheduleStatus
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.repository.RescheduleRepository
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Requests a reschedule per PRD §9.3.
 *
 * Atomicity (audit finding #2): the RescheduleRequest insert AND the booking
 * state transition to `ReschedulePending` are performed in a single Room
 * transaction. If either write fails the other is rolled back so the pair
 * never gets into an inconsistent state (e.g., booking in ReschedulePending
 * with no linked request row).
 *
 * Authoritative data: the trip-start instant is read from the persisted
 * `BookingOrder.tripStartAt` — callers cannot supply it. The original trip
 * dates are similarly sourced from the booking (not caller-supplied) so the
 * request row always matches what the booking actually said.
 */
class RequestRescheduleUseCase(
    private val database: FieldTripDatabase,
    private val rescheduleRepository: RescheduleRepository,
    private val bookingRepository: BookingRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) {

    sealed class Result {
        data class Requested(val request: RescheduleRequest) : Result()
        object BookingNotFound : Result()
        object InvalidBookingState : Result()
        object ExceptionReasonRequired : Result()
        data class ValidationFailed(val errors: List<String>) : Result()
    }

    suspend fun execute(
        bookingOrderId: String,
        newStart: LocalDate, newEnd: LocalDate,
        exceptionReason: String?
    ): Result {
        val session = sessionManager.requireSession()
        val booking = bookingRepository.findById(bookingOrderId) ?: return Result.BookingNotFound

        AccessControl.requireOwnerOrRole(
            session, booking.travelerId, "BookingOrder", bookingOrderId,
            Role.Agent, Role.Administrator
        )

        if (booking.state != BookingState.Booked) return Result.InvalidBookingState

        when (val v = RescheduleValidator.validate(
            booking.tripStartAt, Instant.now(), session.roles.toList(), exceptionReason
        )) {
            is RescheduleValidator.Result.Invalid -> return Result.ValidationFailed(v.errors)
            is RescheduleValidator.Result.RequiresException -> return Result.ExceptionReasonRequired
            RescheduleValidator.Result.Valid -> {}
        }

        val zone = java.time.ZoneId.systemDefault()
        val origStart = booking.tripStartAt.atZone(zone).toLocalDate()
        val origEnd = booking.tripEndAt.atZone(zone).toLocalDate()

        val now = Instant.now()
        val request = RescheduleRequest(
            id = UUID.randomUUID().toString(),
            bookingOrderId = bookingOrderId,
            requestedBy = session.userId,
            requestedAt = now,
            originalStartDate = origStart, originalEndDate = origEnd,
            newStartDate = newStart, newEndDate = newEnd,
            exceptionReason = exceptionReason,
            approvedBy = null, approvedAt = null,
            status = RescheduleStatus.PENDING
        )

        // Atomic: both writes succeed together or both roll back.
        database.withTransaction {
            rescheduleRepository.save(request)
            bookingRepository.updateState(
                bookingOrderId, BookingState.ReschedulePending, now, session.userId,
                exceptionReason ?: "Reschedule requested"
            )
        }

        auditLogger.log(
            session.userId,
            if (exceptionReason != null) AuditAction.RESCHEDULE_EXCEPTION
            else AuditAction.RESCHEDULE_REQUESTED,
            "RescheduleRequest", request.id,
            "booking=$bookingOrderId; orig=$origStart..$origEnd; new=$newStart..$newEnd" +
                (exceptionReason?.let { "; exception: $it" } ?: "")
        )

        return Result.Requested(request)
    }
}
