package com.fieldtripops.domain.model

import java.time.Instant

data class Session(
    val id: String,
    val userId: String,
    val startedAt: Instant,
    val lastActiveAt: Instant,
    val endedAt: Instant?,
    val endReason: String?
)
