package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.ClaimClassification
import com.fieldtripops.domain.model.ClaimStyle
import com.fieldtripops.domain.model.ClaimTicket
import com.fieldtripops.domain.model.Responsibility
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.model.TicketState
import com.fieldtripops.domain.repository.ClaimRepository
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

class TransitionClaimAuthzTest {

    private lateinit var repo: ClaimRepository
    private lateinit var audit: AuditLogger
    private lateinit var session: SessionManager
    private lateinit var uc: TransitionClaimUseCase

    @Before fun setup() {
        repo = mockk(relaxed = true); audit = mockk(relaxed = true)
        session = SessionManager(); uc = TransitionClaimUseCase(repo, audit, session)
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

    @Test(expected = UnauthorizedException::class)
    fun `traveler cannot move ticket to InReview`() = runTest {
        coEvery { repo.findById("t1") } returns ticket(TicketState.Submitted)
        session.set(SessionContext("alice", "A", setOf(Role.Traveler), "s1"))
        uc.execute("t1", TicketState.InReview, null)
    }

    @Test
    fun `reviewer can move Submitted to InReview`() = runTest {
        coEvery { repo.findById("t1") } returns ticket(TicketState.Submitted)
        session.set(SessionContext("rev", "R", setOf(Role.Reviewer), "s1"))
        val r = uc.execute("t1", TicketState.InReview, "ack")
        assertThat(r).isEqualTo(TransitionClaimUseCase.Result.Transitioned)
        coVerify { repo.transition("t1", TicketState.Submitted, TicketState.InReview, "rev", "ack", any()) }
    }

    @Test(expected = UnauthorizedException::class)
    fun `traveler cannot finalize`() = runTest {
        coEvery { repo.findById("t1") } returns ticket(TicketState.Appealed)
        session.set(SessionContext("alice", "A", setOf(Role.Traveler), "s1"))
        uc.execute("t1", TicketState.Finalized, "no")
    }

    @Test
    fun `admin can finalize`() = runTest {
        coEvery { repo.findById("t1") } returns ticket(TicketState.Appealed)
        session.set(SessionContext("adm", "A", setOf(Role.Administrator), "s1"))
        val r = uc.execute("t1", TicketState.Finalized, "final")
        assertThat(r).isEqualTo(TransitionClaimUseCase.Result.Transitioned)
    }

    @Test
    fun `traveler-owner can submit own draft`() = runTest {
        coEvery { repo.findById("t1") } returns ticket(TicketState.Draft, owner = "alice")
        session.set(SessionContext("alice", "A", setOf(Role.Traveler), "s1"))
        val r = uc.execute("t1", TicketState.Submitted, null)
        assertThat(r).isEqualTo(TransitionClaimUseCase.Result.Transitioned)
    }
}
