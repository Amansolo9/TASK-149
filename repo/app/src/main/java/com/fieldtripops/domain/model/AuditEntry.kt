package com.fieldtripops.domain.model

import java.time.Instant

data class AuditEntry(
    val id: String,
    val actor: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val timestamp: Instant,
    val details: String?,
    val checksum: String
)
