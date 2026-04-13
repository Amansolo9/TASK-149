package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.AuthResult
import com.fieldtripops.domain.model.Credential
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.model.Session
import com.fieldtripops.domain.model.User
import com.fieldtripops.domain.repository.AuthRepository
import com.fieldtripops.domain.repository.SessionRepository
import com.fieldtripops.domain.repository.UserRepository
import com.fieldtripops.security.PasswordHasher
import com.fieldtripops.security.auth.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class LoginUseCaseTest {

    private lateinit var userRepository: UserRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var passwordHasher: PasswordHasher
    private lateinit var auditLogger: AuditLogger
    private lateinit var sessionManager: SessionManager
    private lateinit var useCase: LoginUseCase

    private val testSalt = PasswordHasher().generateSalt()
    private val testHash = PasswordHasher().hash("correctPassword", testSalt)

    private val testUser = User(
        id = "user-1",
        username = "testuser",
        displayName = "Test User",
        roles = listOf(Role.Traveler),
        isActive = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private val testCredential = Credential(
        userId = "user-1",
        passwordHash = testHash,
        salt = testSalt,
        failedAttempts = 0,
        lockedUntil = null,
        lastLoginAt = null
    )

    @Before
    fun setup() {
        userRepository = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        sessionRepository = mockk(relaxed = true)
        passwordHasher = PasswordHasher()
        auditLogger = mockk(relaxed = true)

        sessionManager = SessionManager()
        useCase = LoginUseCase(
            userRepository, authRepository, sessionRepository,
            passwordHasher, auditLogger, sessionManager
        )
    }

    @Test
    fun `successful login returns Success with session and user`() = runTest {
        coEvery { userRepository.findByUsername("testuser") } returns testUser
        coEvery { authRepository.getCredential("user-1") } returns testCredential
        coEvery { sessionRepository.getActive("user-1") } returns null

        val result = useCase.execute("testuser", "correctPassword")

        assertThat(result).isInstanceOf(AuthResult.Success::class.java)
        val success = result as AuthResult.Success
        assertThat(success.user.id).isEqualTo("user-1")
        assertThat(success.session.userId).isEqualTo("user-1")

        coVerify { authRepository.resetFailedAttempts("user-1") }
        coVerify { sessionRepository.create(any()) }
        coVerify { auditLogger.log("user-1", AuditAction.LOGIN_SUCCESS, any(), any(), any()) }
    }

    @Test
    fun `unknown user returns InvalidCredentials`() = runTest {
        coEvery { userRepository.findByUsername("unknown") } returns null

        val result = useCase.execute("unknown", "password")

        assertThat(result).isEqualTo(AuthResult.InvalidCredentials)
        coVerify { auditLogger.log("unknown", AuditAction.LOGIN_FAILED, any(), any(), any()) }
    }

    @Test
    fun `inactive user returns UserInactive`() = runTest {
        val inactiveUser = testUser.copy(isActive = false)
        coEvery { userRepository.findByUsername("testuser") } returns inactiveUser

        val result = useCase.execute("testuser", "correctPassword")

        assertThat(result).isEqualTo(AuthResult.UserInactive)
    }

    @Test
    fun `wrong password returns InvalidCredentials and increments attempts`() = runTest {
        coEvery { userRepository.findByUsername("testuser") } returns testUser
        coEvery { authRepository.getCredential("user-1") } returns testCredential

        val result = useCase.execute("testuser", "wrongPassword")

        assertThat(result).isEqualTo(AuthResult.InvalidCredentials)
        coVerify { authRepository.incrementFailedAttempts("user-1") }
        coVerify { auditLogger.log("user-1", AuditAction.LOGIN_FAILED, any(), any(), any()) }
    }

    @Test
    fun `fifth failed attempt locks account`() = runTest {
        val credWith4Failures = testCredential.copy(failedAttempts = 4)
        coEvery { userRepository.findByUsername("testuser") } returns testUser
        coEvery { authRepository.getCredential("user-1") } returns credWith4Failures

        val result = useCase.execute("testuser", "wrongPassword")

        assertThat(result).isInstanceOf(AuthResult.Locked::class.java)
        coVerify { authRepository.lockAccount("user-1", any()) }
        coVerify { auditLogger.log("user-1", AuditAction.ACCOUNT_LOCKED, any(), any(), any()) }
    }

    @Test
    fun `locked account returns Locked`() = runTest {
        val lockedCred = testCredential.copy(
            lockedUntil = Instant.now().plusSeconds(600)
        )
        coEvery { userRepository.findByUsername("testuser") } returns testUser
        coEvery { authRepository.getCredential("user-1") } returns lockedCred

        val result = useCase.execute("testuser", "correctPassword")

        assertThat(result).isInstanceOf(AuthResult.Locked::class.java)
    }

    @Test
    fun `expired lock allows login`() = runTest {
        val expiredLockCred = testCredential.copy(
            lockedUntil = Instant.now().minusSeconds(60)
        )
        coEvery { userRepository.findByUsername("testuser") } returns testUser
        coEvery { authRepository.getCredential("user-1") } returns expiredLockCred
        coEvery { sessionRepository.getActive("user-1") } returns null

        val result = useCase.execute("testuser", "correctPassword")

        assertThat(result).isInstanceOf(AuthResult.Success::class.java)
    }

    @Test
    fun `existing session is ended on new login`() = runTest {
        val existingSession = Session(
            id = "old-session",
            userId = "user-1",
            startedAt = Instant.now().minusSeconds(3600),
            lastActiveAt = Instant.now().minusSeconds(300),
            endedAt = null,
            endReason = null
        )
        coEvery { userRepository.findByUsername("testuser") } returns testUser
        coEvery { authRepository.getCredential("user-1") } returns testCredential
        coEvery { sessionRepository.getActive("user-1") } returns existingSession

        val result = useCase.execute("testuser", "correctPassword")

        assertThat(result).isInstanceOf(AuthResult.Success::class.java)
        coVerify { sessionRepository.end("old-session", any(), "new_login") }
    }
}
