package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "claim_tickets",
    indices = [
        Index(value = ["travelerId", "createdAt"]),
        Index(value = ["state", "updatedAt"]),
        Index(value = ["responsibility", "createdAt"]),
        Index(value = ["bookingOrderId"])
    ]
)
data class ClaimTicketEntity(
    @PrimaryKey val id: String,
    val travelerId: String,
    val bookingOrderId: String,
    val claimStyle: String,
    val classification: String,
    val responsibility: String,
    val description: String,
    val state: String,
    val createdAt: Long,
    val updatedAt: Long,
    val firstResponseAt: Long?,
    val resolvedAt: Long?,
    val closedAt: Long?,
    val lastTravelerActivityAt: Long,
    // Compensation calculation (nullable until investigation completes)
    val compensationAmountCents: Long?,
    val compensationCurrency: String?,
    val compensationBasis: String?, // Rule/source code used for calculation
    val compensationApproverId: String?,
    val compensationApproverName: String?,
    val compensationDecidedAt: Long?,
    val compensationNote: String?
)

@Entity(
    tableName = "ticket_status_history",
    indices = [Index(value = ["ticketId", "timestamp"])]
)
data class TicketStatusHistoryEntity(
    @PrimaryKey val id: String,
    val ticketId: String,
    val fromState: String?,
    val toState: String,
    val actor: String,
    val timestamp: Long,
    val reason: String?
)

@Entity(
    tableName = "investigation_notes",
    indices = [Index(value = ["ticketId", "createdAt"])]
)
data class InvestigationNoteEntity(
    @PrimaryKey val id: String,
    val ticketId: String,
    val authorUserId: String,
    val note: String,
    val createdAt: Long
)

@Entity(
    tableName = "appeal_records",
    indices = [Index(value = ["ticketId", "filedAt"])]
)
data class AppealRecordEntity(
    @PrimaryKey val id: String,
    val ticketId: String,
    val filedBy: String,
    val filedAt: Long,
    val reason: String,
    val resolvedAt: Long?,
    val resolution: String?
)
