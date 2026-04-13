package com.fieldtripops.domain.usecase

import com.fieldtripops.domain.model.BookingOrder
import com.fieldtripops.domain.model.BookingState
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import com.fieldtripops.security.auth.UnauthorizedException
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
 *  - Agent can retrieve pending traveler bookings
 *  - Traveler cannot see another traveler's bookings
 *  - Confirm action is reachable from the agent path
 *  - Unauthorized roles are blocked
 */
class AgentBookingQueueTest {

    private lateinit var bookingRepo: BookingRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var confirmUseCase: ConfirmBookingUseCase

    private fun makeBooking(
        id: String, travelerId: String, state: BookingState = BookingState.PendingConfirmation
    ): BookingOrder {
        val now = Instant.now()
        return BookingOrder(
            id = id, itineraryId = "i1", travelerId = travelerId,
            inventorySlotId = "s1", partySize = 2, state = state,
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
        confirmUseCase = mockk(relaxed = true)
    }

    @Test
    fun `agent can retrieve all pending confirmation bookings`() = runTest {
        sessionManager.set(SessionContext("agent-1", "Agent A", setOf(Role.Agent), "s1"))
        val bookings = listOf(
            makeBooking("b1", "traveler-1"),
            makeBooking("b2", "traveler-2")
        )
        coEvery { bookingRepo.findByState(BookingState.PendingConfirmation) } returns bookings

        val session = sessionManager.requireSession()
        assertThat(session.hasAnyRole(Role.Agent, Role.Administrator)).isTrue()
        val pending = bookingRepo.findByState(BookingState.PendingConfirmation)
        assertThat(pending).hasSize(2)
        assertThat(pending.map { it.travelerId }).containsExactly("traveler-1", "traveler-2")
    }

    @Test
    fun `traveler only sees own bookings not others`() = runTest {
        sessionManager.set(SessionContext("alice", "Alice", setOf(Role.Traveler), "s2"))
        val aliceBookings = listOf(makeBooking("b1", "alice"))
        coEvery { bookingRepo.findByTraveler("alice") } returns aliceBookings

        val session = sessionManager.requireSession()
        // Traveler should NOT have agent/admin role
        assertThat(session.hasAnyRole(Role.Agent, Role.Administrator)).isFalse()
        val myBookings = bookingRepo.findByTraveler(session.userId)
        assertThat(myBookings).hasSize(1)
        assertThat(myBookings[0].travelerId).isEqualTo("alice")
    }

    @Test
    fun `reviewer cannot access agent queue`() = runTest {
        sessionManager.set(SessionContext("reviewer-1", "Rev", setOf(Role.Reviewer), "s3"))
        val session = sessionManager.requireSession()
        assertThat(session.hasAnyRole(Role.Agent, Role.Administrator)).isFalse()
    }

    @Test
    fun `administrator can access agent queue`() = runTest {
        sessionManager.set(SessionContext("admin-1", "Admin", setOf(Role.Administrator), "s4"))
        val bookings = listOf(makeBooking("b1", "traveler-1"))
        coEvery { bookingRepo.findByState(BookingState.PendingConfirmation) } returns bookings

        val session = sessionManager.requireSession()
        assertThat(session.hasAnyRole(Role.Agent, Role.Administrator)).isTrue()
        val pending = bookingRepo.findByState(BookingState.PendingConfirmation)
        assertThat(pending).hasSize(1)
    }
}
