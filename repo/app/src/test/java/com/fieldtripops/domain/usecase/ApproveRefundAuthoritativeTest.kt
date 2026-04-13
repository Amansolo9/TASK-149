package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.booking.RefundEngine
import com.fieldtripops.domain.model.BookingOrder
import com.fieldtripops.domain.model.BookingState
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.repository.RefundDecisionRepository
import com.fieldtripops.domain.repository.RefundRuleRepository
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class ApproveRefundAuthoritativeTest {

    private lateinit var bookingRepo: BookingRepository
    private lateinit var refundRepo: RefundDecisionRepository
    private lateinit var ruleRepo: RefundRuleRepository
    private lateinit var auditLogger: AuditLogger
    private lateinit var session: SessionManager
    private lateinit var uc: ApproveRefundUseCase

    @Before fun setup() {
        bookingRepo = mockk(relaxed = true)
        refundRepo = mockk(relaxed = true)
        ruleRepo = mockk(relaxed = true)
        coEvery { ruleRepo.listActive() } returns emptyList()
        auditLogger = mockk(relaxed = true)
        session = SessionManager().also {
            it.set(SessionContext("admin-1", "Admin One", setOf(Role.Administrator), "s1"))
        }
        uc = ApproveRefundUseCase(refundRepo, bookingRepo, auditLogger, session, ruleRepo)
    }

    private fun booking(paid: BigDecimal, tripInDays: Long) = BookingOrder(
        id = "b1", itineraryId = "i1", travelerId = "u1",
        inventorySlotId = "s1", partySize = 2, state = BookingState.Booked,
        createdAt = Instant.now(), updatedAt = Instant.now(),
        confirmedAt = null, confirmedBy = null,
        cancelledAt = null, cancelReason = null,
        lastActivityAt = Instant.now(),
        tripStartAt = Instant.now().plus(Duration.ofDays(tripInDays)),
        tripEndAt = Instant.now().plus(Duration.ofDays(tripInDays + 2)),
        paidTotal = paid
    )

    @Test
    fun `refund uses booking paid total not caller args`() = runTest {
        coEvery { bookingRepo.findById("b1") } returns booking(BigDecimal("200.00"), 10)

        val r = uc.execute("b1") as ApproveRefundUseCase.Result.Approved
        // The use case reads paidTotal from the persisted booking, not caller input.
        assertThat(r.decision.paidTotal).isEqualTo(BigDecimal("200.00"))
        assertThat(r.decision.refundAmount).isEqualTo(BigDecimal("200.00"))
        assertThat(r.decision.approverUserId).isEqualTo("admin-1")
        assertThat(r.decision.approverName).isEqualTo("Admin One")
    }

    @Test
    fun `spoofed override cannot exceed persisted paid total`() = runTest {
        coEvery { bookingRepo.findById("b1") } returns booking(BigDecimal("50.00"), 10)
        val r = uc.execute(
            bookingOrderId = "b1",
            manualOverrideAmount = BigDecimal("1000.00"),
            manualOverrideReason = "fraud try"
        )
        assertThat(r).isInstanceOf(ApproveRefundUseCase.Result.Invalid::class.java)
    }

    @Test
    fun `booking not found yields explicit result`() = runTest {
        coEvery { bookingRepo.findById("nope") } returns null
        val r = uc.execute("nope")
        assertThat(r).isEqualTo(ApproveRefundUseCase.Result.BookingNotFound)
    }
}
