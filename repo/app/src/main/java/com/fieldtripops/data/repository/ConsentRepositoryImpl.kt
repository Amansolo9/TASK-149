package com.fieldtripops.data.repository

import com.fieldtripops.data.dao.ConsentRecordDao
import com.fieldtripops.data.entity.ConsentRecordEntity
import com.fieldtripops.domain.model.ConsentRecord
import com.fieldtripops.domain.repository.ConsentRepository
import java.time.Instant

class ConsentRepositoryImpl(
    private val consentRecordDao: ConsentRecordDao
) : ConsentRepository {

    override suspend fun record(consent: ConsentRecord) {
        consentRecordDao.insert(consent.toEntity())
    }

    override suspend fun getActiveConsents(userId: String): List<ConsentRecord> {
        return consentRecordDao.getActiveByUserId(userId).map { it.toDomain() }
    }

    override suspend fun revoke(consentId: String, at: Instant) {
        consentRecordDao.revoke(consentId, at.toEpochMilli())
    }

    private fun ConsentRecordEntity.toDomain(): ConsentRecord = ConsentRecord(
        id = id,
        userId = userId,
        consentType = consentType,
        granted = granted,
        grantedAt = Instant.ofEpochMilli(grantedAt),
        revokedAt = revokedAt?.let { Instant.ofEpochMilli(it) },
        policyVersion = policyVersion
    )

    private fun ConsentRecord.toEntity(): ConsentRecordEntity = ConsentRecordEntity(
        id = id,
        userId = userId,
        consentType = consentType,
        granted = granted,
        grantedAt = grantedAt.toEpochMilli(),
        revokedAt = revokedAt?.toEpochMilli(),
        policyVersion = policyVersion
    )
}
