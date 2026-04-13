package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.TicketState
import com.fieldtripops.domain.repository.ClaimRepository
import com.fieldtripops.domain.repository.SlaConfigRepository
import java.time.Duration
import java.time.Instant

/**
 * Auto-closes tickets in WaitingForTraveler past the configured no-response
 * threshold per PRD §9.7. The threshold is read from persisted SLA config
 * (default 72 hours) so administrators can change it without code edits.
 */
class AutoCloseWaitingTicketsUseCase(
    private val claimRepository: ClaimRepository,
    private val slaConfigRepository: SlaConfigRepository,
    private val auditLogger: AuditLogger
) {
    data class Result(val closedCount: Int, val thresholdHours: Int)

    suspend fun execute(): Result {
        val sla = slaConfigRepository.get()
        val threshold = Duration.ofHours(sla.travelerNoResponseHours.toLong())
        val now = Instant.now()
        val cutoff = now.minus(threshold)
        val stale = claimRepository.findStaleWaiting(cutoff)

        var closed = 0
        for (ticket in stale) {
            if (ticket.state == TicketState.WaitingForTraveler) {
                claimRepository.transition(
                    ticket.id, ticket.state, TicketState.AutoClosed,
                    "system",
                    "No traveler response for ${sla.travelerNoResponseHours} hours",
                    now
                )
                auditLogger.log(
                    actor = "system",
                    action = AuditAction.TICKET_AUTO_CLOSED,
                    entityType = "ClaimTicket",
                    entityId = ticket.id,
                    details = "Auto-closed after ${sla.travelerNoResponseHours}h without traveler response"
                )
                closed++
            }
        }
        return Result(closed, sla.travelerNoResponseHours)
    }
}
