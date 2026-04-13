package com.fieldtripops.ui.claims

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtripops.domain.model.ClaimTicket
import com.fieldtripops.domain.repository.ClaimRepository
import com.fieldtripops.domain.usecase.FileAppealUseCase
import com.fieldtripops.security.auth.SessionManager
import kotlinx.coroutines.launch

class MyClaimsViewModel(
    private val claimRepository: ClaimRepository,
    private val sessionManager: SessionManager,
    private val fileAppealUseCase: FileAppealUseCase
) : ViewModel() {

    private val _tickets = MutableLiveData<List<ClaimTicket>>(emptyList())
    val tickets: LiveData<List<ClaimTicket>> = _tickets

    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    fun load() {
        val session = sessionManager.current() ?: return
        viewModelScope.launch {
            _tickets.value = claimRepository.findByTraveler(session.userId)
        }
    }

    fun fileAppeal(ticketId: String, reason: String) {
        viewModelScope.launch {
            try {
                when (val r = fileAppealUseCase.execute(ticketId, reason)) {
                    is FileAppealUseCase.Result.Filed ->
                        _message.value = "Appeal filed; queued for review (${r.queueId.take(8)}…)"
                    is FileAppealUseCase.Result.InvalidState ->
                        _message.value = "Cannot appeal ticket in state ${r.state.name}"
                    is FileAppealUseCase.Result.ReasonRequired ->
                        _message.value = "Appeal reason required"
                    is FileAppealUseCase.Result.TicketNotFound ->
                        _message.value = "Ticket not found"
                }
                load()
            } catch (e: SecurityException) {
                _message.value = "Not authorized: ${e.message}"
            }
        }
    }

    fun clearMessage() { _message.value = null }
}
