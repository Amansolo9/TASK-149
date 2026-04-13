package com.fieldtripops.domain.model

import java.time.Instant

data class RoleAssignment(
    val id: String,
    val userId: String,
    val role: Role,
    val assignedAt: Instant,
    val assignedBy: String
)
