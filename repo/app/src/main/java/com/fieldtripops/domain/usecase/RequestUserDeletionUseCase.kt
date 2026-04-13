package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.DeletionRequest
import com.fieldtripops.domain.model.DeletionScope
import com.fieldtripops.domain.model.DeletionState
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.DeletionRequestRepository
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.OwnershipViolationException
import com.fieldtripops.security.auth.SessionManager
import java.time.Instant
import java.util.UUID

/**
 * Creates a new user-deletion request.
 *
 * Authorization:
 *  - Travelers may request deletion of their OWN data only.
 *  - Administrators may request deletion of any user.
 *
 * Idempotency: if the target user already has an open (Requested|Approved)
 * request, this use case returns [Result.AlreadyPending] rather than creating
 * a duplicate record.
 */
class RequestUserDeletionUseCase(
    private val repository: DeletionRequestRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) {
    sealed class Result {
        data class Queued(val request: DeletionRequest) : Result()
        data class AlreadyPending(val existingCount: Int) : Result()
        data class Invalid(val reason: String) : Result()
    }

    suspend fun execute(
        targetUserId: String,
        reason: String?,
        scope: DeletionScope = DeletionScope.ANONYMIZE
    ): Result {
        val session = sessionManager.requireSession()

        if (targetUserId.isBlank()) return Result.Invalid("targetUserId required")

        // Self-request OR administrator. Any other role targeting another user
        // is rejected as an ownership violation.
        if (session.userId != targetUserId && !session.hasRole(Role.Administrator)) {
            throw OwnershipViolationException(session.userId, "User", targetUserId)
        }

        if (repository.hasOpenFor(targetUserId)) {
            return Result.AlreadyPending(repository.findByTarget(targetUserId).size)
        }

        val now = Instant.now()
        val request = DeletionRequest(
            id = UUID.randomUUID().toString(),
            targetUserId = targetUserId,
            requestedBy = session.userId,
            requestedAt = now,
            reason = reason,
            state = DeletionState.Requested,
            approvedBy = null,
            approvedAt = null,
            executedBy = null,
            executedAt = null,
            failureReason = null,
            scope = scope
        )
        repository.save(request)

        auditLogger.log(
            actor = session.userId,
            action = AuditAction.DELETION_REQUESTED,
            entityType = "DeletionRequest",
            entityId = request.id,
            details = "target=$targetUserId, scope=${scope.name}, reason=${reason ?: "(none)"}"
        )
        return Result.Queued(request)
    }
}
