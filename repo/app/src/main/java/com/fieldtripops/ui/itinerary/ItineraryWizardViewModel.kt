package com.fieldtripops.ui.itinerary

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtripops.domain.booking.ItineraryValidator
import com.fieldtripops.domain.model.InventorySlot
import com.fieldtripops.domain.model.ItineraryDraft
import com.fieldtripops.domain.repository.InventoryRepository
import com.fieldtripops.domain.usecase.SaveItineraryDraftUseCase
import com.fieldtripops.domain.usecase.SubmitBookingUseCase
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class ItineraryWizardViewModel(
    private val saveItineraryDraftUseCase: SaveItineraryDraftUseCase,
    private val submitBookingUseCase: SubmitBookingUseCase,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val _form = MutableLiveData(ItineraryFormState())
    val form: LiveData<ItineraryFormState> = _form

    private val _state = MutableLiveData<ItineraryWizardState>(ItineraryWizardState.Editing)
    val state: LiveData<ItineraryWizardState> = _state

    /** Slots matching the chosen itinerary type with seats >= partySize. */
    private val _availability = MutableLiveData<List<InventorySlot>>(emptyList())
    val availability: LiveData<List<InventorySlot>> = _availability

    fun initialize(travelerId: String) { /* retained for binary compat; ignored */ }

    fun updateForm(update: ItineraryFormState.() -> ItineraryFormState) {
        _form.value = (_form.value ?: ItineraryFormState()).update()
    }

    fun goToNext(): Boolean {
        val current = _form.value ?: return false
        val stepErrors = validateStep(current, current.step)
        if (stepErrors.isNotEmpty()) {
            _state.value = ItineraryWizardState.ValidationError(stepErrors)
            return false
        }
        _state.value = ItineraryWizardState.Editing
        if (current.step < current.totalSteps) {
            _form.value = current.copy(step = current.step + 1)
            if (_form.value?.step == current.totalSteps) loadAvailability()
            return true
        }
        return false
    }

    fun goBack(): Boolean {
        val current = _form.value ?: return false
        if (current.step > 1) {
            _form.value = current.copy(step = current.step - 1)
            _state.value = ItineraryWizardState.Editing
            return true
        }
        return false
    }

    /**
     * Loads inventory availability for the currently chosen itinerary type and
     * party size. Called when the wizard reaches the review step, and re-run
     * on-demand by the Fragment after submission failures.
     */
    fun loadAvailability() {
        val form = _form.value ?: return
        val party = form.partySize.toIntOrNull() ?: return
        viewModelScope.launch {
            _availability.value = inventoryRepository
                .findAvailableSlots(form.itineraryType.trim().ifBlank { "standard" }, party)
        }
    }

    /** Save as draft, without submitting for booking. */
    fun saveDraft() {
        val form = _form.value ?: return
        val draft = buildDraft(form) ?: return
        viewModelScope.launch {
            try {
                when (val r = saveItineraryDraftUseCase.execute(draft)) {
                    is SaveItineraryDraftUseCase.Result.Saved ->
                        _state.value = ItineraryWizardState.Saved(r.draft)
                    is SaveItineraryDraftUseCase.Result.Invalid ->
                        _state.value = ItineraryWizardState.ValidationError(r.errors)
                }
            } catch (e: SecurityException) {
                _state.value = ItineraryWizardState.SubmissionError("Not authorized: ${e.message}")
            }
        }
    }

    /**
     * Save the draft, then submit a booking against the chosen inventory slot.
     * The state machine lands on `PendingConfirmation` on success, or surfaces
     * a specific UI message for sold-out / duplicate paths.
     */
    fun submitBooking(inventorySlotId: String) {
        val form = _form.value ?: return
        val draft = buildDraft(form) ?: return
        viewModelScope.launch {
            try {
                val saveResult = saveItineraryDraftUseCase.execute(draft)
                val savedDraft = when (saveResult) {
                    is SaveItineraryDraftUseCase.Result.Saved -> saveResult.draft
                    is SaveItineraryDraftUseCase.Result.Invalid -> {
                        _state.value = ItineraryWizardState.ValidationError(saveResult.errors)
                        return@launch
                    }
                }

                val submit = submitBookingUseCase.execute(savedDraft.id, inventorySlotId)
                _state.value = when (submit) {
                    is SubmitBookingUseCase.Result.Submitted ->
                        ItineraryWizardState.BookingSubmitted(submit.order.id)
                    is SubmitBookingUseCase.Result.QuotaUnavailable ->
                        ItineraryWizardState.SubmissionError(
                            "Only ${submit.remaining} seat(s) remaining — needs ${submit.requested}. " +
                                "Choose a different date or reduce party size."
                        )
                    is SubmitBookingUseCase.Result.DuplicateSubmission ->
                        ItineraryWizardState.SubmissionError(
                            "A booking for this itinerary is already pending or active."
                        )
                    is SubmitBookingUseCase.Result.InventoryNotFound ->
                        ItineraryWizardState.SubmissionError("Selected slot is no longer available.")
                    is SubmitBookingUseCase.Result.ItineraryNotFound ->
                        ItineraryWizardState.SubmissionError("Draft missing — please retry.")
                    is SubmitBookingUseCase.Result.ValidationFailed ->
                        ItineraryWizardState.ValidationError(submit.errors)
                }
            } catch (e: SecurityException) {
                _state.value = ItineraryWizardState.SubmissionError("Not authorized: ${e.message}")
            }
        }
    }

    private fun buildDraft(form: ItineraryFormState): ItineraryDraft? {
        val start = ItineraryValidator.parseInputDate(form.startDateInput) ?: return null
        val end = ItineraryValidator.parseInputDate(form.endDateInput) ?: return null
        val party = form.partySize.toIntOrNull() ?: return null
        val now = Instant.now()
        // travelerId is forced from the authenticated session inside the use case.
        return ItineraryDraft(
            id = UUID.randomUUID().toString(),
            travelerId = "",
            travelerInitials = form.initials.trim().uppercase(),
            partySize = party,
            startDate = start,
            endDate = end,
            notes = form.notes.trim().takeIf { it.isNotBlank() },
            itineraryType = form.itineraryType.trim().ifBlank { "standard" },
            createdAt = now,
            updatedAt = now,
            submitted = false
        )
    }

    private fun validateStep(form: ItineraryFormState, step: Int): List<String> {
        val errors = mutableListOf<String>()
        when (step) {
            1 -> {
                if (form.initials.isBlank()) errors += "Traveler initials are required"
                val party = form.partySize.toIntOrNull()
                if (party == null || party < 1 || party > 12)
                    errors += "Party size must be between 1 and 12"
                if (form.itineraryType.isBlank()) errors += "Itinerary type is required"
            }
            2 -> {
                val start = ItineraryValidator.parseInputDate(form.startDateInput)
                val end = ItineraryValidator.parseInputDate(form.endDateInput)
                if (start == null) errors += "Start date must be in MM/DD/YYYY format"
                if (end == null) errors += "End date must be in MM/DD/YYYY format"
                if (start != null && end != null) {
                    if (end.isBefore(start)) errors += "End date must be on or after start date"
                    val days = java.time.temporal.ChronoUnit.DAYS.between(start, end)
                    if (days > 365) errors += "Date range cannot exceed 365 days"
                }
            }
            3 -> {
                if (form.notes.length > 2000) errors += "Notes exceed maximum length"
            }
        }
        return errors
    }
}
