package com.fieldtripops.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtripops.domain.model.DeletionRequest
import com.fieldtripops.domain.model.DeletionScope
import com.fieldtripops.domain.repository.DeletionRequestRepository
import com.fieldtripops.domain.usecase.ExecuteUserDeletionUseCase
import com.fieldtripops.domain.usecase.RequestUserDeletionUseCase
import kotlinx.coroutines.launch

/**
 * Backs both the admin deletion queue and the traveler self-service delete
 * button. Authorization is enforced in the use cases — the VM only reflects
 * outcomes.
 */
class DeletionRequestsViewModel(
    private val repository: DeletionRequestRepository,
    private val requestUseCase: RequestUserDeletionUseCase,
    private val executeUseCase: ExecuteUserDeletionUseCase,
    private val sessionManager: com.fieldtripops.security.auth.SessionManager
) : ViewModel() {

    sealed class State {
        data class Loaded(val items: List<DeletionRequest>) : State()
        data class Message(val text: String) : State()
        data class Error(val text: String) : State()
    }

    private val _state = MutableLiveData<State>()
    val state: LiveData<State> = _state

    fun loadAll() {
        viewModelScope.launch {
            try {
                // Fail-closed: only Administrator may view the full queue.
                val session = sessionManager.requireSession()
                com.fieldtripops.security.auth.AccessControl.requireAdmin(
                    session, "deletion.queue.read"
                )
                _state.value = State.Loaded(repository.getAll())
            } catch (e: SecurityException) {
                _state.value = State.Error(e.message ?: "Not authorized")
            }
        }
    }

    fun requestSelfDelete(userId: String, reason: String?) {
        viewModelScope.launch {
            try {
                when (val r = requestUseCase.execute(
                    targetUserId = userId, reason = reason,
                    scope = DeletionScope.ANONYMIZE
                )) {
                    is RequestUserDeletionUseCase.Result.Queued ->
                        _state.value = State.Message("Deletion request queued: ${r.request.id}")
                    is RequestUserDeletionUseCase.Result.AlreadyPending ->
                        _state.value = State.Message("A deletion request is already pending.")
                    is RequestUserDeletionUseCase.Result.Invalid ->
                        _state.value = State.Error(r.reason)
                }
            } catch (e: SecurityException) {
                _state.value = State.Error(e.message ?: "Not authorized")
            }
        }
    }

    fun requestOnBehalf(targetUserId: String, reason: String?, scope: DeletionScope) {
        viewModelScope.launch {
            try {
                when (val r = requestUseCase.execute(targetUserId, reason, scope)) {
                    is RequestUserDeletionUseCase.Result.Queued ->
                        _state.value = State.Message("Queued: ${r.request.id}")
                    is RequestUserDeletionUseCase.Result.AlreadyPending ->
                        _state.value = State.Message("Already pending.")
                    is RequestUserDeletionUseCase.Result.Invalid ->
                        _state.value = State.Error(r.reason)
                }
                loadAll()
            } catch (e: SecurityException) {
                _state.value = State.Error(e.message ?: "Not authorized")
            }
        }
    }

    fun approveAndExecute(requestId: String) {
        viewModelScope.launch {
            try {
                when (val r = executeUseCase.execute(requestId)) {
                    is ExecuteUserDeletionUseCase.Result.Executed ->
                        _state.value = State.Message("Executed: rows=${r.rowsTouched}")
                    is ExecuteUserDeletionUseCase.Result.AlreadyExecuted ->
                        _state.value = State.Message("Already executed.")
                    is ExecuteUserDeletionUseCase.Result.NotFound ->
                        _state.value = State.Error("Not found: ${r.id}")
                    is ExecuteUserDeletionUseCase.Result.Failed ->
                        _state.value = State.Error("Failed: ${r.reason}")
                }
                loadAll()
            } catch (e: SecurityException) {
                _state.value = State.Error(e.message ?: "Not authorized")
            }
        }
    }

    fun reject(requestId: String, reason: String) {
        viewModelScope.launch {
            try {
                executeUseCase.reject(requestId, reason)
                loadAll()
            } catch (e: SecurityException) {
                _state.value = State.Error(e.message ?: "Not authorized")
            }
        }
    }
}
