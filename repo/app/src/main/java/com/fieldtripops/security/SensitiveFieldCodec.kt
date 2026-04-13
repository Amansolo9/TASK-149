package com.fieldtripops.security

/**
 * Thin abstraction over [FieldEncryptor] for sensitive textual fields. Adds:
 *  - null/blank passthrough (do not encrypt empty strings — wastes space and
 *    tells an attacker which rows are non-empty),
 *  - a versioned ciphertext envelope `enc:v1:<base64>` so future format
 *    upgrades (e.g., HKDF or per-field IV scheme changes) can detect old rows
 *    and decrypt with the legacy path,
 *  - safe `decrypt()` that detects an unencrypted row and returns it verbatim
 *    (supports gradual migration of pre-existing plaintext rows).
 */
interface SensitiveFieldCodec {
    fun encrypt(plaintext: String?): String?
    fun decrypt(stored: String?): String?
}

class AesSensitiveFieldCodec(private val encryptor: FieldEncryptor) : SensitiveFieldCodec {

    companion object {
        const val PREFIX_V1 = "enc:v1:"
    }

    override fun encrypt(plaintext: String?): String? {
        if (plaintext.isNullOrEmpty()) return plaintext
        val ct = encryptor.encrypt(plaintext)
        return PREFIX_V1 + ct
    }

    override fun decrypt(stored: String?): String? {
        if (stored.isNullOrEmpty()) return stored
        if (!stored.startsWith(PREFIX_V1)) {
            // legacy plaintext or migrated-out value; return as-is
            return stored
        }
        return encryptor.decrypt(stored.removePrefix(PREFIX_V1))
    }
}

/**
 * Identity codec used when AndroidKeyStore is unavailable (unit tests on JVM).
 * Production wiring uses `AesSensitiveFieldCodec`.
 */
class NoopSensitiveFieldCodec : SensitiveFieldCodec {
    override fun encrypt(plaintext: String?): String? = plaintext
    override fun decrypt(stored: String?): String? = stored
}
