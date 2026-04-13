package com.fieldtripops.domain.repository

import com.fieldtripops.domain.model.ConsentRecord
import java.time.Instant

interface ConsentRepository {
    suspend fun record(consent: ConsentRecord)
    suspend fun getActiveConsents(userId: String): List<ConsentRecord>
    suspend fun revoke(consentId: String, at: Instant)
}
