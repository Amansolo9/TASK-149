package com.fieldtripops.domain.usecase

import androidx.room.withTransaction
import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.domain.model.ContentState
import com.fieldtripops.domain.model.GovernanceDecision
import com.fieldtripops.domain.repository.CheckpointRepository
import com.fieldtripops.domain.repository.ContentRepository
import com.fieldtripops.domain.repository.GovernanceRepository
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import java.time.Instant
import java.util.UUID

/**
 * Reviewer/Admin manual content state override per PRD §9.8.
 * Reason mandatory; actor identity from session.
 */
class GovernanceOverrideUseCase(
    private val database: FieldTripDatabase,
    private val contentRepository: ContentRepository,
    private val governanceRepository: GovernanceRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager,
    private val checkpointRepository: CheckpointRepository
) {

    sealed class Result {
        object Applied : Result()
        object ContentNotFound : Result()
        data class IllegalTransition(val from: ContentState, val to: ContentState) : Result()
        object ReasonRequired : Result()
    }

    suspend fun execute(contentId: String, toState: ContentState, reason: String): Result {
        val session = sessionManager.requireSession()
        AccessControl.requireReviewerOrAdmin(session, operation = "governance.override")

        if (reason.isBlank()) return Result.ReasonRequired
        val content = contentRepository.findById(contentId) ?: return Result.ContentNotFound
        if (!ContentState.canTransition(content.state, toState)) {
            return Result.IllegalTransition(content.state, toState)
        }

        val actor = session.userId
        val now = Instant.now()
        database.withTransaction {
            val fromState = content.state
            // Checkpoint before state change so rollback can restore
            checkpointRepository.createCheckpoint(
                label = "pre-override",
                entityType = "ContentItem",
                entityId = contentId,
                snapshotJson = """{"state":"${fromState.name}","averageRating":${content.averageRating},"ratingCount":${content.ratingCount}}"""
            )
            contentRepository.updateState(contentId, toState, now)
            governanceRepository.recordDecision(
                GovernanceDecision(
                    id = UUID.randomUUID().toString(),
                    contentId = contentId, fromState = fromState, toState = toState,
                    actor = actor, reason = reason, threshold = null, decidedAt = now
                )
            )
            if (toState == ContentState.Active) {
                governanceRepository.clearSuppression(contentId, now)
            }
        }

        auditLogger.log(actor, AuditAction.GOVERNANCE_OVERRIDE, "ContentItem", contentId,
            "${content.state.name} -> ${toState.name} by ${session.displayName}: $reason")
        return Result.Applied
    }
}
