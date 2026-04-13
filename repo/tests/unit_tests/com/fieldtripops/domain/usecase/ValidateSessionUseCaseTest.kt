package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.Session
import com.fieldtripops.domain.repository.SessionRepository
import com.fieldtripops.security.SessionConfig
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ValidateSessionUseCaseTest {

    private lateinit var sessionRepository: SessionRepository
    private lateinit var auditLogger: AuditLogger
    private lateinit var useCase: ValidateSessionUseCase

    @Before
    fun setup() {
        sessionRepository = mockk(relaxed = true)
        auditLogger = mockk(relaxed = true)
        useCase = ValidateSessionUseCase(sessionRepository, auditLogger)
    }

    @Test
    fun `valid session returns Valid and touches lastActive`() = runTest {
        val session = Session(
            id = "sess-1",
            userId = "user-1",
            startedAt = Instant.now().minusSeconds(60),
            lastActiveAt = Instant.now().minusSeconds(10),
            endedAt = null,
            endReason = null
        )
        coEvery { sessionRepository.getActive("user-1") } returns session

        val result = useCase.execute("user-1")

        assertThat(result).isInstanceOf(ValidateSessionUseCase.Result.Valid::class.java)
        coVerify { sessionRepository.touchLastActive("sess-1", any()) }
    }

    @Test
    fun `expired session returns Expired and ends session`() = runTest {
        val session = Session(
            id = "sess-1",
            userId = "user-1",
            startedAt = Instant.now().minusSeconds(7200),
            lastActiveAt = Instant.now().minusSeconds(
                (SessionConfig.SESSION_TIMEOUT_MINUTES + 1) * 60
            ),
            endedAt = null,
            endReason = null
        )
        coEvery { sessionRepository.getActive("user-1") } returns session

        val result = useCase.execute("user-1")

        assertThat(result).isEqualTo(ValidateSessionUseCase.Result.Expired)
        coVerify { sessionRepository.end("sess-1", any(), "timeout") }
        coVerify { auditLogger.log("system", AuditAction.SESSION_EXPIRED, any(), any(), any()) }
    }

    @Test
    fun `no active session returns NoSession`() = runTest {
        coEvery { sessionRepository.getActive("user-1") } returns null

        val result = useCase.execute("user-1")

        assertThat(result).isEqualTo(ValidateSessionUseCase.Result.NoSession)
    }
}
