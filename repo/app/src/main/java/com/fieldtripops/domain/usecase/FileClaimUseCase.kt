package com.fieldtripops.domain.usecase

import androidx.room.withTransaction
import com.fieldtripops.attachment.PendingAttachment
import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.domain.booking.ClaimValidator
import com.fieldtripops.domain.model.AttachmentRef
import com.fieldtripops.domain.model.ClaimClassification
import com.fieldtripops.domain.model.ClaimStyle
import com.fieldtripops.domain.model.ClaimTicket
import com.fieldtripops.domain.model.Responsibility
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.model.TicketState
import com.fieldtripops.domain.repository.AttachmentRepository
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.repository.ClaimRepository
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import java.time.Instant
import java.util.UUID

/**
 * Files a claim atomically with at least one valid proof attachment.
 *
 * Authorization & ownership:
 *  - travelerId is taken from session (callers cannot file on behalf of others
 *    unless they are Agent/Admin).
 *  - the bookingOrderId must reference a booking owned by the acting traveler
 *    (Agent/Admin override allowed).
 *
 * Atomicity guarantee:
 *  - Validates evidence (≥1 proof, allowed mime, ≤10MB) BEFORE opening tx.
 *  - Inside a single Room transaction we insert the claim row, status history,
 *    and stage attachment ref rows.
 *  - Disk byte writes are deferred until the transaction commits successfully;
 *    if the transaction throws, NO ref rows persist and NO disk artifacts exist.
 *  - If the post-commit byte flush fails, we rethrow so the caller sees the
 *    failure; the ref rows are removed in a compensating delete to preserve
 *    the invariant "ref exists ⟹ bytes exist".
 */
class FileClaimUseCase(
    private val database: FieldTripDatabase,
    private val claimRepository: ClaimRepository,
    private val bookingRepository: BookingRepository,
    private val attachmentRepository: AttachmentRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) {

    sealed class Result {
        data class Filed(val ticket: ClaimTicket, val attachments: List<AttachmentRef>) : Result()
        object BookingNotFound : Result()
        data class ValidationFailed(val errors: List<String>) : Result()
    }

    /**
     * Claim eligibility uses the booking's persisted `tripEndAt` (audit finding
     * #7) rather than anything callers can influence. Any caller-supplied
     * `tripEnd` parameter has been removed; updating booking.updatedAt for
     * unrelated reasons no longer changes claim eligibility.
     */
    suspend fun execute(
        bookingOrderId: String,
        style: ClaimStyle,
        classification: ClaimClassification,
        responsibility: Responsibility,
        description: String,
        evidence: List<PendingAttachment>
    ): Result {
        val session = sessionManager.requireSession()
        val booking = bookingRepository.findById(bookingOrderId) ?: return Result.BookingNotFound
        val tripEnd = booking.tripEndAt

        // Object-level: claim must target a booking the actor owns, unless staff.
        AccessControl.requireOwnerOrRole(
            session, booking.travelerId, "BookingOrder", bookingOrderId,
            Role.Agent, Role.Reviewer, Role.Administrator
        )

        val now = Instant.now()
        // Project pending attachments to AttachmentRef for the validator (size + mime checks)
        val previewRefs = evidence.map {
            AttachmentRef(
                id = it.id, ownerEntityType = "ClaimTicket", ownerEntityId = "preview",
                fileName = it.fileName, mimeType = it.mimeType,
                storagePath = "", sizeBytes = it.sizeBytes, createdAt = now
            )
        }
        when (val v = ClaimValidator.validate(tripEnd, now, previewRefs, description)) {
            is ClaimValidator.Result.Invalid -> return Result.ValidationFailed(v.errors)
            else -> {}
        }

        val ticket = ClaimTicket(
            id = UUID.randomUUID().toString(),
            travelerId = booking.travelerId,            // bound to booking, not caller
            bookingOrderId = bookingOrderId,
            claimStyle = style,
            classification = classification,
            responsibility = responsibility,
            description = description,                  // ClaimRepositoryImpl encrypts on write
            state = TicketState.Submitted,
            createdAt = now, updatedAt = now,
            firstResponseAt = null, resolvedAt = null, closedAt = null,
            lastTravelerActivityAt = now
        )

        // Atomic DB phase: ticket + initial history + staged ref rows
        val stagedRefs = database.withTransaction {
            claimRepository.save(ticket)
            attachmentRepository.stageInTransaction(
                ownerEntityType = "ClaimTicket",
                ownerEntityId = ticket.id,
                pending = evidence
            )
        }

        // Post-commit phase: write bytes. On failure, compensate by deleting ref rows
        // so we never leak rows without backing files.
        try {
            attachmentRepository.commitPayloads(stagedRefs, evidence)
        } catch (t: Throwable) {
            for (r in stagedRefs) attachmentRepository.delete(r.id)
            throw t
        }

        auditLogger.log(session.userId, AuditAction.CLAIM_FILED, "ClaimTicket", ticket.id,
            "Style=${style.name}, class=${classification.name}, resp=${responsibility.name}, " +
                "attachments=${stagedRefs.size}, booking=$bookingOrderId, by=${session.displayName}")
        for (r in stagedRefs) {
            auditLogger.log(session.userId, AuditAction.CLAIM_ATTACHMENT_ADDED,
                "AttachmentRef", r.id,
                "${r.fileName} (${r.mimeType}, ${r.sizeBytes}B)")
        }

        return Result.Filed(ticket, stagedRefs)
    }
}
