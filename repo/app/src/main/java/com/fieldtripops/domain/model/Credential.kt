package com.fieldtripops.domain.model

import java.time.Instant

data class Credential(
    val userId: String,
    val passwordHash: String,
    val salt: String,
    val failedAttempts: Int,
    val lockedUntil: Instant?,
    val lastLoginAt: Instant?
)
