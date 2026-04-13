package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.ConsentRecord
import com.fieldtripops.domain.repository.ConsentRepository
import com.fieldtripops.security.auth.OwnershipViolationException
import com.fieldtripops.security.auth.SessionManager
import java.time.Instant
import java.util.UUID

/**
 * Manages on-device consent toggles per PRD §14.
 *
 * Ownership: session-bound. A user can only grant/revoke their OWN consent —
 * the acting userId always comes from `SessionManager`, never from callers.
 */
class RecordConsentUseCase(
    private val consentRepository: ConsentRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) {

    suspend fun grant(consentType: String, policyVersion: String): ConsentRecord {
        val session = sessionManager.requireSession()
        val now = Instant.now()
        val consent = ConsentRecord(
            id = UUID.randomUUID().toString(),
            userId = session.userId,
            consentType = consentType,
            granted = true,
            grantedAt = now,
            revokedAt = null,
            policyVersion = policyVersion
        )
        consentRepository.record(consent)
        auditLogger.log(
            actor = session.userId,
            action = AuditAction.CONSENT_GRANTED,
            entityType = "ConsentRecord",
            entityId = consent.id,
            details = "Consent granted: $consentType (policy v$policyVersion)"
        )
        return consent
    }

    suspend fun revoke(consentId: String) {
        val session = sessionManager.requireSession()
        val owned = consentRepository.getActiveConsents(session.userId)
            .any { it.id == consentId }
        if (!owned) {
            throw OwnershipViolationException(
                userId = session.userId,
                entityType = "ConsentRecord",
                entityId = consentId
            )
        }
        val now = Instant.now()
        consentRepository.revoke(consentId, now)
        auditLogger.log(
            actor = session.userId,
            action = AuditAction.CONSENT_REVOKED,
            entityType = "ConsentRecord",
            entityId = consentId,
            details = "Consent revoked"
        )
    }

    suspend fun listActive(): List<ConsentRecord> {
        val session = sessionManager.requireSession()
        return consentRepository.getActiveConsents(session.userId)
    }
}
