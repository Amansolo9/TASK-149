package com.fieldtripops.domain.booking

import com.fieldtripops.domain.model.ItineraryDraft
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Validates itinerary drafts per PRD §9.1 and §17.
 */
object ItineraryValidator {

    private val DATE_INPUT_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy")

    sealed class Result {
        object Valid : Result()
        data class Invalid(val errors: List<String>) : Result()
    }

    fun validate(draft: ItineraryDraft): Result {
        val errors = mutableListOf<String>()

        if (draft.travelerInitials.isBlank()) {
            errors += "Traveler initials are required"
        }

        if (draft.partySize < ItineraryDraft.MIN_PARTY_SIZE ||
            draft.partySize > ItineraryDraft.MAX_PARTY_SIZE
        ) {
            errors += "Party size must be between ${ItineraryDraft.MIN_PARTY_SIZE} and ${ItineraryDraft.MAX_PARTY_SIZE}"
        }

        if (draft.endDate.isBefore(draft.startDate)) {
            errors += "End date must be on or after start date"
        } else {
            val rangeDays = ChronoUnit.DAYS.between(draft.startDate, draft.endDate)
            if (rangeDays > ItineraryDraft.MAX_RANGE_DAYS) {
                errors += "Date range cannot exceed ${ItineraryDraft.MAX_RANGE_DAYS} days"
            }
        }

        if (draft.notes != null && draft.notes.length > ItineraryDraft.MAX_NOTES_LENGTH) {
            errors += "Notes exceed maximum length of ${ItineraryDraft.MAX_NOTES_LENGTH}"
        }

        return if (errors.isEmpty()) Result.Valid else Result.Invalid(errors)
    }

    /**
     * Parses MM/DD/YYYY input per PRD §9.1.
     */
    fun parseInputDate(input: String): LocalDate? {
        return try {
            LocalDate.parse(input.trim(), DATE_INPUT_FORMAT)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun formatDisplayDate(date: LocalDate): String = date.format(DATE_INPUT_FORMAT)
}
