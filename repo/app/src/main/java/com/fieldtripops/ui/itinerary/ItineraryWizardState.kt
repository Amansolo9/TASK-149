package com.fieldtripops.ui.itinerary

import com.fieldtripops.domain.model.ItineraryDraft

sealed class ItineraryWizardState {
    object Editing : ItineraryWizardState()
    data class ValidationError(val errors: List<String>) : ItineraryWizardState()
    data class Saved(val draft: ItineraryDraft) : ItineraryWizardState()
    data class BookingSubmitted(val orderId: String) : ItineraryWizardState()
    data class SubmissionError(val message: String) : ItineraryWizardState()
}

data class ItineraryFormState(
    val step: Int = 1,
    val totalSteps: Int = 3,
    val initials: String = "",
    val partySize: String = "",
    val itineraryType: String = "standard",
    val startDateInput: String = "",
    val endDateInput: String = "",
    val notes: String = ""
)
