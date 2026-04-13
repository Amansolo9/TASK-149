package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.SessionRepository
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class LogoutUseCaseTest {

    private lateinit var sessionRepository: SessionRepository
    private lateinit var auditLogger: AuditLogger
    private lateinit var sessionManager: SessionManager
    private lateinit var useCase: LogoutUseCase

    @Before
    fun setup() {
        sessionRepository = mockk(relaxed = true)
        auditLogger = mockk(relaxed = true)
        sessionManager = SessionManager()
        useCase = LogoutUseCase(sessionRepository, auditLogger, sessionManager)
    }

    @Test
    fun `logout ends active session and clears session manager`() = runTest {
        sessionManager.set(SessionContext("u1", "U1", setOf(Role.Traveler), "sess-1"))
        useCase.execute()
        coVerify { sessionRepository.end("sess-1", any(), "user_logout") }
        coVerify { auditLogger.log("u1", AuditAction.LOGOUT, "Session", "sess-1", any()) }
        assertThat(sessionManager.current()).isNull()
    }

    @Test
    fun `logout with no active session is a no-op`() = runTest {
        useCase.execute()
        coVerify(exactly = 0) { sessionRepository.end(any(), any(), any()) }
    }
}
