package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.booking.RefundEngine
import com.fieldtripops.domain.model.RefundDecision
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.repository.RefundDecisionRepository
import com.fieldtripops.domain.repository.RefundRuleRepository
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Approves a refund per PRD §9.5.
 *
 * Integrity boundaries (audit finding #4):
 *   - The approver identity is read from the authenticated SessionContext.
 *     Callers cannot spoof who approved a refund.
 *   - The paid total and trip start are read from the persisted BookingOrder
 *     row INSIDE the use case. Callers pass only the booking id. Previous
 *     versions accepted `paidTotal` and `tripStart` as parameters; that
 *     signature was a trust-the-caller bug and is removed.
 *   - Refund policy is resolved from persisted rules via [RefundRuleRepository];
 *     administrators can change bands without code edits. Seed defaults replay
 *     the original 48h/24h bands so fresh installs behave identically.
 */
class ApproveRefundUseCase(
    private val refundDecisionRepository: RefundDecisionRepository,
    private val bookingRepository: BookingRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager,
    private val refundRuleRepository: RefundRuleRepository
) {

    sealed class Result {
        data class Approved(val decision: RefundDecision) : Result()
        object BookingNotFound : Result()
        data class Invalid(val reason: String) : Result()
    }

    suspend fun execute(
        bookingOrderId: String,
        manualOverrideAmount: BigDecimal? = null,
        manualOverrideReason: String? = null
    ): Result {
        val session = sessionManager.requireSession()
        AccessControl.requireRefundApprover(session)

        val booking = bookingRepository.findById(bookingOrderId)
            ?: return Result.BookingNotFound

        // Authoritative financial + timing facts from persistence.
        val paidTotal = booking.paidTotal
        val tripStart = booking.tripStartAt

        if (paidTotal.signum() <= 0) {
            // Nothing was paid according to the confirmed fee items — refund N/A.
            return Result.Invalid("Booking has no recorded paid total to refund")
        }

        val rules = refundRuleRepository.listActive().ifEmpty { RefundEngine.defaultRules() }
        val quote = RefundEngine.compute(paidTotal, tripStart, Instant.now(), rules)
        val finalAmount = manualOverrideAmount ?: quote.refundAmount

        if (finalAmount > paidTotal) return Result.Invalid("Refund cannot exceed paid total")
        if (finalAmount.signum() < 0) return Result.Invalid("Refund amount cannot be negative")
        if (manualOverrideAmount != null && manualOverrideReason.isNullOrBlank()) {
            return Result.Invalid("Manual override requires a reason")
        }

        val decision = RefundDecision(
            id = UUID.randomUUID().toString(),
            bookingOrderId = bookingOrderId,
            paidTotal = paidTotal,                       // persisted, not caller
            refundAmount = finalAmount,
            refundPercent = quote.refundPercent,
            ruleUsed = quote.ruleUsed,
            approverUserId = session.userId,             // session, not caller
            approverName = session.displayName,          // session, not caller
            decidedAt = Instant.now(),
            manualOverrideReason = manualOverrideReason
        )
        refundDecisionRepository.record(decision)

        val action = if (manualOverrideReason != null)
            AuditAction.REFUND_OVERRIDE else AuditAction.REFUND_APPROVED
        auditLogger.log(
            session.userId, action, "RefundDecision", decision.id,
            "booking=$bookingOrderId, paid=$paidTotal, amount=$finalAmount, " +
                "rule=${quote.ruleUsed}, tripStart=$tripStart" +
                (manualOverrideReason?.let { ", override: $it" } ?: "")
        )

        return Result.Approved(decision)
    }
}
