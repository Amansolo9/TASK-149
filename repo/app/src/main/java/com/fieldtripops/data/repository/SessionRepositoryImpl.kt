package com.fieldtripops.data.repository

import com.fieldtripops.data.dao.SessionAuditDao
import com.fieldtripops.data.entity.SessionAuditEntity
import com.fieldtripops.domain.model.Session
import com.fieldtripops.domain.repository.SessionRepository
import java.time.Instant

class SessionRepositoryImpl(
    private val sessionAuditDao: SessionAuditDao
) : SessionRepository {

    override suspend fun create(session: Session) {
        sessionAuditDao.insert(session.toEntity())
    }

    override suspend fun getActive(userId: String): Session? {
        return sessionAuditDao.getActiveByUserId(userId)?.toDomain()
    }

    override suspend fun touchLastActive(sessionId: String, at: Instant) {
        sessionAuditDao.touchLastActive(sessionId, at.toEpochMilli())
    }

    override suspend fun end(sessionId: String, at: Instant, reason: String) {
        sessionAuditDao.endSession(sessionId, at.toEpochMilli(), reason)
    }

    override suspend fun getExpiredSessions(timeoutThreshold: Instant): List<Session> {
        return sessionAuditDao.getExpiredSessions(timeoutThreshold.toEpochMilli())
            .map { it.toDomain() }
    }

    private fun SessionAuditEntity.toDomain(): Session = Session(
        id = id,
        userId = userId,
        startedAt = Instant.ofEpochMilli(startedAt),
        lastActiveAt = Instant.ofEpochMilli(lastActiveAt),
        endedAt = endedAt?.let { Instant.ofEpochMilli(it) },
        endReason = endReason
    )

    private fun Session.toEntity(): SessionAuditEntity = SessionAuditEntity(
        id = id,
        userId = userId,
        startedAt = startedAt.toEpochMilli(),
        lastActiveAt = lastActiveAt.toEpochMilli(),
        endedAt = endedAt?.toEpochMilli(),
        endReason = endReason
    )
}
