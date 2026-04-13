package com.fieldtripops.domain.model

import java.math.BigDecimal
import java.time.Instant

data class ClaimTicket(
    val id: String,
    val travelerId: String,
    val bookingOrderId: String,
    val claimStyle: ClaimStyle,
    val classification: ClaimClassification,
    val responsibility: Responsibility,
    val description: String,
    val state: TicketState,
    val createdAt: Instant,
    val updatedAt: Instant,
    val firstResponseAt: Instant?,
    val resolvedAt: Instant?,
    val closedAt: Instant?,
    val lastTravelerActivityAt: Instant,
    val compensation: CompensationCalculation? = null
)

/**
 * Compensation calculation attached to a claim ticket. Persisted when a
 * reviewer/admin computes the amount owed to the traveler.
 */
data class CompensationCalculation(
    val amount: BigDecimal,
    val currency: String,
    /** Rule/source code used for calculation, e.g. "SERVICE_NOT_DELIVERED_FULL_REFUND". */
    val basis: String,
    val approverId: String,
    val approverName: String,
    val decidedAt: Instant,
    val note: String? = null
)

enum class ClaimStyle { REFUND_ONLY, SERVICE_NOT_DELIVERED, PARTIAL_DELIVERY }

enum class ClaimClassification {
    PROVIDER_NO_SHOW,
    CUSTOMER_LATE_ARRIVAL,
    SAFETY_CONCERN,
    PRICING_DISCREPANCY
}

enum class Responsibility { TRAVELER, AGENT, PROVIDER, UNKNOWN }

enum class TicketState {
    Draft, Submitted, InReview, WaitingForTraveler, Escalated,
    Resolved, Rejected, Appealed, AutoClosed, Closed, Cancelled, Finalized;

    companion object {
        private val TRANSITIONS: Map<TicketState, Set<TicketState>> = mapOf(
            Draft to setOf(Submitted, Cancelled),
            Submitted to setOf(InReview, Rejected),
            InReview to setOf(WaitingForTraveler, Resolved, Rejected, Escalated),
            WaitingForTraveler to setOf(InReview, AutoClosed),
            Escalated to setOf(Resolved, Rejected),
            Resolved to setOf(Appealed, Closed),
            Rejected to setOf(Appealed, Closed),
            Appealed to setOf(InReview, Finalized),
            AutoClosed to setOf(Closed),
            Closed to emptySet(),
            Cancelled to emptySet(),
            Finalized to emptySet()
        )

        fun canTransition(from: TicketState, to: TicketState): Boolean =
            TRANSITIONS[from]?.contains(to) ?: false

        fun allowedNextStates(from: TicketState): Set<TicketState> =
            TRANSITIONS[from] ?: emptySet()

        fun isTerminal(state: TicketState): Boolean =
            TRANSITIONS[state].isNullOrEmpty()
    }
}

data class TicketStatusHistory(
    val id: String,
    val ticketId: String,
    val fromState: TicketState?,
    val toState: TicketState,
    val actor: String,
    val timestamp: Instant,
    val reason: String?
)

data class InvestigationNote(
    val id: String,
    val ticketId: String,
    val authorUserId: String,
    val note: String,
    val createdAt: Instant
)

data class AppealRecord(
    val id: String,
    val ticketId: String,
    val filedBy: String,
    val filedAt: Instant,
    val reason: String,
    val resolvedAt: Instant?,
    val resolution: String?
)
