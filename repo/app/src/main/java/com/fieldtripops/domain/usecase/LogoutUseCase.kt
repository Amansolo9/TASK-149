package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.repository.SessionRepository
import com.fieldtripops.security.auth.SessionManager
import java.time.Instant

class LogoutUseCase(
    private val sessionRepository: SessionRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) {

    suspend fun execute() {
        val session = sessionManager.current() ?: return
        val now = Instant.now()
        sessionRepository.end(session.sessionId, now, "user_logout")
        auditLogger.log(
            actor = session.userId,
            action = AuditAction.LOGOUT,
            entityType = "Session",
            entityId = session.sessionId,
            details = "User initiated logout"
        )
        sessionManager.clear()
    }
}
