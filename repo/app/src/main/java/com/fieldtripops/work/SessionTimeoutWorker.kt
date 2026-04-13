package com.fieldtripops.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.repository.SessionRepository
import com.fieldtripops.security.SessionConfig
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant

class SessionTimeoutWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams), KoinComponent {

    private val sessionRepository: SessionRepository by inject()
    private val auditLogger: AuditLogger by inject()

    override suspend fun doWork(): Result {
        return try {
            val timeoutThreshold = Instant.now()
                .minusSeconds(SessionConfig.SESSION_TIMEOUT_MINUTES * 60)
            val expiredSessions = sessionRepository.getExpiredSessions(timeoutThreshold)

            for (session in expiredSessions) {
                val now = Instant.now()
                sessionRepository.end(session.id, now, "timeout")
                auditLogger.log(
                    actor = "system",
                    action = AuditAction.SESSION_EXPIRED,
                    entityType = "Session",
                    entityId = session.id,
                    details = "Session expired by background worker"
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "session_timeout_worker"
    }
}
