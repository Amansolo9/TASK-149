package com.fieldtripops.domain.repository

import com.fieldtripops.domain.model.Session
import java.time.Instant

interface SessionRepository {
    suspend fun create(session: Session)
    suspend fun getActive(userId: String): Session?
    suspend fun touchLastActive(sessionId: String, at: Instant)
    suspend fun end(sessionId: String, at: Instant, reason: String)
    suspend fun getExpiredSessions(timeoutThreshold: Instant): List<Session>
}
