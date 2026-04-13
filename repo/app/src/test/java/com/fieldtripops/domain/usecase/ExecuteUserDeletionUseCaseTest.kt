package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.DeletionRequest
import com.fieldtripops.domain.model.DeletionScope
import com.fieldtripops.domain.model.DeletionState
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.DeletionRequestRepository
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import com.fieldtripops.security.auth.UnauthorizedException
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ExecuteUserDeletionUseCaseTest {

    private lateinit var repo: DeletionRequestRepository
    private lateinit var audit: AuditLogger
    private lateinit var session: SessionManager
    private lateinit var uc: ExecuteUserDeletionUseCase

    private fun req(id: String, state: DeletionState = DeletionState.Requested) = DeletionRequest(
        id = id, targetUserId = "user-target", requestedBy = "u1",
        requestedAt = Instant.now(), reason = "privacy", state = state,
        approvedBy = null, approvedAt = null, executedBy = null, executedAt = null,
        failureReason = null, scope = DeletionScope.ANONYMIZE
    )

    @Before
    fun setup() {
        repo = mockk(relaxed = true)
        coEvery { repo.executeDeletion(any(), any()) } returns 3
        audit = mockk(relaxed = true)
        session = SessionManager()
        uc = ExecuteUserDeletionUseCase(repo, audit, session)
    }

    @Test(expected = UnauthorizedException::class)
    fun `non-admin cannot execute deletion`() = runTest {
        session.set(SessionContext("u1", "U", setOf(Role.Traveler), "s1"))
        uc.execute("r1")
    }

    @Test
    fun `admin executes pending deletion and audits chain`() = runTest {
        coEvery { repo.findById("r1") } returns req("r1")
        session.set(SessionContext("admin1", "Admin", setOf(Role.Administrator), "s1"))

        val r = uc.execute("r1") as ExecuteUserDeletionUseCase.Result.Executed
        assertThat(r.request.state).isEqualTo(DeletionState.Executed)
        coVerify { repo.executeDeletion("user-target", DeletionScope.ANONYMIZE) }
        coVerify {
            audit.log("admin1", AuditAction.DELETION_APPROVED, "DeletionRequest", "r1", any())
        }
        coVerify {
            audit.log("admin1", AuditAction.DELETION_EXECUTED, "DeletionRequest", "r1", any())
        }
        coVerify {
            audit.log("admin1", AuditAction.DATA_ANONYMIZED, "User", "user-target", any())
        }
    }

    @Test
    fun `already executed returns AlreadyExecuted (no double delete)`() = runTest {
        coEvery { repo.findById("r1") } returns req("r1", DeletionState.Executed)
        session.set(SessionContext("admin1", "Admin", setOf(Role.Administrator), "s1"))
        val r = uc.execute("r1")
        assertThat(r).isInstanceOf(ExecuteUserDeletionUseCase.Result.AlreadyExecuted::class.java)
        coVerify(exactly = 0) { repo.executeDeletion(any(), any()) }
    }

    @Test
    fun `failure path writes DELETION_FAILED and sets Failed state`() = runTest {
        coEvery { repo.findById("r1") } returns req("r1")
        coEvery { repo.executeDeletion(any(), any()) } throws RuntimeException("io fail")
        session.set(SessionContext("admin1", "Admin", setOf(Role.Administrator), "s1"))
        val r = uc.execute("r1")
        assertThat(r).isInstanceOf(ExecuteUserDeletionUseCase.Result.Failed::class.java)
        coVerify {
            audit.log("admin1", AuditAction.DELETION_FAILED, "DeletionRequest", "r1", any())
        }
    }
}
