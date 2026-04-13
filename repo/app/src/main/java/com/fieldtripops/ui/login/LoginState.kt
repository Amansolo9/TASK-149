package com.fieldtripops.ui.login

import com.fieldtripops.domain.model.User
import java.time.Instant

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val user: User) : LoginState()
    data class Error(val message: String) : LoginState()
    data class LockedOut(val unlockAt: Instant) : LoginState()
}
