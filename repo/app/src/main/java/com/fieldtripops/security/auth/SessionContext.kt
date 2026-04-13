package com.fieldtripops.security.auth

import com.fieldtripops.domain.model.Role

/**
 * Authenticated session, the authoritative source of acting identity for ALL
 * privileged operations. Use cases must NEVER trust caller-supplied actor or
 * role strings; they must read from this context.
 *
 * The context is set by the auth/login layer immediately after a successful
 * `LoginUseCase.execute(...) -> AuthResult.Success` and cleared on logout or
 * session expiry.
 */
data class SessionContext(
    val userId: String,
    val displayName: String,
    val roles: Set<Role>,
    val sessionId: String
) {
    fun hasRole(role: Role): Boolean = roles.contains(role)
    fun hasAnyRole(vararg required: Role): Boolean = required.any { roles.contains(it) }
}
