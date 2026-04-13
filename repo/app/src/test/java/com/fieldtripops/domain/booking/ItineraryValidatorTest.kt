package com.fieldtripops.domain.booking

import com.fieldtripops.domain.model.ItineraryDraft
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ItineraryValidatorTest {

    private fun draft(
        partySize: Int = 2,
        start: LocalDate = LocalDate.of(2026, 5, 1),
        end: LocalDate = LocalDate.of(2026, 5, 5),
        initials: String = "JD",
        notes: String? = null
    ) = ItineraryDraft(
        id = "d1",
        travelerId = "u1",
        travelerInitials = initials,
        partySize = partySize,
        startDate = start,
        endDate = end,
        notes = notes,
        itineraryType = "standard",
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        submitted = false
    )

    @Test
    fun `valid itinerary passes`() {
        assertThat(ItineraryValidator.validate(draft()))
            .isEqualTo(ItineraryValidator.Result.Valid)
    }

    @Test
    fun `blank initials fails`() {
        val r = ItineraryValidator.validate(draft(initials = ""))
        assertThat(r).isInstanceOf(ItineraryValidator.Result.Invalid::class.java)
    }

    @Test
    fun `party size 0 fails`() {
        val r = ItineraryValidator.validate(draft(partySize = 0))
                as ItineraryValidator.Result.Invalid
        assertThat(r.errors.first()).contains("Party size")
    }

    @Test
    fun `party size 13 fails`() {
        val r = ItineraryValidator.validate(draft(partySize = 13))
        assertThat(r).isInstanceOf(ItineraryValidator.Result.Invalid::class.java)
    }

    @Test
    fun `party size boundaries 1 and 12 pass`() {
        assertThat(ItineraryValidator.validate(draft(partySize = 1)))
            .isEqualTo(ItineraryValidator.Result.Valid)
        assertThat(ItineraryValidator.validate(draft(partySize = 12)))
            .isEqualTo(ItineraryValidator.Result.Valid)
    }

    @Test
    fun `end before start fails`() {
        val r = ItineraryValidator.validate(
            draft(start = LocalDate.of(2026, 5, 5), end = LocalDate.of(2026, 5, 1))
        )
        assertThat(r).isInstanceOf(ItineraryValidator.Result.Invalid::class.java)
    }

    @Test
    fun `range exceeding 365 days fails`() {
        val r = ItineraryValidator.validate(
            draft(start = LocalDate.of(2026, 1, 1), end = LocalDate.of(2027, 1, 5))
        )
        assertThat(r).isInstanceOf(ItineraryValidator.Result.Invalid::class.java)
    }

    @Test
    fun `exactly 365 days passes`() {
        assertThat(
            ItineraryValidator.validate(
                draft(start = LocalDate.of(2026, 1, 1), end = LocalDate.of(2027, 1, 1))
            )
        ).isEqualTo(ItineraryValidator.Result.Valid)
    }

    @Test
    fun `parse input date`() {
        assertThat(ItineraryValidator.parseInputDate("05/01/2026"))
            .isEqualTo(LocalDate.of(2026, 5, 1))
    }

    @Test
    fun `parse invalid input returns null`() {
        assertThat(ItineraryValidator.parseInputDate("2026-05-01")).isNull()
        assertThat(ItineraryValidator.parseInputDate("not-a-date")).isNull()
    }
}
