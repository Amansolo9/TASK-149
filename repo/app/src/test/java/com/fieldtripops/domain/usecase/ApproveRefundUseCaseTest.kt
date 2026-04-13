package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.BookingOrder
import com.fieldtripops.domain.model.BookingState
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.repository.RefundDecisionRepository
import com.fieldtripops.domain.repository.RefundRuleRepository
import io.mockk.coEvery
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import com.fieldtripops.security.auth.UnauthorizedException
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class ApproveRefundUseCaseTest {

    private lateinit var repository: RefundDecisionRepository
    private lateinit var bookingRepository: BookingRepository
    private lateinit var auditLogger: AuditLogger
    private lateinit var sessionManager: SessionManager
    private lateinit var refundRuleRepository: RefundRuleRepository
    private lateinit var useCase: ApproveRefundUseCase

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        bookingRepository = mockk(relaxed = true)
        auditLogger = mockk(relaxed = true)
        sessionManager = SessionManager().also {
            it.set(SessionContext("agent-1", "Agent A", setOf(Role.Agent), "s1"))
        }
        refundRuleRepository = mockk(relaxed = true)
        coEvery { refundRuleRepository.listActive() } returns emptyList() // fall back to defaults
        useCase = ApproveRefundUseCase(repository, bookingRepository, auditLogger, sessionManager, refundRuleRepository)
    }

    private fun bookingWith(paidTotal: BigDecimal, tripStartAt: Instant): BookingOrder {
        val now = Instant.now()
        return BookingOrder(
            id = "b1", itineraryId = "i1", travelerId = "traveler-1",
            inventorySlotId = "s1", partySize = 2,
            state = BookingState.Booked,
            createdAt = now, updatedAt = now,
            confirmedAt = now, confirmedBy = "agent-1",
            cancelledAt = null, cancelReason = null,
            lastActivityAt = now,
            tripStartAt = tripStartAt,
            tripEndAt = tripStartAt.plus(Duration.ofDays(1)),
            paidTotal = paidTotal
        )
    }

    @Test
    fun `agent can approve full refund for trip far away`() = runTest {
        val tripStart = Instant.now().plus(Duration.ofDays(5))
        coEvery { bookingRepository.findById("b1") } returns bookingWith(BigDecimal("100.00"), tripStart)
        val r = useCase.execute("b1") as ApproveRefundUseCase.Result.Approved
        assertThat(r.decision.refundAmount).isEqualTo(BigDecimal("100.00"))
        assertThat(r.decision.approverUserId).isEqualTo("agent-1")
        assertThat(r.decision.approverName).isEqualTo("Agent A")
        coVerify { repository.record(any()) }
    }

    @Test(expected = UnauthorizedException::class)
    fun `traveler cannot approve refund`() = runTest {
        sessionManager.set(SessionContext("u1", "U", setOf(Role.Traveler), "s2"))
        coEvery { bookingRepository.findById("b1") } returns bookingWith(
            BigDecimal("50.00"), Instant.now().plus(Duration.ofDays(3))
        )
        useCase.execute("b1")
    }

    @Test
    fun `approver identity always from session never from caller`() = runTest {
        sessionManager.set(SessionContext("admin-1", "Real Admin", setOf(Role.Administrator), "s3"))
        val tripStart = Instant.now().plus(Duration.ofDays(3))
        coEvery { bookingRepository.findById("b1") } returns bookingWith(BigDecimal("100.00"), tripStart)
        val captured = slot<com.fieldtripops.domain.model.RefundDecision>()
        coEvery { repository.record(capture(captured)) } returns Unit
        useCase.execute("b1")
        assertThat(captured.captured.approverUserId).isEqualTo("admin-1")
        assertThat(captured.captured.approverName).isEqualTo("Real Admin")
    }

    @Test
    fun `refund cannot exceed paid total`() = runTest {
        val tripStart = Instant.now().plus(Duration.ofDays(3))
        coEvery { bookingRepository.findById("b1") } returns bookingWith(BigDecimal("100.00"), tripStart)
        val r = useCase.execute(
            "b1",
            manualOverrideAmount = BigDecimal("200.00"),
            manualOverrideReason = "loyalty"
        )
        assertThat(r).isInstanceOf(ApproveRefundUseCase.Result.Invalid::class.java)
    }

    @Test
    fun `refund uses stored rules when present — admin can change outcome`() = runTest {
        sessionManager.set(SessionContext("admin-1", "Admin", setOf(Role.Administrator), "s4"))
        coEvery { refundRuleRepository.listActive() } returns listOf(
            com.fieldtripops.domain.model.RefundRule(
                id = "x", code = "ADMIN_FULL",
                minHoursBeforeStartExclusive = -1,
                maxHoursBeforeStartInclusive = null,
                refundPercent = 100, description = "always 100%",
                active = true, updatedAt = Instant.EPOCH, updatedBy = "admin1"
            )
        )
        val tripStart = Instant.now().plus(Duration.ofHours(1))
        coEvery { bookingRepository.findById("b1") } returns bookingWith(BigDecimal("100.00"), tripStart)
        val r = useCase.execute("b1") as ApproveRefundUseCase.Result.Approved
        assertThat(r.decision.refundPercent).isEqualTo(100)
        assertThat(r.decision.ruleUsed).isEqualTo("ADMIN_FULL")
    }

    @Test
    fun `manual override without reason is rejected`() = runTest {
        val tripStart = Instant.now().plus(Duration.ofDays(3))
        coEvery { bookingRepository.findById("b1") } returns bookingWith(BigDecimal("100.00"), tripStart)
        val r = useCase.execute(
            "b1",
            manualOverrideAmount = BigDecimal("50.00"),
            manualOverrideReason = null
        )
        assertThat(r).isInstanceOf(ApproveRefundUseCase.Result.Invalid::class.java)
    }

    @Test
    fun `booking not found returns BookingNotFound`() = runTest {
        coEvery { bookingRepository.findById("missing") } returns null
        val r = useCase.execute("missing")
        assertThat(r).isInstanceOf(ApproveRefundUseCase.Result.BookingNotFound::class.java)
    }
}
