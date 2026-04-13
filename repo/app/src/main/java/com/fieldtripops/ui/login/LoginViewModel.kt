package com.fieldtripops.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtripops.domain.model.AuthResult
import com.fieldtripops.domain.usecase.LoginUseCase
import kotlinx.coroutines.launch

class LoginViewModel(
    private val loginUseCase: LoginUseCase
) : ViewModel() {

    private val _state = MutableLiveData<LoginState>(LoginState.Idle)
    val state: LiveData<LoginState> = _state

    private var _loggedInUserId: String? = null
    val loggedInUserId: String? get() = _loggedInUserId

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _state.value = LoginState.Error("Username and password are required")
            return
        }

        _state.value = LoginState.Loading

        viewModelScope.launch {
            try {
                when (val result = loginUseCase.execute(username, password)) {
                    is AuthResult.Success -> {
                        _loggedInUserId = result.user.id
                        _state.value = LoginState.Success(result.user)
                    }
                    is AuthResult.InvalidCredentials -> {
                        _state.value = LoginState.Error("Invalid username or password")
                    }
                    is AuthResult.Locked -> {
                        _state.value = LoginState.LockedOut(result.unlockAt)
                    }
                    is AuthResult.UserInactive -> {
                        _state.value = LoginState.Error("Account is inactive")
                    }
                }
            } catch (e: Exception) {
                _state.value = LoginState.Error("Login failed: ${e.message}")
            }
        }
    }

    fun resetState() {
        _state.value = LoginState.Idle
    }
}
