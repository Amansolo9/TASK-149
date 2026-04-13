package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.model.TicketState
import com.fieldtripops.domain.repository.ClaimRepository
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import java.time.Instant

/**
 * Authorization rules for ticket transitions:
 *  - Submit (Draft -> Submitted) and Cancel: traveler-owner only (or admin)
 *  - WaitingForTraveler -> InReview: traveler-owner (responding) or reviewer
 *  - All other transitions (InReview, Resolve, Reject, Escalate, Appeal review,
 *    Finalized): Reviewer or Administrator only
 */
class TransitionClaimUseCase(
    private val claimRepository: ClaimRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) {

    sealed class Result {
        object Transitioned : Result()
        object TicketNotFound : Result()
        data class IllegalTransition(val from: TicketState, val to: TicketState) : Result()
    }

    suspend fun execute(ticketId: String, toState: TicketState, reason: String?): Result {
        val session = sessionManager.requireSession()
        val ticket = claimRepository.findById(ticketId) ?: return Result.TicketNotFound

        if (!TicketState.canTransition(ticket.state, toState)) {
            return Result.IllegalTransition(ticket.state, toState)
        }

        // Authorization matrix
        when (toState) {
            TicketState.Submitted, TicketState.Cancelled -> {
                AccessControl.requireOwnerOrRole(
                    session, ticket.travelerId, "ClaimTicket", ticketId,
                    Role.Administrator
                )
            }
            TicketState.InReview -> {
                if (ticket.state == TicketState.WaitingForTraveler) {
                    AccessControl.requireOwnerOrRole(
                        session, ticket.travelerId, "ClaimTicket", ticketId,
                        Role.Reviewer, Role.Administrator
                    )
                } else {
                    AccessControl.requireReviewerOrAdmin(session, "ticket.review")
                }
            }
            TicketState.WaitingForTraveler, TicketState.Resolved, TicketState.Rejected,
            TicketState.Escalated -> {
                AccessControl.requireReviewerOrAdmin(session, "ticket.transition")
            }
            TicketState.Appealed -> {
                AccessControl.requireOwnerOrRole(
                    session, ticket.travelerId, "ClaimTicket", ticketId,
                    Role.Reviewer, Role.Administrator
                )
            }
            TicketState.Finalized -> {
                AccessControl.requireAdmin(session, "ticket.finalize")
            }
            TicketState.AutoClosed, TicketState.Closed -> {
                AccessControl.requireReviewerOrAdmin(session, "ticket.close")
            }
            TicketState.Draft -> { /* no-op, draft is initial only */ }
        }

        val now = Instant.now()
        claimRepository.transition(ticketId, ticket.state, toState, session.userId, reason, now)
        auditLogger.log(session.userId, AuditAction.TICKET_STATE_CHANGED,
            "ClaimTicket", ticketId,
            "${ticket.state.name} -> ${toState.name} by ${session.displayName}${reason?.let { ": $it" } ?: ""}")
        return Result.Transitioned
    }
}
