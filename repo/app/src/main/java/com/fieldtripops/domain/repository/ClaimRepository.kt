package com.fieldtripops.domain.repository

import com.fieldtripops.domain.model.AppealRecord
import com.fieldtripops.domain.model.ClaimTicket
import com.fieldtripops.domain.model.CompensationCalculation
import com.fieldtripops.domain.model.InvestigationNote
import com.fieldtripops.domain.model.TicketState
import com.fieldtripops.domain.model.TicketStatusHistory
import java.time.Instant

interface ClaimRepository {
    suspend fun save(ticket: ClaimTicket)
    suspend fun findById(id: String): ClaimTicket?
    suspend fun findByTraveler(travelerId: String): List<ClaimTicket>
    suspend fun findByState(state: TicketState): List<ClaimTicket>
    suspend fun transition(
        ticketId: String, fromState: TicketState, toState: TicketState,
        actor: String, reason: String?, at: Instant
    )
    suspend fun recordTravelerActivity(ticketId: String, at: Instant)
    suspend fun findStaleWaiting(threshold: Instant): List<ClaimTicket>
    suspend fun getHistory(ticketId: String): List<TicketStatusHistory>

    suspend fun addNote(note: InvestigationNote)
    suspend fun getNotes(ticketId: String): List<InvestigationNote>

    suspend fun recordAppeal(appeal: AppealRecord)
    suspend fun getAppeals(ticketId: String): List<AppealRecord>

    /** Atomically stores compensation calculation and an investigation note describing it. */
    suspend fun setCompensation(
        ticketId: String,
        compensation: CompensationCalculation,
        investigationNote: InvestigationNote?
    )
}
