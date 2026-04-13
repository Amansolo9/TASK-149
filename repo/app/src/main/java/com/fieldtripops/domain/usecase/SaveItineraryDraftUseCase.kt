package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.booking.ItineraryValidator
import com.fieldtripops.domain.model.ItineraryDraft
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.ItineraryRepository
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import java.time.Instant
import java.util.UUID

/**
 * Persists an itinerary draft for the authenticated traveler.
 * The travelerId on the draft is forced to match the session, preventing a
 * malicious caller from creating drafts attributed to another user.
 */
class SaveItineraryDraftUseCase(
    private val itineraryRepository: ItineraryRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) {

    sealed class Result {
        data class Saved(val draft: ItineraryDraft) : Result()
        data class Invalid(val errors: List<String>) : Result()
    }

    suspend fun execute(draft: ItineraryDraft): Result {
        val session = sessionManager.requireSession()

        val existing = if (draft.id.isNotBlank()) {
            itineraryRepository.findById(draft.id)
        } else null

        if (existing != null) {
            AccessControl.requireOwnerOrRole(
                session, existing.travelerId, "ItineraryDraft", existing.id,
                Role.Agent, Role.Administrator
            )
        }

        // Force travelerId to authenticated user unless an Agent/Admin acts on behalf.
        val effectiveTravelerId = if (
            session.hasAnyRole(Role.Agent, Role.Administrator) && draft.travelerId.isNotBlank()
        ) draft.travelerId else session.userId

        val candidate = draft.copy(
            id = draft.id.ifBlank { UUID.randomUUID().toString() },
            travelerId = effectiveTravelerId,
            updatedAt = Instant.now()
        )

        when (val v = ItineraryValidator.validate(candidate)) {
            is ItineraryValidator.Result.Invalid -> return Result.Invalid(v.errors)
            else -> {}
        }

        itineraryRepository.save(candidate)
        auditLogger.log(session.userId, AuditAction.ITINERARY_SAVED,
            "ItineraryDraft", candidate.id, "Saved by ${session.displayName}")
        return Result.Saved(candidate)
    }
}
