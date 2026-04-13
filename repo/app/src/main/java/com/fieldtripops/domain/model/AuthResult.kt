package com.fieldtripops.domain.model

import java.time.Instant

sealed class AuthResult {
    data class Success(val session: Session, val user: User) : AuthResult()
    data class Locked(val unlockAt: Instant) : AuthResult()
    object InvalidCredentials : AuthResult()
    object UserInactive : AuthResult()
}
