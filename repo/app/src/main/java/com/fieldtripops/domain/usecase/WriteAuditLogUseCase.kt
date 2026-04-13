package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger

class WriteAuditLogUseCase(
    private val auditLogger: AuditLogger
) {

    suspend fun execute(
        actor: String,
        action: AuditAction,
        entityType: String,
        entityId: String,
        details: String? = null
    ) {
        auditLogger.log(actor, action, entityType, entityId, details)
    }
}
