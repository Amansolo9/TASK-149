package com.fieldtripops.security.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thread-safe holder of the active SessionContext. Singleton in DI.
 *
 * This replaces the previous in-memory `UserIdHolder` which leaked plain user
 * ids without any role/session binding. After this change all use cases must
 * resolve the acting identity through `SessionManager.requireSession()` rather
 * than accepting actor parameters from callers.
 */
class SessionManager {

    private val _session = MutableStateFlow<SessionContext?>(null)
    val session: StateFlow<SessionContext?> = _session.asStateFlow()

    fun set(context: SessionContext) {
        _session.value = context
    }

    fun clear() {
        _session.value = null
    }

    fun current(): SessionContext? = _session.value

    fun requireSession(): SessionContext =
        _session.value ?: throw NotAuthenticatedException()
}

class NotAuthenticatedException :
    SecurityException("No authenticated session. Login required.")

/**
 * Thrown when an authenticated user lacks required authorization.
 * Distinct from `NotAuthenticatedException` so UI can route accordingly.
 */
class UnauthorizedException(
    val requiredRoles: Set<com.fieldtripops.domain.model.Role>,
    val actualRoles: Set<com.fieldtripops.domain.model.Role>,
    val operation: String
) : SecurityException(
    "User with roles $actualRoles cannot perform '$operation'. Required: $requiredRoles"
)

/**
 * Thrown when an authenticated user attempts to act on an entity they don't
 * own and aren't authorized to act on by role.
 */
class OwnershipViolationException(
    val userId: String,
    val entityType: String,
    val entityId: String
) : SecurityException(
    "User $userId is not the owner of $entityType $entityId and lacks override role."
)
