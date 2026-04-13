package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.TicketState
import com.fieldtripops.domain.repository.ClaimRepository
import java.time.Duration
import java.time.Instant

/**
 * Retention sweep per PRD §9.10: tickets retained for 24 months from closure.
 * Anonymizes (does not hard-delete) closed tickets older than retention window
 * to preserve audit integrity.
 */
class RetentionSweepUseCase(
    private val claimRepository: ClaimRepository,
    private val auditLogger: AuditLogger
) {
    companion object {
        val TICKET_RETENTION: Duration = Duration.ofDays(24L * 30L) // ~24 months
    }

    data class Result(val anonymizedTickets: Int)

    suspend fun execute(): Result {
        val cutoff = Instant.now().minus(TICKET_RETENTION)

        var anonymized = 0
        for (state in listOf(TicketState.Closed, TicketState.AutoClosed, TicketState.Finalized)) {
            val tickets = claimRepository.findByState(state)
            for (t in tickets) {
                val closeAt = t.closedAt ?: t.resolvedAt ?: continue
                if (closeAt.isBefore(cutoff)) {
                    // Anonymize description by overwriting with placeholder.
                    val anon = t.copy(description = "[anonymized]")
                    claimRepository.save(anon)
                    auditLogger.log(
                        actor = "system", action = AuditAction.DATA_ANONYMIZED,
                        entityType = "ClaimTicket", entityId = t.id,
                        details = "Anonymized after 24-month retention"
                    )
                    anonymized++
                }
            }
        }
        auditLogger.log(
            actor = "system", action = AuditAction.RETENTION_SWEEP_RUN,
            entityType = "RetentionSweep", entityId = "auto",
            details = "Tickets anonymized: $anonymized"
        )
        return Result(anonymized)
    }
}
