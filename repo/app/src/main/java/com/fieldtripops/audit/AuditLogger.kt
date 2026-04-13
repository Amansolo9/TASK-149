package com.fieldtripops.audit

interface AuditLogger {
    suspend fun log(
        actor: String,
        action: AuditAction,
        entityType: String,
        entityId: String,
        details: String? = null
    )
}
