package com.fieldtripops.audit

import com.fieldtripops.domain.model.AuditEntry
import com.fieldtripops.domain.repository.AuditRepository
import com.fieldtripops.security.AuditChecksum
import java.time.Instant
import java.util.UUID

class RoomAuditLogger(
    private val auditRepository: AuditRepository,
    private val auditChecksum: AuditChecksum
) : AuditLogger {

    override suspend fun log(
        actor: String,
        action: AuditAction,
        entityType: String,
        entityId: String,
        details: String?
    ) {
        val now = Instant.now()
        val lastEntry = auditRepository.getLastEntry()
        val checksum = auditChecksum.compute(
            actor = actor,
            action = action.name,
            entityType = entityType,
            entityId = entityId,
            timestamp = now,
            previousChecksum = lastEntry?.checksum
        )
        val entry = AuditEntry(
            id = UUID.randomUUID().toString(),
            actor = actor,
            action = action.name,
            entityType = entityType,
            entityId = entityId,
            timestamp = now,
            details = details,
            checksum = checksum
        )
        auditRepository.append(entry)
    }
}
