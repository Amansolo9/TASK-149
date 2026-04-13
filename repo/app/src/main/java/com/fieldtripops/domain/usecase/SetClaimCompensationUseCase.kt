package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.ClaimTicket
import com.fieldtripops.domain.model.CompensationCalculation
import com.fieldtripops.domain.model.InvestigationNote
import com.fieldtripops.domain.model.TicketState
import com.fieldtripops.domain.repository.ClaimRepository
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Reviewer/Administrator records the compensation calculation against a claim
 * ticket. Writes the compensation fields and an investigation note atomically,
 * and emits an audit event. Validates amount and currency.
 *
 * Compensation is an investigation/resolution artifact: a reviewer can set or
 * revise it while the ticket is in InReview/Escalated. After Resolved it is
 * read-only (the note remains editable via addNote).
 */
class SetClaimCompensationUseCase(
    private val claimRepository: ClaimRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) {
    sealed class Result {
        data class Updated(val ticket: ClaimTicket) : Result()
        data class TicketNotFound(val id: String) : Result()
        data class Invalid(val reason: String) : Result()
    }

    suspend fun execute(
        ticketId: String,
        amount: BigDecimal,
        currency: String,
        basis: String,
        note: String? = null
    ): Result {
        val session = sessionManager.requireSession()
        AccessControl.requireReviewerOrAdmin(session, operation = "claim.compensation.set")

        if (amount.signum() < 0) return Result.Invalid("Compensation cannot be negative")
        if (currency.isBlank() || currency.length > 8) return Result.Invalid("Invalid currency code")
        if (basis.isBlank()) return Result.Invalid("basis required")

        val ticket = claimRepository.findById(ticketId)
            ?: return Result.TicketNotFound(ticketId)

        // Disallow editing after a hard terminal state like Closed/Finalized/Cancelled.
        if (TicketState.isTerminal(ticket.state) &&
            ticket.state != TicketState.Resolved) {
            return Result.Invalid("Cannot set compensation on ${ticket.state.name} ticket")
        }

        val now = Instant.now()
        val comp = CompensationCalculation(
            amount = amount,
            currency = currency.uppercase(),
            basis = basis,
            approverId = session.userId,
            approverName = session.displayName,
            decidedAt = now,
            note = note
        )
        val investigationNote = note?.let {
            InvestigationNote(
                id = UUID.randomUUID().toString(),
                ticketId = ticketId,
                authorUserId = session.userId,
                note = "[compensation] $basis amount=$amount $currency: $it",
                createdAt = now
            )
        }
        claimRepository.setCompensation(ticketId, comp, investigationNote)
        auditLogger.log(
            session.userId, AuditAction.CLAIM_COMPENSATION_SET,
            "ClaimTicket", ticketId,
            "amount=$amount $currency, basis=$basis"
        )
        val updated = claimRepository.findById(ticketId)!!
        return Result.Updated(updated)
    }
}
