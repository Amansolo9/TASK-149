package com.fieldtripops.security

import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AuditChecksum {

    companion object {
        private const val ALGORITHM = "HmacSHA256"
        // Device-bound key for audit integrity. In production, this would be
        // sourced from AndroidKeyStore. For local-only offline use, a static
        // app-level secret provides tamper evidence within the same device.
        private val HMAC_KEY = "fieldtripops-audit-integrity-key".toByteArray(Charsets.UTF_8)
    }

    fun compute(
        actor: String,
        action: String,
        entityType: String,
        entityId: String,
        timestamp: Instant,
        previousChecksum: String?
    ): String {
        val payload = buildString {
            append(actor)
            append('|')
            append(action)
            append('|')
            append(entityType)
            append('|')
            append(entityId)
            append('|')
            append(timestamp.toEpochMilli())
            append('|')
            append(previousChecksum ?: "GENESIS")
        }
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(HMAC_KEY, ALGORITHM))
        val hash = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hash)
    }
}
