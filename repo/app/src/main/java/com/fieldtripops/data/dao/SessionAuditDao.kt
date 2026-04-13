package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.fieldtripops.data.entity.SessionAuditEntity

@Dao
interface SessionAuditDao {
    @Insert
    suspend fun insert(session: SessionAuditEntity)

    @Query("SELECT * FROM session_audits WHERE userId = :userId AND endedAt IS NULL LIMIT 1")
    suspend fun getActiveByUserId(userId: String): SessionAuditEntity?

    @Query("UPDATE session_audits SET lastActiveAt = :at WHERE id = :id")
    suspend fun touchLastActive(id: String, at: Long)

    @Query("UPDATE session_audits SET endedAt = :at, endReason = :reason WHERE id = :id")
    suspend fun endSession(id: String, at: Long, reason: String)

    @Query("SELECT * FROM session_audits WHERE endedAt IS NULL AND lastActiveAt < :threshold")
    suspend fun getExpiredSessions(threshold: Long): List<SessionAuditEntity>
}
