package com.fieldtripops.data.repository

import androidx.room.withTransaction
import com.fieldtripops.data.dao.AppealRecordDao
import com.fieldtripops.data.dao.ClaimTicketDao
import com.fieldtripops.data.dao.InvestigationNoteDao
import com.fieldtripops.data.dao.TicketStatusHistoryDao
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.entity.AppealRecordEntity
import com.fieldtripops.data.entity.ClaimTicketEntity
import com.fieldtripops.data.entity.InvestigationNoteEntity
import com.fieldtripops.data.entity.TicketStatusHistoryEntity
import com.fieldtripops.domain.model.AppealRecord
import com.fieldtripops.domain.model.ClaimClassification
import com.fieldtripops.domain.model.ClaimStyle
import com.fieldtripops.domain.model.ClaimTicket
import com.fieldtripops.domain.model.CompensationCalculation
import java.math.BigDecimal
import java.math.RoundingMode
import com.fieldtripops.domain.model.InvestigationNote
import com.fieldtripops.domain.model.Responsibility
import com.fieldtripops.domain.model.TicketState
import com.fieldtripops.domain.model.TicketStatusHistory
import com.fieldtripops.domain.repository.ClaimRepository
import com.fieldtripops.security.SensitiveFieldCodec
import java.time.Instant
import java.util.UUID

/**
 * Persists claim tickets. The free-text `description` and investigation `note`
 * fields are encrypted at rest because they often contain traveler PII and
 * dispute details. Reads transparently decrypt.
 */
