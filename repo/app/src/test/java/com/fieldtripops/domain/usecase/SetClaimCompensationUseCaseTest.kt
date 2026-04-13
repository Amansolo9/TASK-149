package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.ClaimClassification
import com.fieldtripops.domain.model.ClaimStyle
import com.fieldtripops.domain.model.ClaimTicket
import com.fieldtripops.domain.model.CompensationCalculation
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
import java.math.BigDecimal
import java.time.Instant

class SetClaimCompensationUseCaseTest {

    private lateinit var repo: ClaimRepository
    private lateinit var audit: AuditLogger
    private lateinit var session: SessionManager
    private lateinit var uc: SetClaimCompensationUseCase

    private fun ticket(state: TicketState = TicketState.InReview) = ClaimTicket(
        id = "t1", travelerId = "u1", bookingOrderId = "b1",
        claimStyle = ClaimStyle.REFUND_ONLY,
        classification = ClaimClassification.PROVIDER_NO_SHOW,
        responsibility = Responsibility.PROVIDER,
        description = "", state = state,
        createdAt = Instant.now(), updatedAt = Instant.now(),
        firstResponseAt = null, resolvedAt = null, closedAt = null,
        lastTravelerActivityAt = Instant.now(),
        compensation = null
    )

    @Before
    fun setup() {
        repo = mockk(relaxed = true)
        audit = mockk(relaxed = true)
        session = SessionManager()
        coEvery { repo.findById("t1") } returns ticket()
        uc = SetClaimCompensationUseCase(repo, audit, session)
    }

    @Test(expected = UnauthorizedException::class)
    fun `traveler cannot set compensation`() = runTest {
        session.set(SessionContext("u1", "U", setOf(Role.Traveler), "s1"))
        uc.execute("t1", BigDecimal("50.00"), "USD", "SERVICE_NOT_DELIVERED")
    }

    @Test
    fun `reviewer can set compensation, persisted with approver identity`() = runTest {
        session.set(SessionContext("rev1", "Reviewer A", setOf(Role.Reviewer), "s1"))
        var capturedComp: CompensationCalculation? = null
        coEvery { repo.setCompensation("t1", any(), any()) } coAnswers { capturedComp = secondArg(); Unit }
        val updated = ticket().copy(
            compensation = CompensationCalculation(
                amount = BigDecimal("50.00"), currency = "USD",
                basis = "SERVICE_NOT_DELIVERED",
                approverId = "rev1", approverName = "Reviewer A",
                decidedAt = Instant.now(), note = null
            )
        )
        coEvery { repo.findById("t1") } returns ticket() andThen updated

        val r = uc.execute("t1", BigDecimal("50.00"), "USD", "SERVICE_NOT_DELIVERED", "root cause noted")
                as SetClaimCompensationUseCase.Result.Updated
        assertThat(r.ticket.compensation?.amount).isEqualTo(BigDecimal("50.00"))
        assertThat(capturedComp!!.approverId).isEqualTo("rev1")
        assertThat(capturedComp!!.currency).isEqualTo("USD")
        coVerify { audit.log("rev1", AuditAction.CLAIM_COMPENSATION_SET, "ClaimTicket", "t1", any()) }
    }

    @Test
    fun `negative amount rejected`() = runTest {
        session.set(SessionContext("rev1", "R", setOf(Role.Reviewer), "s1"))
        val r = uc.execute("t1", BigDecimal("-1.00"), "USD", "X")
        assertThat(r).isInstanceOf(SetClaimCompensationUseCase.Result.Invalid::class.java)
    }

    @Test
    fun `blank basis rejected`() = runTest {
        session.set(SessionContext("rev1", "R", setOf(Role.Reviewer), "s1"))
        val r = uc.execute("t1", BigDecimal("10.00"), "USD", "")
        assertThat(r).isInstanceOf(SetClaimCompensationUseCase.Result.Invalid::class.java)
    }

    @Test
    fun `terminal non-resolved ticket rejects compensation edit`() = runTest {
        session.set(SessionContext("rev1", "R", setOf(Role.Reviewer), "s1"))
        coEvery { repo.findById("t1") } returns ticket(TicketState.Closed)
        val r = uc.execute("t1", BigDecimal("10.00"), "USD", "X")
        assertThat(r).isInstanceOf(SetClaimCompensationUseCase.Result.Invalid::class.java)
    }
}
