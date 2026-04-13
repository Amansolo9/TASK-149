package com.fieldtripops.ui.claims

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtripops.attachment.PendingAttachment
import com.fieldtripops.domain.model.ClaimClassification
import com.fieldtripops.domain.model.ClaimStyle
import com.fieldtripops.domain.model.Responsibility
import com.fieldtripops.domain.usecase.FileClaimUseCase
import kotlinx.coroutines.launch

class FileClaimViewModel(
    private val fileClaimUseCase: FileClaimUseCase
) : ViewModel() {

    sealed class State {
        object Idle : State()
        object Submitting : State()
        object Submitted : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableLiveData<State>(State.Idle)
    val state: LiveData<State> = _state

    private val pending = mutableListOf<PendingAttachment>()
    val attachmentCount: Int get() = pending.size

    fun stageAttachment(att: PendingAttachment) { pending += att }
    fun clearAttachments() { pending.clear() }

    fun submit(
        bookingOrderId: String,
        style: ClaimStyle,
        classification: ClaimClassification,
        description: String
    ) {
        _state.value = State.Submitting
        viewModelScope.launch {
            try {
                val responsibility = when (classification) {
                    ClaimClassification.PROVIDER_NO_SHOW -> Responsibility.PROVIDER
                    ClaimClassification.CUSTOMER_LATE_ARRIVAL -> Responsibility.TRAVELER
                    ClaimClassification.SAFETY_CONCERN -> Responsibility.UNKNOWN
                    ClaimClassification.PRICING_DISCREPANCY -> Responsibility.AGENT
                }
                val r = fileClaimUseCase.execute(
                    bookingOrderId = bookingOrderId,
                    style = style,
                    classification = classification,
                    responsibility = responsibility,
                    description = description,
                    evidence = pending.toList()
                )
                _state.value = when (r) {
                    is FileClaimUseCase.Result.Filed -> State.Submitted
                    is FileClaimUseCase.Result.BookingNotFound -> State.Error("Booking not found")
                    is FileClaimUseCase.Result.ValidationFailed ->
                        State.Error(r.errors.joinToString("\n"))
                }
            } catch (e: SecurityException) {
                _state.value = State.Error("Not authorized: ${e.message}")
            } catch (t: Throwable) {
                _state.value = State.Error("Failed: ${t.message}")
            }
        }
    }
}
