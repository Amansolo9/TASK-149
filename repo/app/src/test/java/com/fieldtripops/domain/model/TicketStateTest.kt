package com.fieldtripops.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TicketStateTest {

    @Test
    fun `valid submission flow`() {
        assertThat(TicketState.canTransition(TicketState.Draft, TicketState.Submitted)).isTrue()
        assertThat(TicketState.canTransition(TicketState.Submitted, TicketState.InReview)).isTrue()
        assertThat(TicketState.canTransition(TicketState.InReview, TicketState.Resolved)).isTrue()
        assertThat(TicketState.canTransition(TicketState.Resolved, TicketState.Closed)).isTrue()
    }

    @Test
    fun `appeal flow`() {
        assertThat(TicketState.canTransition(TicketState.Resolved, TicketState.Appealed)).isTrue()
        assertThat(TicketState.canTransition(TicketState.Rejected, TicketState.Appealed)).isTrue()
        assertThat(TicketState.canTransition(TicketState.Appealed, TicketState.InReview)).isTrue()
        assertThat(TicketState.canTransition(TicketState.Appealed, TicketState.Finalized)).isTrue()
    }

    @Test
    fun `auto-closed path`() {
        assertThat(TicketState.canTransition(TicketState.WaitingForTraveler, TicketState.AutoClosed)).isTrue()
        assertThat(TicketState.canTransition(TicketState.AutoClosed, TicketState.Closed)).isTrue()
    }

    @Test
    fun `illegal transitions blocked`() {
        assertThat(TicketState.canTransition(TicketState.Draft, TicketState.Resolved)).isFalse()
        assertThat(TicketState.canTransition(TicketState.Closed, TicketState.Appealed)).isFalse()
        assertThat(TicketState.canTransition(TicketState.Submitted, TicketState.Closed)).isFalse()
    }

    @Test
    fun `terminal states`() {
        assertThat(TicketState.isTerminal(TicketState.Closed)).isTrue()
        assertThat(TicketState.isTerminal(TicketState.Cancelled)).isTrue()
        assertThat(TicketState.isTerminal(TicketState.Finalized)).isTrue()
    }
}
