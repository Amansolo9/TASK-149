package com.fieldtripops.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PasswordHasher {

    companion object {
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 32
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    }

    fun generateSalt(): String {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    fun hash(password: String, salt: String): String {
        val saltBytes = Base64.getDecoder().decode(salt)
        val spec = PBEKeySpec(password.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val hashBytes = factory.generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(hashBytes)
    }

    fun verify(password: String, salt: String, expectedHash: String): Boolean {
        val actualHash = hash(password, salt)
        // Constant-time comparison to prevent timing attacks
        if (actualHash.length != expectedHash.length) return false
        var result = 0
        for (i in actualHash.indices) {
            result = result or (actualHash[i].code xor expectedHash[i].code)
        }
        return result == 0
    }
}
