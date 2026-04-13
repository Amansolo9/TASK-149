package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.DeletionRequest
import com.fieldtripops.domain.model.DeletionState
import com.fieldtripops.domain.repository.DeletionRequestRepository
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import java.time.Instant

/**
 * Administrator-only execution step that APPROVES and APPLIES a pending
 * deletion request. The actual row-level deletion/anonymization runs inside a
 * single Room transaction via the repository so that a crash mid-sweep cannot
 * leave user data partially removed.
 *
 * Audit trail on success: DELETION_APPROVED + DELETION_EXECUTED.
 * On failure: DELETION_FAILED with the failure reason.
 * Idempotency: re-executing an already-Executed row is a safe no-op.
 */
class ExecuteUserDeletionUseCase(
    private val repository: DeletionRequestRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) {
    sealed class Result {
        data class Executed(val request: DeletionRequest, val rowsTouched: Int) : Result()
        data class AlreadyExecuted(val request: DeletionRequest) : Result()
        data class NotFound(val id: String) : Result()
        data class Failed(val reason: String) : Result()
    }

    suspend fun execute(requestId: String): Result {
        val session = sessionManager.requireSession()
        AccessControl.requireAdmin(session, operation = "user.deletion.execute")

        val req = repository.findById(requestId) ?: return Result.NotFound(requestId)

        if (req.state == DeletionState.Executed) return Result.AlreadyExecuted(req)
        if (req.state == DeletionState.Rejected) {
            return Result.Failed("Request was rejected; cannot execute")
        }

        val now = Instant.now()
        val approved = req.copy(
            state = DeletionState.Approved,
            approvedBy = session.userId,
            approvedAt = now
        )
        repository.save(approved)
        auditLogger.log(
            session.userId, AuditAction.DELETION_APPROVED,
            "DeletionRequest", req.id,
            "target=${req.targetUserId}"
        )

        return try {
            val touched = repository.executeDeletion(req.targetUserId, req.scope)
            val executed = approved.copy(
                state = DeletionState.Executed,
                executedBy = session.userId,
                executedAt = Instant.now()
            )
            repository.save(executed)
            auditLogger.log(
                session.userId, AuditAction.DELETION_EXECUTED,
                "DeletionRequest", req.id,
                "target=${req.targetUserId}, scope=${req.scope.name}, rowsTouched=$touched"
            )
            // Also emit the existing DATA_DELETED/DATA_ANONYMIZED markers for
            // consistency with retention reporting.
            val marker = when (req.scope) {
                com.fieldtripops.domain.model.DeletionScope.HARD_DELETE -> AuditAction.DATA_DELETED
                com.fieldtripops.domain.model.DeletionScope.ANONYMIZE -> AuditAction.DATA_ANONYMIZED
            }
            auditLogger.log(
                session.userId, marker,
                "User", req.targetUserId,
                "via deletion request ${req.id}"
            )
            Result.Executed(executed, touched)
        } catch (e: Exception) {
            val failed = approved.copy(
                state = DeletionState.Failed,
                failureReason = e.message
            )
            repository.save(failed)
            auditLogger.log(
                session.userId, AuditAction.DELETION_FAILED,
                "DeletionRequest", req.id,
                "target=${req.targetUserId}, error=${e.message}"
            )
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun reject(requestId: String, reason: String): Result {
        val session = sessionManager.requireSession()
        AccessControl.requireAdmin(session, operation = "user.deletion.reject")
        val req = repository.findById(requestId) ?: return Result.NotFound(requestId)
        val rejected = req.copy(
            state = DeletionState.Rejected,
            approvedBy = session.userId,
            approvedAt = Instant.now(),
            failureReason = reason
        )
        repository.save(rejected)
        auditLogger.log(
            session.userId, AuditAction.DELETION_REJECTED,
            "DeletionRequest", req.id,
            "target=${req.targetUserId}, reason=$reason"
        )
        return Result.Executed(rejected, 0)
    }
}
