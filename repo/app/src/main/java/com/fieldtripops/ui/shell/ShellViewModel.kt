package com.fieldtripops.ui.shell

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtripops.domain.repository.UserRepository
import com.fieldtripops.domain.usecase.LogoutUseCase
import com.fieldtripops.domain.usecase.ValidateSessionUseCase
import com.fieldtripops.security.auth.SessionManager
import kotlinx.coroutines.launch

class ShellViewModel(
    private val validateSessionUseCase: ValidateSessionUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableLiveData<ShellState>(ShellState.Loading)
    val state: LiveData<ShellState> = _state

    fun initialize() {
        validateSession()
    }

    fun validateSession() {
        val session = sessionManager.current() ?: run {
            _state.value = ShellState.SessionExpired
            return
        }
        viewModelScope.launch {
            when (validateSessionUseCase.execute(session.userId)) {
                is ValidateSessionUseCase.Result.Valid -> {
                    val user = userRepository.findById(session.userId)
                    if (user != null) _state.value = ShellState.Active(user)
                    else _state.value = ShellState.SessionExpired
                }
                else -> _state.value = ShellState.SessionExpired
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase.execute()
            _state.value = ShellState.LoggedOut
        }
    }
}
