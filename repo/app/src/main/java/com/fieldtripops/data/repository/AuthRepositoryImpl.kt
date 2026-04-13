package com.fieldtripops.data.repository

import com.fieldtripops.data.dao.CredentialDao
import com.fieldtripops.data.entity.CredentialEntity
import com.fieldtripops.domain.model.Credential
import com.fieldtripops.domain.repository.AuthRepository
import java.time.Instant

class AuthRepositoryImpl(
    private val credentialDao: CredentialDao
) : AuthRepository {

    override suspend fun getCredential(userId: String): Credential? {
        return credentialDao.getByUserId(userId)?.toDomain()
    }

    override suspend fun saveCredential(credential: Credential) {
        credentialDao.insert(credential.toEntity())
    }

    override suspend fun incrementFailedAttempts(userId: String) {
        credentialDao.incrementFailedAttempts(userId)
    }

    override suspend fun resetFailedAttempts(userId: String) {
        credentialDao.resetFailedAttempts(userId)
    }

    override suspend fun lockAccount(userId: String, until: Instant) {
        credentialDao.lockAccount(userId, until.toEpochMilli())
    }

    override suspend fun updateLastLogin(userId: String, at: Instant) {
        credentialDao.updateLastLogin(userId, at.toEpochMilli())
    }

    private fun CredentialEntity.toDomain(): Credential = Credential(
        userId = userId,
        passwordHash = passwordHash,
        salt = salt,
        failedAttempts = failedAttempts,
        lockedUntil = lockedUntil?.let { Instant.ofEpochMilli(it) },
        lastLoginAt = lastLoginAt?.let { Instant.ofEpochMilli(it) }
    )

    private fun Credential.toEntity(): CredentialEntity = CredentialEntity(
        userId = userId,
        passwordHash = passwordHash,
        salt = salt,
        failedAttempts = failedAttempts,
        lockedUntil = lockedUntil?.toEpochMilli(),
        lastLoginAt = lastLoginAt?.toEpochMilli()
    )
}
