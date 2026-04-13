package com.fieldtripops.data.repository

import com.fieldtripops.data.dao.SlaReminderDao
import com.fieldtripops.data.entity.SlaReminderEntity
import com.fieldtripops.domain.model.SlaReminder
import com.fieldtripops.domain.model.SlaReminderKind
import com.fieldtripops.domain.repository.SlaReminderRepository
import java.time.Instant

class SlaReminderRepositoryImpl(
    private val dao: SlaReminderDao
) : SlaReminderRepository {

    override suspend fun upsertIfAbsent(reminder: SlaReminder): Boolean {
        val rowId = dao.insertIfAbsent(reminder.toEntity())
        return rowId >= 0L
    }

    override suspend fun listOpen(): List<SlaReminder> =
        dao.listOpen().map { it.toDomain() }

    override suspend fun getByTicket(ticketId: String): List<SlaReminder> =
        dao.getByTicket(ticketId).map { it.toDomain() }

    override suspend fun findByTicketAndKind(
        ticketId: String, kind: SlaReminderKind
    ): SlaReminder? = dao.findByTicketAndKind(ticketId, kind.name)?.toDomain()

    override suspend fun acknowledge(id: String) = dao.acknowledge(id)

    override suspend fun purgeStalePending(currentVersion: Long) =
        dao.purgeStalePending(currentVersion)

    private fun SlaReminderEntity.toDomain() = SlaReminder(
        id = id, ticketId = ticketId,
        kind = SlaReminderKind.valueOf(kind),
        dueAt = Instant.ofEpochMilli(dueAt),
        generatedAt = Instant.ofEpochMilli(generatedAt),
        slaConfigVersion = slaConfigVersion,
        acknowledged = acknowledged,
        message = message
    )

    private fun SlaReminder.toEntity() = SlaReminderEntity(
        id = id, ticketId = ticketId, kind = kind.name,
        dueAt = dueAt.toEpochMilli(),
        generatedAt = generatedAt.toEpochMilli(),
        slaConfigVersion = slaConfigVersion,
        acknowledged = acknowledged,
        message = message
    )
}
