package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fieldtripops.data.entity.AppealRecordEntity
import com.fieldtripops.data.entity.ClaimTicketEntity
import com.fieldtripops.data.entity.InvestigationNoteEntity
import com.fieldtripops.data.entity.TicketStatusHistoryEntity

@Dao
interface ClaimTicketDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ticket: ClaimTicketEntity)

    @Query("SELECT * FROM claim_tickets WHERE id = :id")
    suspend fun findById(id: String): ClaimTicketEntity?

    @Query("SELECT * FROM claim_tickets WHERE travelerId = :userId ORDER BY createdAt DESC")
    suspend fun findByTraveler(userId: String): List<ClaimTicketEntity>

    @Query("SELECT * FROM claim_tickets WHERE state = :state ORDER BY updatedAt DESC")
    suspend fun findByState(state: String): List<ClaimTicketEntity>

    @Query(
        """UPDATE claim_tickets SET state = :newState, updatedAt = :at,
           firstResponseAt = CASE WHEN firstResponseAt IS NULL AND :newState != 'Submitted'
                                  THEN :at ELSE firstResponseAt END,
           resolvedAt = CASE WHEN :newState = 'Resolved' THEN :at ELSE resolvedAt END,
           closedAt = CASE WHEN :newState IN ('Closed', 'AutoClosed', 'Finalized', 'Cancelled')
                           THEN :at ELSE closedAt END
           WHERE id = :id"""
    )
    suspend fun updateState(id: String, newState: String, at: Long)

    @Query("UPDATE claim_tickets SET lastTravelerActivityAt = :at WHERE id = :id")
    suspend fun updateTravelerActivity(id: String, at: Long)

    @Query(
        """UPDATE claim_tickets SET
            compensationAmountCents = :amountCents,
            compensationCurrency = :currency,
            compensationBasis = :basis,
            compensationApproverId = :approverId,
            compensationApproverName = :approverName,
            compensationDecidedAt = :decidedAt,
            compensationNote = :note,
            updatedAt = :updatedAt
           WHERE id = :id"""
    )
    suspend fun updateCompensation(
        id: String,
        amountCents: Long,
        currency: String,
        basis: String,
        approverId: String,
        approverName: String,
        decidedAt: Long,
        note: String?,
        updatedAt: Long
    )

    @Query(
        """SELECT * FROM claim_tickets
           WHERE state IN ('Submitted','InReview','Escalated','WaitingForTraveler')
             AND firstResponseAt IS NULL"""
    )
    suspend fun findAwaitingFirstResponse(): List<ClaimTicketEntity>

    @Query(
        """SELECT * FROM claim_tickets
           WHERE state IN ('Submitted','InReview','Escalated','WaitingForTraveler')
             AND resolvedAt IS NULL AND closedAt IS NULL"""
    )
    suspend fun findAwaitingResolution(): List<ClaimTicketEntity>

    @Query(
        """SELECT * FROM claim_tickets
           WHERE state = 'WaitingForTraveler' AND lastTravelerActivityAt < :threshold"""
    )
    suspend fun findStaleWaiting(threshold: Long): List<ClaimTicketEntity>
}

@Dao
interface TicketStatusHistoryDao {
    @Insert
    suspend fun insert(entry: TicketStatusHistoryEntity)

    @Query("SELECT * FROM ticket_status_history WHERE ticketId = :id ORDER BY timestamp ASC")
    suspend fun getByTicket(id: String): List<TicketStatusHistoryEntity>
}

@Dao
interface InvestigationNoteDao {
    @Insert
    suspend fun insert(note: InvestigationNoteEntity)

    @Query("SELECT * FROM investigation_notes WHERE ticketId = :id ORDER BY createdAt ASC")
    suspend fun getByTicket(id: String): List<InvestigationNoteEntity>
}

@Dao
interface AppealRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: AppealRecordEntity)

    @Query("SELECT * FROM appeal_records WHERE ticketId = :id ORDER BY filedAt DESC")
    suspend fun getByTicket(id: String): List<AppealRecordEntity>

    @Query("SELECT * FROM appeal_records WHERE id = :id")
    suspend fun findById(id: String): AppealRecordEntity?
}
