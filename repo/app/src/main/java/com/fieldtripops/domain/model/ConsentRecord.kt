package com.fieldtripops.domain.model

import java.time.Instant

data class ConsentRecord(
    val id: String,
    val userId: String,
    val consentType: String,
    val granted: Boolean,
    val grantedAt: Instant,
    val revokedAt: Instant?,
    val policyVersion: String
)
