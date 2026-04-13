package com.fieldtripops.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BookingStateTest {

    @Test
    fun `allowed transitions from Draft`() {
        assertThat(BookingState.canTransition(BookingState.Draft, BookingState.PendingConfirmation)).isTrue()
        assertThat(BookingState.canTransition(BookingState.Draft, BookingState.Cancelled)).isTrue()
    }

    @Test
    fun `illegal transition Draft to Completed`() {
        assertThat(BookingState.canTransition(BookingState.Draft, BookingState.Completed)).isFalse()
    }

    @Test
    fun `illegal transition Pending to InService`() {
        assertThat(BookingState.canTransition(BookingState.PendingConfirmation, BookingState.InService)).isFalse()
    }

    @Test
    fun `illegal transition Completed to Booked`() {
        assertThat(BookingState.canTransition(BookingState.Completed, BookingState.Booked)).isFalse()
    }

    @Test
    fun `closed is terminal`() {
        assertThat(BookingState.isTerminal(BookingState.Closed)).isTrue()
        assertThat(BookingState.allowedNextStates(BookingState.Closed)).isEmpty()
    }

    @Test
    fun `pending to booked is allowed`() {
        assertThat(BookingState.canTransition(BookingState.PendingConfirmation, BookingState.Booked)).isTrue()
    }

    @Test
    fun `pending to closed via auto-close is allowed`() {
        assertThat(BookingState.canTransition(BookingState.PendingConfirmation, BookingState.Closed)).isTrue()
    }

    @Test
    fun `booked to reschedule pending is allowed`() {
        assertThat(BookingState.canTransition(BookingState.Booked, BookingState.ReschedulePending)).isTrue()
    }

    @Test
    fun `cancelled is terminal`() {
        assertThat(BookingState.isTerminal(BookingState.Cancelled)).isTrue()
        assertThat(BookingState.canTransition(BookingState.Cancelled, BookingState.Closed)).isFalse()
        assertThat(BookingState.canTransition(BookingState.Cancelled, BookingState.Booked)).isFalse()
    }

    @Test
    fun `only Cancelled and Closed are terminal states`() {
        assertThat(BookingState.isTerminal(BookingState.Cancelled)).isTrue()
        assertThat(BookingState.isTerminal(BookingState.Closed)).isTrue()
        // All other states have outgoing transitions
        assertThat(BookingState.isTerminal(BookingState.Draft)).isFalse()
        assertThat(BookingState.isTerminal(BookingState.PendingConfirmation)).isFalse()
        assertThat(BookingState.isTerminal(BookingState.Booked)).isFalse()
        assertThat(BookingState.isTerminal(BookingState.InService)).isFalse()
        assertThat(BookingState.isTerminal(BookingState.Completed)).isFalse()
    }

    @Test
    fun `full lifecycle path works`() {
        assertThat(BookingState.canTransition(BookingState.Draft, BookingState.PendingConfirmation)).isTrue()
        assertThat(BookingState.canTransition(BookingState.PendingConfirmation, BookingState.Booked)).isTrue()
        assertThat(BookingState.canTransition(BookingState.Booked, BookingState.InService)).isTrue()
        assertThat(BookingState.canTransition(BookingState.InService, BookingState.Completed)).isTrue()
        assertThat(BookingState.canTransition(BookingState.Completed, BookingState.Closed)).isTrue()
    }
}
