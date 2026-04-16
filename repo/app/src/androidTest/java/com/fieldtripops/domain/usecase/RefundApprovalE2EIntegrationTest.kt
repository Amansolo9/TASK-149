package com.fieldtripops.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.entity.BookingOrderEntity
import com.fieldtripops.data.entity.UserEntity
import com.fieldtripops.data.repository.BookingRepositoryImpl
import com.fieldtripops.data.repository.RefundDecisionRepositoryImpl
import com.fieldtripops.data.repository.RefundRuleRepositoryImpl
import com.fieldtripops.domain.booking.RefundEngine
import com.fieldtripops.domain.model.RefundRule
import com.fieldtripops.domain.model.Role
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/**
 * End-to-end refund approval test with real Room DB. Validates:
 *  - paidTotal is read from persisted booking, not caller input
 *  - Refund amount computed from stored rules
 *  - Decision record persisted with session-bound approver identity
 */
@RunWith(AndroidJUnit4::class)
class RefundApprovalE2EIntegrationTest {

    private lateinit var db: FieldTripDatabase
    private lateinit var bookingRepo: BookingRepositoryImpl
    private lateinit var refundDecisionRepo: RefundDecisionRepositoryImpl
    private lateinit var refundRuleRepo: RefundRuleRepositoryImpl
    private lateinit var session: SessionManager
    private lateinit var useCase: ApproveRefundUseCase

    private val bookingId = "b1"

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FieldTripDatabase::class.java
        ).allowMainThreadQueries().build()

        bookingRepo = BookingRepositoryImpl(db.bookingOrderDao())
        refundDecisionRepo = RefundDecisionRepositoryImpl(db.refundDecisionDao())
        refundRuleRepo = RefundRuleRepositoryImpl(db, db.refundRuleDao())
        session = SessionManager()

        val logger: AuditLogger = mockk(relaxed = true)
        useCase = ApproveRefundUseCase(refundDecisionRepo, bookingRepo, logger, session, refundRuleRepo)

        val now = System.currentTimeMillis()
        val tripStart = Instant.now().plus(Duration.ofDays(5)).toEpochMilli()
        val tripEnd = tripStart + Duration.ofDays(1).toMillis()
        runBlocking {
            db.userDao().insert(
                UserEntity("agent-1", "agent", "Agent A", true, now, now)
            )
            db.bookingOrderDao().upsert(
                BookingOrderEntity(
                    id = bookingId, itineraryId = "i1", travelerId = "alice",
                    inventorySlotId = "s1", partySize = 2, state = "Booked",
                    createdAt = now, updatedAt = now,
                    confirmedAt = now, confirmedBy = "agent-1",
                    cancelledAt = null, cancelReason = null,
                    lastActivityAt = now,
                    tripStartAt = tripStart, tripEndAt = tripEnd,
                    paidTotalCents = 10000 // $100.00
                )
            )
        }
    }

    @After fun teardown() { db.close() }

    @Test
    fun `agent approves full refund for trip beyond 48h — decision persisted`() = runTest {
        session.set(SessionContext("agent-1", "Agent A", setOf(Role.Agent), "s1"))

        val result = useCase.execute(bookingId) as ApproveRefundUseCase.Result.Approved

        assertThat(result.decision.refundAmount).isEqualTo(BigDecimal("100.00"))
        assertThat(result.decision.approverUserId).isEqualTo("agent-1")
        assertThat(result.decision.approverName).isEqualTo("Agent A")
        assertThat(result.decision.ruleUsed).isEqualTo(RefundEngine.RULE_FULL)

        // Decision must be in DB
        val persisted = db.refundDecisionDao().getByBooking(bookingId)
        assertThat(persisted).hasSize(1)
        assertThat(persisted.first().approverUserId).isEqualTo("agent-1")
    }

    @Test
    fun `refund honors admin-edited rules via repository`() = runTest {
        session.set(SessionContext("admin-1", "Admin", setOf(Role.Administrator), "s2"))
        // Upsert an override rule giving 100% regardless of time
        refundRuleRepo.upsert(
            RefundRule(
                id = "always-full", code = "ADMIN_FULL",
                minHoursBeforeStartExclusive = -1,
                maxHoursBeforeStartInclusive = null,
                refundPercent = 100, description = "full always",
                active = true, updatedAt = Instant.now(), updatedBy = "admin-1"
            )
        )

        val result = useCase.execute(bookingId) as ApproveRefundUseCase.Result.Approved
        assertThat(result.decision.refundPercent).isEqualTo(100)
        assertThat(result.decision.ruleUsed).isEqualTo("ADMIN_FULL")
    }

    @Test
    fun `traveler cannot approve refund — use case rejects`() = runTest {
        session.set(SessionContext("traveler-1", "U", setOf(Role.Traveler), "s3"))
        try {
            useCase.execute(bookingId)
            assert(false) { "should throw UnauthorizedException" }
        } catch (_: SecurityException) {
            // expected
        }
    }
}
