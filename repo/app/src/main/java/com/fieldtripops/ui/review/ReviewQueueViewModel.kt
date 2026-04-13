package com.fieldtripops.ui.review

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtripops.domain.model.ClaimTicket
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.model.TicketState
import com.fieldtripops.domain.repository.ClaimRepository
import com.fieldtripops.domain.usecase.TransitionClaimUseCase
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import kotlinx.coroutines.launch

class ReviewQueueViewModel(
    private val claimRepository: ClaimRepository,
    private val transitionClaimUseCase: TransitionClaimUseCase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _queue = MutableLiveData<List<ClaimTicket>>(emptyList())
    val queue: LiveData<List<ClaimTicket>> = _queue

    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    /**
     * Fail-closed load. If the session does not carry Reviewer or Administrator
     * role we publish an error and skip the repository call entirely — the
     * UI cannot show claim data unless the authz check passes.
     */
    fun load() {
        viewModelScope.launch {
            try {
                val session = sessionManager.requireSession()
                AccessControl.requireReviewerOrAdmin(session, "review.queue.read")
                val submitted = claimRepository.findByState(TicketState.Submitted)
                val inReview = claimRepository.findByState(TicketState.InReview)
                val escalated = claimRepository.findByState(TicketState.Escalated)
                _queue.value = (submitted + inReview + escalated).sortedBy { it.createdAt }
            } catch (e: SecurityException) {
                _queue.value = emptyList()
                _message.value = "Not authorized to view review queue"
            }
        }
    }

    fun moveTo(ticketId: String, target: TicketState, reason: String?) {
        viewModelScope.launch {
            try {
                when (val r = transitionClaimUseCase.execute(ticketId, target, reason)) {
                    is TransitionClaimUseCase.Result.Transitioned -> {
                        _message.value = "Moved to ${target.name}"
                        load()
                    }
                    is TransitionClaimUseCase.Result.IllegalTransition ->
                        _message.value = "Illegal transition: ${r.from} -> ${r.to}"
                    is TransitionClaimUseCase.Result.TicketNotFound ->
                        _message.value = "Ticket not found"
                }
            } catch (e: SecurityException) {
                _message.value = "Not authorized: ${e.message}"
            }
        }
    }

    fun clearMessage() { _message.value = null }
}
