package com.fieldtripops.domain.usecase

import com.fieldtripops.domain.model.BookingOrder
import com.fieldtripops.domain.model.BookingState
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.OwnershipViolationException
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/**
 * Tests proving:
 *  - Unauthorized booking ID access is rejected
 *  - Traveler cannot load someone else's booking in confirm flow
 *  - Agent can load a valid pending booking through the proper path
 */
class BookingConfirmAuthorizationTest {

    private lateinit var bookingRepo: BookingRepository
    private lateinit var sessionManager: SessionManager

    private fun makeBooking(id: String, travelerId: String): BookingOrder {
        val now = Instant.now()
        return BookingOrder(
            id = id, itineraryId = "i1", travelerId = travelerId,
            inventorySlotId = "s1", partySize = 2,
            state = BookingState.PendingConfirmation,
            createdAt = now, updatedAt = now,
            confirmedAt = null, confirmedBy = null,
            cancelledAt = null, cancelReason = null,
            lastActivityAt = now,
            tripStartAt = now.plus(Duration.ofDays(7)),
            tripEndAt = now.plus(Duration.ofDays(8)),
            paidTotal = BigDecimal.ZERO
        )
    }

    @Before
    fun setup() {
        bookingRepo = mockk(relaxed = true)
        sessionManager = SessionManager()
    }

    @Test(expected = OwnershipViolationException::class)
    fun `traveler cannot load another travelers booking`() = runTest {
        val order = makeBooking("b1", "alice")
        coEvery { bookingRepo.findById("b1") } returns order
        sessionManager.set(SessionContext("bob", "Bob", setOf(Role.Traveler), "s1"))

        val session = sessionManager.requireSession()
        AccessControl.requireOwnerOrRole(
            session, order.travelerId, "BookingOrder", "b1",
            Role.Agent, Role.Administrator
        )
    }

    @Test
    fun `agent can load any travelers pending booking`() = runTest {
        val order = makeBooking("b1", "alice")
        coEvery { bookingRepo.findById("b1") } returns order
        sessionManager.set(SessionContext("agent-1", "Agent A", setOf(Role.Agent), "s2"))

        val session = sessionManager.requireSession()
        // This should not throw
        AccessControl.requireOwnerOrRole(
            session, order.travelerId, "BookingOrder", "b1",
            Role.Agent, Role.Administrator
        )
    }

    @Test
    fun `traveler can load their own booking`() = runTest {
        val order = makeBooking("b1", "alice")
        coEvery { bookingRepo.findById("b1") } returns order
        sessionManager.set(SessionContext("alice", "Alice", setOf(Role.Traveler), "s3"))

        val session = sessionManager.requireSession()
        // This should not throw — Alice is the owner
        AccessControl.requireOwnerOrRole(
            session, order.travelerId, "BookingOrder", "b1",
            Role.Agent, Role.Administrator
        )
    }

    @Test(expected = OwnershipViolationException::class)
    fun `reviewer cannot load a booking in confirm flow`() = runTest {
        val order = makeBooking("b1", "alice")
        coEvery { bookingRepo.findById("b1") } returns order
        sessionManager.set(SessionContext("reviewer-1", "Rev", setOf(Role.Reviewer), "s4"))

        val session = sessionManager.requireSession()
        AccessControl.requireOwnerOrRole(
            session, order.travelerId, "BookingOrder", "b1",
            Role.Agent, Role.Administrator
        )
    }
}
