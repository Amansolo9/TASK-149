package com.fieldtripops.domain.repository

import com.fieldtripops.domain.model.AuditEntry

interface AuditRepository {
    suspend fun append(entry: AuditEntry)
    suspend fun getByEntity(entityType: String, entityId: String): List<AuditEntry>
    suspend fun getByActor(actor: String, limit: Int = 50): List<AuditEntry>
    suspend fun getLastEntry(): AuditEntry?
    suspend fun findByActions(actions: Collection<String>, limit: Int = 5000): List<AuditEntry>
}