class ClaimRepositoryImpl(
    private val database: FieldTripDatabase,
    private val ticketDao: ClaimTicketDao,
    private val historyDao: TicketStatusHistoryDao,
    private val noteDao: InvestigationNoteDao,
    private val appealDao: AppealRecordDao,
    private val sensitiveCodec: SensitiveFieldCodec
) : ClaimRepository {

    override suspend fun save(ticket: ClaimTicket) {
        database.withTransaction {
            val existing = ticketDao.findById(ticket.id)
            ticketDao.upsert(ticket.toEntity())
            if (existing == null) {
                historyDao.insert(
                    TicketStatusHistoryEntity(
                        id = UUID.randomUUID().toString(),
                        ticketId = ticket.id,
                        fromState = null,
                        toState = ticket.state.name,
                        actor = ticket.travelerId,
                        timestamp = ticket.createdAt.toEpochMilli(),
                        reason = "Initial state"
                    )
                )
            }
        }
    }

    override suspend fun findById(id: String): ClaimTicket? =
        ticketDao.findById(id)?.toDomain()

    override suspend fun findByTraveler(travelerId: String): List<ClaimTicket> =
        ticketDao.findByTraveler(travelerId).map { it.toDomain() }

    override suspend fun findByState(state: TicketState): List<ClaimTicket> =
        ticketDao.findByState(state.name).map { it.toDomain() }

    override suspend fun transition(
        ticketId: String, fromState: TicketState, toState: TicketState,
        actor: String, reason: String?, at: Instant
    ) {
        database.withTransaction {
            ticketDao.updateState(ticketId, toState.name, at.toEpochMilli())
            historyDao.insert(
                TicketStatusHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    ticketId = ticketId,
                    fromState = fromState.name,
                    toState = toState.name,
                    actor = actor,
                    timestamp = at.toEpochMilli(),
                    reason = reason
                )
            )
        }
    }

    override suspend fun recordTravelerActivity(ticketId: String, at: Instant) {
        ticketDao.updateTravelerActivity(ticketId, at.toEpochMilli())
    }

    override suspend fun findStaleWaiting(threshold: Instant): List<ClaimTicket> =
        ticketDao.findStaleWaiting(threshold.toEpochMilli()).map { it.toDomain() }

    override suspend fun getHistory(ticketId: String): List<TicketStatusHistory> =
        historyDao.getByTicket(ticketId).map { it.toDomain() }

    override suspend fun addNote(note: InvestigationNote) {
        noteDao.insert(note.toEntity())
    }

    override suspend fun getNotes(ticketId: String): List<InvestigationNote> =
        noteDao.getByTicket(ticketId).map { it.toDomain() }

    override suspend fun recordAppeal(appeal: AppealRecord) {
        appealDao.upsert(appeal.toEntity())
    }

    override suspend fun getAppeals(ticketId: String): List<AppealRecord> =
        appealDao.getByTicket(ticketId).map { it.toDomain() }

    override suspend fun setCompensation(
        ticketId: String,
        compensation: com.fieldtripops.domain.model.CompensationCalculation,
        investigationNote: InvestigationNote?
    ) {
        val amountCents = compensation.amount
            .setScale(2, RoundingMode.HALF_EVEN).movePointRight(2).toLong()
        database.withTransaction {
            ticketDao.updateCompensation(
                id = ticketId,
                amountCents = amountCents,
                currency = compensation.currency,
                basis = compensation.basis,
                approverId = compensation.approverId,
                approverName = compensation.approverName,
                decidedAt = compensation.decidedAt.toEpochMilli(),
                note = compensation.note,
                updatedAt = Instant.now().toEpochMilli()
            )
            if (investigationNote != null) {
                noteDao.insert(investigationNote.toEntity())
            }
        }
    }

    // Mappers
    private fun ClaimTicketEntity.toDomain() = ClaimTicket(
        id = id, travelerId = travelerId, bookingOrderId = bookingOrderId,
        claimStyle = ClaimStyle.valueOf(claimStyle),
        classification = ClaimClassification.valueOf(classification),
        responsibility = Responsibility.valueOf(responsibility),
        description = sensitiveCodec.decrypt(description) ?: "",
        state = TicketState.valueOf(state),
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        firstResponseAt = firstResponseAt?.let { Instant.ofEpochMilli(it) },
        resolvedAt = resolvedAt?.let { Instant.ofEpochMilli(it) },
        closedAt = closedAt?.let { Instant.ofEpochMilli(it) },
        lastTravelerActivityAt = Instant.ofEpochMilli(lastTravelerActivityAt),
        compensation = compensationAmountCents?.let { cents ->
            CompensationCalculation(
                amount = BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_EVEN),
                currency = compensationCurrency ?: "USD",
                basis = compensationBasis ?: "",
                approverId = compensationApproverId ?: "",
                approverName = compensationApproverName ?: "",
                decidedAt = compensationDecidedAt?.let { Instant.ofEpochMilli(it) } ?: Instant.EPOCH,
                note = compensationNote
            )
        }
    )

    private fun ClaimTicket.toEntity() = ClaimTicketEntity(
        id = id, travelerId = travelerId, bookingOrderId = bookingOrderId,
        claimStyle = claimStyle.name, classification = classification.name,
        responsibility = responsibility.name,
        description = sensitiveCodec.encrypt(description) ?: "",
        state = state.name,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        firstResponseAt = firstResponseAt?.toEpochMilli(),
        resolvedAt = resolvedAt?.toEpochMilli(),
        closedAt = closedAt?.toEpochMilli(),
        lastTravelerActivityAt = lastTravelerActivityAt.toEpochMilli(),
        compensationAmountCents = compensation?.let {
            it.amount.setScale(2, RoundingMode.HALF_EVEN).movePointRight(2).toLong()
        },
        compensationCurrency = compensation?.currency,
        compensationBasis = compensation?.basis,
        compensationApproverId = compensation?.approverId,
        compensationApproverName = compensation?.approverName,
        compensationDecidedAt = compensation?.decidedAt?.toEpochMilli(),
        compensationNote = compensation?.note
    )

    private fun TicketStatusHistoryEntity.toDomain() = TicketStatusHistory(
        id = id, ticketId = ticketId,
        fromState = fromState?.let { TicketState.valueOf(it) },
        toState = TicketState.valueOf(toState),
        actor = actor, timestamp = Instant.ofEpochMilli(timestamp), reason = reason
    )

    private fun InvestigationNoteEntity.toDomain() = InvestigationNote(
        id = id, ticketId = ticketId, authorUserId = authorUserId,
        note = sensitiveCodec.decrypt(note) ?: "",
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun InvestigationNote.toEntity() = InvestigationNoteEntity(
        id = id, ticketId = ticketId, authorUserId = authorUserId,
        note = sensitiveCodec.encrypt(note) ?: "",
        createdAt = createdAt.toEpochMilli()
    )

    private fun AppealRecordEntity.toDomain() = AppealRecord(
        id = id, ticketId = ticketId, filedBy = filedBy,
        filedAt = Instant.ofEpochMilli(filedAt),
        reason = reason,
        resolvedAt = resolvedAt?.let { Instant.ofEpochMilli(it) },
        resolution = resolution
    )

    private fun AppealRecord.toEntity() = AppealRecordEntity(
        id = id, ticketId = ticketId, filedBy = filedBy,
        filedAt = filedAt.toEpochMilli(), reason = reason,
        resolvedAt = resolvedAt?.toEpochMilli(), resolution = resolution
    )
}
