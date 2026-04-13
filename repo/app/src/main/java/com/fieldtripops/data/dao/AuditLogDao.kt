package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.fieldtripops.data.entity.AuditLogEntity

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(entry: AuditLogEntity)

    @Query("SELECT * FROM audit_logs WHERE entityType = :entityType AND entityId = :entityId ORDER BY timestamp ASC")
    suspend fun getByEntity(entityType: String, entityId: String): List<AuditLogEntity>

    @Query("SELECT * FROM audit_logs WHERE actor = :actor ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByActor(actor: String, limit: Int): List<AuditLogEntity>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastEntry(): AuditLogEntity?

    @Query("SELECT * FROM audit_logs WHERE action IN (:actions) ORDER BY timestamp DESC LIMIT :limit")
    suspend fun findByActions(actions: Collection<String>, limit: Int): List<AuditLogEntity>
}
