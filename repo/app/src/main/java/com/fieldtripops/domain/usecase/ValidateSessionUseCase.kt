package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.Session
import com.fieldtripops.domain.repository.SessionRepository
import com.fieldtripops.security.SessionConfig
import java.time.Instant

class ValidateSessionUseCase(
    private val sessionRepository: SessionRepository,
    private val auditLogger: AuditLogger
) {

    sealed class Result {
        data class Valid(val session: Session) : Result()
        object Expired : Result()
        object NoSession : Result()
    }

    suspend fun execute(userId: String): Result {
        val session = sessionRepository.getActive(userId) ?: return Result.NoSession

        val now = Instant.now()
        val timeoutThreshold = now.minusSeconds(SessionConfig.SESSION_TIMEOUT_MINUTES * 60)

        if (session.lastActiveAt.isBefore(timeoutThreshold)) {
            sessionRepository.end(session.id, now, "timeout")
            auditLogger.log(
                actor = "system",
                action = AuditAction.SESSION_EXPIRED,
                entityType = "Session",
                entityId = session.id,
                details = "Session timed out after ${SessionConfig.SESSION_TIMEOUT_MINUTES} minutes of inactivity"
            )
            return Result.Expired
        }

        sessionRepository.touchLastActive(session.id, now)
        return Result.Valid(session.copy(lastActiveAt = now))
    }
}
