package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.DeletionScope
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.DeletionRequestRepository
import com.fieldtripops.security.auth.OwnershipViolationException
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class RequestUserDeletionUseCaseTest {

    private lateinit var repo: DeletionRequestRepository
    private lateinit var audit: AuditLogger
    private lateinit var session: SessionManager
    private lateinit var uc: RequestUserDeletionUseCase

    @Before
    fun setup() {
        repo = mockk(relaxed = true)
        coEvery { repo.hasOpenFor(any()) } returns false
        coEvery { repo.findByTarget(any()) } returns emptyList()
        audit = mockk(relaxed = true)
        session = SessionManager()
        uc = RequestUserDeletionUseCase(repo, audit, session)
    }

    @Test
    fun `traveler can request deletion of own data`() = runTest {
        session.set(SessionContext("u1", "U1", setOf(Role.Traveler), "s1"))
        val r = uc.execute("u1", "privacy")
        assertThat(r).isInstanceOf(RequestUserDeletionUseCase.Result.Queued::class.java)
        coVerify { repo.save(any()) }
        coVerify {
            audit.log("u1", AuditAction.DELETION_REQUESTED, "DeletionRequest", any(), any())
        }
    }

    @Test(expected = OwnershipViolationException::class)
    fun `traveler cannot request deletion of another users data`() = runTest {
        session.set(SessionContext("u1", "U1", setOf(Role.Traveler), "s1"))
        uc.execute("u2", "malice")
    }

    @Test
    fun `admin can request deletion of another user`() = runTest {
        session.set(SessionContext("admin1", "Admin", setOf(Role.Administrator), "s1"))
        val r = uc.execute("u2", "legal hold expired", DeletionScope.HARD_DELETE)
        assertThat(r).isInstanceOf(RequestUserDeletionUseCase.Result.Queued::class.java)
    }

    @Test
    fun `repeated request is idempotent — returns AlreadyPending`() = runTest {
        coEvery { repo.hasOpenFor("u1") } returns true
        coEvery { repo.findByTarget("u1") } returns listOf(mockk(relaxed = true))
        session.set(SessionContext("u1", "U1", setOf(Role.Traveler), "s1"))
        val r = uc.execute("u1", null)
        assertThat(r).isInstanceOf(RequestUserDeletionUseCase.Result.AlreadyPending::class.java)
    }

    @Test
    fun `blank target rejected`() = runTest {
        session.set(SessionContext("u1", "U1", setOf(Role.Traveler), "s1"))
        val r = uc.execute("", null)
        assertThat(r).isInstanceOf(RequestUserDeletionUseCase.Result.Invalid::class.java)
    }
}
