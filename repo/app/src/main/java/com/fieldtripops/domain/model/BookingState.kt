package com.fieldtripops.domain.model

enum class BookingState {
    Draft,
    PendingConfirmation,
    Booked,
    ReschedulePending,
    InService,
    Completed,
    Cancelled,
    Closed;

    companion object {
        /**
         * Defines allowed state transitions for the booking order lifecycle.
         * Per PRD §10.1:
         *   Draft -> PendingConfirmation -> Booked -> InService -> Completed -> Closed
         *   Cancelled and Closed are terminal states (no outgoing transitions).
         *
         * Auto-close of stale pending bookings transitions to `Closed` (not a
         * separate `AutoClosed` state) to strictly align with the prompt
         * requirement that only `Cancelled` and `Closed` are terminal.
         */
        private val ALLOWED_TRANSITIONS: Map<BookingState, Set<BookingState>> = mapOf(
            Draft to setOf(PendingConfirmation, Cancelled),
            PendingConfirmation to setOf(Booked, Closed, Cancelled),
            Booked to setOf(InService, Cancelled, ReschedulePending),
            ReschedulePending to setOf(Booked, Cancelled),
            InService to setOf(Completed),
            Completed to setOf(Closed),
            Cancelled to emptySet(),
            Closed to emptySet()
        )

        fun canTransition(from: BookingState, to: BookingState): Boolean {
            return ALLOWED_TRANSITIONS[from]?.contains(to) ?: false
        }

        fun allowedNextStates(from: BookingState): Set<BookingState> {
            return ALLOWED_TRANSITIONS[from] ?: emptySet()
        }

        fun isTerminal(state: BookingState): Boolean {
            return ALLOWED_TRANSITIONS[state].isNullOrEmpty()
        }
    }
}

class IllegalBookingTransitionException(
    val from: BookingState,
    val to: BookingState
) : IllegalStateException("Illegal booking transition: $from -> $to")
