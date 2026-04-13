package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.ConsentRecord
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.ConsentRepository
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
import java.time.Instant

class RecordConsentUseCaseTest {

    private lateinit var repo: ConsentRepository
    private lateinit var audit: AuditLogger
    private lateinit var session: SessionManager
    private lateinit var uc: RecordConsentUseCase

    @Before fun setup() {
        repo = mockk(relaxed = true)
        audit = mockk(relaxed = true)
        session = SessionManager()
        uc = RecordConsentUseCase(repo, audit, session)
    }

    @Test
    fun `grant uses session userId not caller`() = runTest {
        session.set(SessionContext("alice", "Alice", setOf(Role.Traveler), "s1"))
        val c = uc.grant("analytics", "1.0")
        assertThat(c.userId).isEqualTo("alice")
        coVerify { repo.record(match { it.userId == "alice" }) }
    }

    @Test
    fun `revoke rejects consent not owned by session user`() = runTest {
        // Alice logged in, but the target consent belongs to Bob.
        session.set(SessionContext("alice", "A", setOf(Role.Traveler), "s1"))
        coEvery { repo.getActiveConsents("alice") } returns emptyList()
        try {
            uc.revoke("bob-consent-id")
            assert(false) { "Should have thrown OwnershipViolationException" }
        } catch (_: OwnershipViolationException) {
            /* expected */
        }
    }

    @Test
    fun `revoke succeeds when consent belongs to session user`() = runTest {
        session.set(SessionContext("alice", "A", setOf(Role.Traveler), "s1"))
        val mine = ConsentRecord(
            id = "c1", userId = "alice", consentType = "analytics",
            granted = true, grantedAt = Instant.now(), revokedAt = null,
            policyVersion = "1.0"
        )
        coEvery { repo.getActiveConsents("alice") } returns listOf(mine)
        uc.revoke("c1")
        coVerify { repo.revoke("c1", any()) }
    }
}
