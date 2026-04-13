package com.fieldtripops.data.repository

import com.fieldtripops.data.dao.AuditLogDao
import com.fieldtripops.data.entity.AuditLogEntity
import com.fieldtripops.domain.model.AuditEntry
import com.fieldtripops.domain.repository.AuditRepository
import java.time.Instant

class AuditRepositoryImpl(
    private val auditLogDao: AuditLogDao
) : AuditRepository {

    override suspend fun append(entry: AuditEntry) {
        auditLogDao.insert(entry.toEntity())
    }

    override suspend fun getByEntity(entityType: String, entityId: String): List<AuditEntry> {
        return auditLogDao.getByEntity(entityType, entityId).map { it.toDomain() }
    }

    override suspend fun getByActor(actor: String, limit: Int): List<AuditEntry> {
        return auditLogDao.getByActor(actor, limit).map { it.toDomain() }
    }

    override suspend fun getLastEntry(): AuditEntry? {
        return auditLogDao.getLastEntry()?.toDomain()
    }

    override suspend fun findByActions(actions: Collection<String>, limit: Int): List<AuditEntry> {
        return auditLogDao.findByActions(actions, limit).map { it.toDomain() }
    }

    private fun AuditLogEntity.toDomain(): AuditEntry = AuditEntry(
        id = id,
        actor = actor,
        action = action,
        entityType = entityType,
        entityId = entityId,
        timestamp = Instant.ofEpochMilli(timestamp),
        details = details,
        checksum = checksum
    )

    private fun AuditEntry.toEntity(): AuditLogEntity = AuditLogEntity(
        id = id,
        actor = actor,
        action = action,
        entityType = entityType,
        entityId = entityId,
        timestamp = timestamp.toEpochMilli(),
        details = details,
        checksum = checksum
    )
}
