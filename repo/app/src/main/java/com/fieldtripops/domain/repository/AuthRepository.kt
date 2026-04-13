package com.fieldtripops.domain.repository

import com.fieldtripops.domain.model.Credential
import java.time.Instant

interface AuthRepository {
    suspend fun getCredential(userId: String): Credential?
    suspend fun saveCredential(credential: Credential)
    suspend fun incrementFailedAttempts(userId: String)
    suspend fun resetFailedAttempts(userId: String)
    suspend fun lockAccount(userId: String, until: Instant)
    suspend fun updateLastLogin(userId: String, at: Instant)
}
