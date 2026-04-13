package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.domain.model.BookingOrder
import com.fieldtripops.domain.model.BookingState
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.repository.InventoryRepository
import com.fieldtripops.security.auth.NotAuthenticatedException
import com.fieldtripops.security.auth.OwnershipViolationException
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/**
 * Pure-authorization tests: we verify the use case rejects the call BEFORE
 * touching the Room transaction. Atomicity of the transaction body is
 * exercised separately by instrumented integration tests like
 * `FileClaimAtomicityTest`.
 */
class CancelBookingOwnershipTest {

    private lateinit var bookingRepo: BookingRepository
    private lateinit var inventoryRepo: InventoryRepository
    private lateinit var auditLogger: AuditLogger
    private lateinit var session: SessionManager
    private lateinit var db: FieldTripDatabase
    private lateinit var useCase: CancelBookingUseCase

    @Before
    fun setup() {
        db = mockk(relaxed = true)
        bookingRepo = mockk(relaxed = true)
        inventoryRepo = mockk(relaxed = true)
        auditLogger = mockk(relaxed = true)
        session = SessionManager()
        useCase = CancelBookingUseCase(db, bookingRepo, inventoryRepo, auditLogger, session)
    }

    private fun bookingOwnedBy(userId: String): BookingOrder {
        val now = Instant.now()
        return BookingOrder(
            id = "b1", itineraryId = "i1", travelerId = userId,
            inventorySlotId = "s1", partySize = 2,
            state = BookingState.Booked,
            createdAt = now, updatedAt = now,
            confirmedAt = null, confirmedBy = null,
            cancelledAt = null, cancelReason = null,
            lastActivityAt = now,
            tripStartAt = now.plus(Duration.ofDays(7)),
            tripEndAt = now.plus(Duration.ofDays(8)),
            paidTotal = BigDecimal("100.00")
        )
    }

    @Test(expected = NotAuthenticatedException::class)
    fun `cancel without session is not authenticated`() = runTest {
        useCase.execute("b1", "test")
    }

    @Test(expected = OwnershipViolationException::class)
    fun `traveler cannot cancel another travelers booking`() = runTest {
        coEvery { bookingRepo.findById("b1") } returns bookingOwnedBy("alice")
        session.set(SessionContext("bob", "Bob", setOf(Role.Traveler), "s1"))
        useCase.execute("b1", "test")
    }
}
