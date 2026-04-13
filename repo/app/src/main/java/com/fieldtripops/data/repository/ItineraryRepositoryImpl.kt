package com.fieldtripops.data.repository

import com.fieldtripops.data.dao.ItineraryDraftDao
import com.fieldtripops.data.entity.ItineraryDraftEntity
import com.fieldtripops.domain.model.ItineraryDraft
import com.fieldtripops.domain.repository.ItineraryRepository
import com.fieldtripops.security.SensitiveFieldCodec
import java.time.Instant
import java.time.LocalDate

/**
 * Persists itinerary drafts. The free-text `notes` field is encrypted at rest
 * via [sensitiveCodec] because it may contain traveler PII (medical needs,
 * accessibility info, contact details). Reads transparently decrypt.
 */
class ItineraryRepositoryImpl(
    private val dao: ItineraryDraftDao,
    private val sensitiveCodec: SensitiveFieldCodec
) : ItineraryRepository {

    override suspend fun save(draft: ItineraryDraft) {
        dao.upsert(draft.toEntity())
    }

    override suspend fun findById(id: String): ItineraryDraft? = dao.findById(id)?.toDomain()

    override suspend fun findByTraveler(travelerId: String): List<ItineraryDraft> =
        dao.findByTraveler(travelerId).map { it.toDomain() }

    override suspend fun markSubmitted(id: String) {
        dao.markSubmitted(id, Instant.now().toEpochMilli())
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    private fun ItineraryDraftEntity.toDomain() = ItineraryDraft(
        id = id,
        travelerId = travelerId,
        travelerInitials = travelerInitials,
        partySize = partySize,
        startDate = LocalDate.ofEpochDay(startDateEpochDay),
        endDate = LocalDate.ofEpochDay(endDateEpochDay),
        notes = sensitiveCodec.decrypt(notes),
        itineraryType = itineraryType,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        submitted = submitted
    )

    private fun ItineraryDraft.toEntity() = ItineraryDraftEntity(
        id = id,
        travelerId = travelerId,
        travelerInitials = travelerInitials,
        partySize = partySize,
        startDateEpochDay = startDate.toEpochDay(),
        endDateEpochDay = endDate.toEpochDay(),
        notes = sensitiveCodec.encrypt(notes),
        itineraryType = itineraryType,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        submitted = submitted
    )
}
