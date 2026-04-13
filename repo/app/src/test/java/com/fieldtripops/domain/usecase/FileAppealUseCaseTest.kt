package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.domain.model.ClaimClassification
import com.fieldtripops.domain.model.ClaimStyle
import com.fieldtripops.domain.model.ClaimTicket
import com.fieldtripops.domain.model.Responsibility
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.model.TicketState
import com.fieldtripops.domain.repository.ClaimRepository
import com.fieldtripops.domain.repository.OfflineQueueRepository
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

class FileAppealUseCaseTest {

    private lateinit var db: FieldTripDatabase
    private lateinit var claimRepo: ClaimRepository
    private lateinit var queueRepo: OfflineQueueRepository
    private lateinit var auditLogger: AuditLogger
    private lateinit var session: SessionManager
    private lateinit var uc: FileAppealUseCase

    @Before fun setup() {
        // FileAppealUseCase calls db.withTransaction{...}; here we don't test
        // transaction integrity (that's in instrumented tests). We mock db so
        // the transaction extension passes through via relaxed behavior for
        // the cases that don't reach the transaction body.
        db = mockk(relaxed = true)
        claimRepo = mockk(relaxed = true)
        queueRepo = mockk(relaxed = true)
        auditLogger = mockk(relaxed = true)
        session = SessionManager()
        uc = FileAppealUseCase(db, claimRepo, queueRepo, auditLogger, session)
    }

    private fun ticket(state: TicketState, owner: String = "alice") = ClaimTicket(
        id = "t1", travelerId = owner, bookingOrderId = "b1",
        claimStyle = ClaimStyle.REFUND_ONLY,
        classification = ClaimClassification.PROVIDER_NO_SHOW,
        responsibility = Responsibility.PROVIDER,
        description = "x", state = state,
        createdAt = Instant.now(), updatedAt = Instant.now(),
        firstResponseAt = null, resolvedAt = null, closedAt = null,
        lastTravelerActivityAt = Instant.now()
    )

    @Test
    fun `cannot appeal ticket in Draft`() = runTest {
        coEvery { claimRepo.findById("t1") } returns ticket(TicketState.Draft)
        session.set(SessionContext("alice", "A", setOf(Role.Traveler), "s1"))
        val r = uc.execute("t1", "bad decision")
        assertThat(r).isInstanceOf(FileAppealUseCase.Result.InvalidState::class.java)
    }

    @Test
    fun `non-owner non-staff cannot appeal`() = runTest {
        coEvery { claimRepo.findById("t1") } returns ticket(TicketState.Resolved, owner = "alice")
        session.set(SessionContext("bob", "B", setOf(Role.Traveler), "s1"))
        try {
            uc.execute("t1", "mine")
            assert(false) { "Should have thrown OwnershipViolationException" }
        } catch (_: OwnershipViolationException) {
            /* expected */
        }
    }

    @Test
    fun `empty reason rejected`() = runTest {
        coEvery { claimRepo.findById("t1") } returns ticket(TicketState.Resolved)
        session.set(SessionContext("alice", "A", setOf(Role.Traveler), "s1"))
        val r = uc.execute("t1", "   ")
        assertThat(r).isEqualTo(FileAppealUseCase.Result.ReasonRequired)
    }
}
