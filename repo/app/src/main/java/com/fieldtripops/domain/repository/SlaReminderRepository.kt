package com.fieldtripops.domain.repository

import com.fieldtripops.domain.model.SlaReminder
import com.fieldtripops.domain.model.SlaReminderKind

interface SlaReminderRepository {
    /** Returns true if a NEW reminder row was inserted; false if dedup suppressed. */
    suspend fun upsertIfAbsent(reminder: SlaReminder): Boolean
    suspend fun listOpen(): List<SlaReminder>
    suspend fun getByTicket(ticketId: String): List<SlaReminder>
    suspend fun findByTicketAndKind(ticketId: String, kind: SlaReminderKind): SlaReminder?
    suspend fun acknowledge(id: String)
    suspend fun purgeStalePending(currentVersion: Long)
}
