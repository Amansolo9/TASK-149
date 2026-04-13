package com.fieldtripops.ui.shell

import com.fieldtripops.domain.model.User

sealed class ShellState {
    object Loading : ShellState()
    data class Active(val user: User) : ShellState()
    object SessionExpired : ShellState()
    object LoggedOut : ShellState()
}
