package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.AuthResult
import com.fieldtripops.domain.model.Session
import com.fieldtripops.domain.repository.AuthRepository
import com.fieldtripops.domain.repository.SessionRepository
import com.fieldtripops.domain.repository.UserRepository
import com.fieldtripops.security.PasswordHasher
import com.fieldtripops.security.SessionConfig
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import java.time.Instant
import java.util.UUID

class LoginUseCase(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val passwordHasher: PasswordHasher,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) {

    suspend fun execute(username: String, password: String): AuthResult {
        val user = userRepository.findByUsername(username)
        if (user == null) {
            auditLogger.log(
                actor = username,
                action = AuditAction.LOGIN_FAILED,
                entityType = "User",
                entityId = "unknown",
                details = "User not found"
            )
            return AuthResult.InvalidCredentials
        }

        if (!user.isActive) {
            auditLogger.log(
                actor = user.id,
                action = AuditAction.LOGIN_FAILED,
                entityType = "User",
                entityId = user.id,
                details = "Account inactive"
            )
            return AuthResult.UserInactive
        }

        val credential = authRepository.getCredential(user.id)
            ?: return AuthResult.InvalidCredentials

        // Check lockout
        val now = Instant.now()
        if (credential.lockedUntil != null && now.isBefore(credential.lockedUntil)) {
            auditLogger.log(
                actor = user.id,
                action = AuditAction.LOGIN_FAILED,
                entityType = "User",
                entityId = user.id,
                details = "Account locked until ${credential.lockedUntil}"
            )
            return AuthResult.Locked(credential.lockedUntil)
        }

        // Verify password
        if (!passwordHasher.verify(password, credential.salt, credential.passwordHash)) {
            authRepository.incrementFailedAttempts(user.id)
            val updatedFailedAttempts = credential.failedAttempts + 1

            if (updatedFailedAttempts >= SessionConfig.MAX_FAILED_ATTEMPTS) {
                val lockUntil = now.plusSeconds(SessionConfig.LOCKOUT_DURATION_MINUTES * 60)
                authRepository.lockAccount(user.id, lockUntil)
                auditLogger.log(
                    actor = user.id,
                    action = AuditAction.ACCOUNT_LOCKED,
                    entityType = "User",
                    entityId = user.id,
                    details = "Locked after $updatedFailedAttempts failed attempts until $lockUntil"
                )
                return AuthResult.Locked(lockUntil)
            }

            auditLogger.log(
                actor = user.id,
                action = AuditAction.LOGIN_FAILED,
                entityType = "User",
                entityId = user.id,
                details = "Invalid password, attempt $updatedFailedAttempts of ${SessionConfig.MAX_FAILED_ATTEMPTS}"
            )
            return AuthResult.InvalidCredentials
        }

        // Successful login
        authRepository.resetFailedAttempts(user.id)
        authRepository.updateLastLogin(user.id, now)

        // End any existing active session
        val existingSession = sessionRepository.getActive(user.id)
        if (existingSession != null) {
            sessionRepository.end(existingSession.id, now, "new_login")
        }

        val session = Session(
            id = UUID.randomUUID().toString(),
            userId = user.id,
            startedAt = now,
            lastActiveAt = now,
            endedAt = null,
            endReason = null
        )
        sessionRepository.create(session)

        // Establish the authoritative session context. ALL subsequent privileged
        // operations resolve acting identity from SessionManager, never from caller args.
        sessionManager.set(
            SessionContext(
                userId = user.id,
                displayName = user.displayName,
                roles = user.roles.toSet(),
                sessionId = session.id
            )
        )

        auditLogger.log(
            actor = user.id,
            action = AuditAction.LOGIN_SUCCESS,
            entityType = "Session",
            entityId = session.id,
            details = "Login successful"
        )

        return AuthResult.Success(session = session, user = user)
    }
}
