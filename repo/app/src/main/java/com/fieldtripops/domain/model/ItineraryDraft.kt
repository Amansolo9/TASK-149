package com.fieldtripops.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * A traveler's itinerary draft prior to booking submission.
 * Required fields per PRD §9.1: travelerInitials, partySize, startDate, endDate.
 */
data class ItineraryDraft(
    val id: String,
    val travelerId: String,
    val travelerInitials: String,
    val partySize: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val notes: String?,
    val itineraryType: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val submitted: Boolean
) {
    companion object {
        const val MAX_RANGE_DAYS = 365L
        const val MIN_PARTY_SIZE = 1
        const val MAX_PARTY_SIZE = 12
        const val MAX_NOTES_LENGTH = 2000
    }
}
