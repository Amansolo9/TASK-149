package com.fieldtripops.domain.model

import java.time.Instant

data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val roles: List<Role>,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)
