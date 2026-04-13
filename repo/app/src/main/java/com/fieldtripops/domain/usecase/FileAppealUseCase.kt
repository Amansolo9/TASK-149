package com.fieldtripops.domain.usecase

import androidx.room.withTransaction
import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.domain.model.AppealRecord
import com.fieldtripops.domain.model.OfflineQueueItem
import com.fieldtripops.domain.model.QueueItemState
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.model.TicketState
import com.fieldtripops.domain.repository.ClaimRepository
import com.fieldtripops.domain.repository.OfflineQueueRepository
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import java.time.Instant
import java.util.UUID

/**
 * Files an appeal against a Resolved or Rejected claim ticket (PRD §10.2).
 *
 * Integration:
 *  - Owner traveler OR a Reviewer/Admin may file the appeal.
 *  - Creates an AppealRecord row AND transitions the ticket to `Appealed` in
 *    a single Room transaction so the two states stay consistent.
 *  - Also enqueues an OfflineQueueItem with jobType=`APPEAL_REVIEW` so a
 *    reviewer can pick the item up later, even offline. The queue row is the
 *    entry point for reviewers to discover pending appeals.
 */
class FileAppealUseCase(
    private val database: FieldTripDatabase,
    private val claimRepository: ClaimRepository,
    private val offlineQueueRepository: OfflineQueueRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) {

    sealed class Result {
        data class Filed(val appeal: AppealRecord, val queueId: String) : Result()
        object TicketNotFound : Result()
        data class InvalidState(val state: TicketState) : Result()
        object ReasonRequired : Result()
    }

    suspend fun execute(ticketId: String, reason: String): Result {
        val session = sessionManager.requireSession()
        if (reason.isBlank()) return Result.ReasonRequired

        val ticket = claimRepository.findById(ticketId) ?: return Result.TicketNotFound

        // Only appealable from Resolved or Rejected per state machine.
        if (!TicketState.canTransition(ticket.state, TicketState.Appealed)) {
            return Result.InvalidState(ticket.state)
        }

        AccessControl.requireOwnerOrRole(
            session, ticket.travelerId, "ClaimTicket", ticketId,
            Role.Reviewer, Role.Administrator
        )

        val now = Instant.now()
        val appeal = AppealRecord(
            id = UUID.randomUUID().toString(),
            ticketId = ticketId,
            filedBy = session.userId,
            filedAt = now,
            reason = reason,
            resolvedAt = null,
            resolution = null
        )
        val queueItem = OfflineQueueItem(
            id = UUID.randomUUID().toString(),
            jobType = "APPEAL_REVIEW",
            payloadJson = """{"ticketId":"$ticketId","appealId":"${appeal.id}"}""",
            state = QueueItemState.Pending,
            attempts = 0,
            maxAttempts = 5,
            scheduledAt = now,
            lastError = null,
            createdAt = now,
            updatedAt = now
        )

        database.withTransaction {
            claimRepository.recordAppeal(appeal)
            claimRepository.transition(
                ticketId = ticketId,
                fromState = ticket.state,
                toState = TicketState.Appealed,
                actor = session.userId,
                reason = "Appeal filed",
                at = now
            )
            offlineQueueRepository.enqueue(queueItem)
        }

        auditLogger.log(
            session.userId, AuditAction.TICKET_APPEAL_FILED, "AppealRecord", appeal.id,
            "ticket=$ticketId, queued=${queueItem.id}, by=${session.displayName}"
        )

        return Result.Filed(appeal, queueItem.id)
    }
}
