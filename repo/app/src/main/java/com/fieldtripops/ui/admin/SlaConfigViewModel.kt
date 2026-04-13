package com.fieldtripops.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtripops.domain.repository.SlaConfigRepository
import com.fieldtripops.domain.sla.SlaConfig
import com.fieldtripops.domain.usecase.UpdateSlaConfigUseCase
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import kotlinx.coroutines.launch

class SlaConfigViewModel(
    private val repository: SlaConfigRepository,
    private val updateSlaConfigUseCase: UpdateSlaConfigUseCase,
    private val sessionManager: SessionManager
) : ViewModel() {

    sealed class State {
        data class Loaded(val config: SlaConfig) : State()
        data class Saved(val config: SlaConfig) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableLiveData<State>()
    val state: LiveData<State> = _state

    fun load() {
        viewModelScope.launch {
            try {
                val session = sessionManager.requireSession()
                AccessControl.requireAdmin(session, "sla.read")
                _state.value = State.Loaded(repository.get())
            } catch (e: SecurityException) {
                _state.value = State.Error("Not authorized to view SLA configuration")
            }
        }
    }

    fun save(
        firstMinutes: Int,
        resolutionMinutes: Int,
        noResponseHours: Int,
        workDayStartHour: Int = 9,
        workDayEndHour: Int = 17,
        excludeWeekends: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                when (val r = updateSlaConfigUseCase.execute(
                    firstMinutes, resolutionMinutes, noResponseHours,
                    workDayStartHour, workDayEndHour, excludeWeekends
                )) {
                    is UpdateSlaConfigUseCase.Result.Updated -> _state.value = State.Saved(r.config)
                    is UpdateSlaConfigUseCase.Result.Invalid -> _state.value = State.Error(r.reason)
                }
            } catch (e: SecurityException) {
                _state.value = State.Error("Not authorized: ${e.message}")
            }
        }
    }
}
