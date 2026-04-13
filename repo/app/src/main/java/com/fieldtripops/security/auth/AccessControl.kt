package com.fieldtripops.security.auth

import com.fieldtripops.domain.model.Role

/**
 * Centralized authorization rules. Use cases call into this rather than
 * inlining role checks, so policy is auditable in one place.
 */
object AccessControl {

    fun requireRole(session: SessionContext, vararg roles: Role, operation: String) {
        if (!session.hasAnyRole(*roles)) {
            throw UnauthorizedException(
                requiredRoles = roles.toSet(),
                actualRoles = session.roles,
                operation = operation
            )
        }
    }

    /** Booking confirmation/cancellation by Agent or Administrator. */
    fun requireAgentOrAdmin(session: SessionContext, operation: String) =
        requireRole(session, Role.Agent, Role.Administrator, operation = operation)

    /** Refund approval requires Agent (with refund grant) or Administrator. */
    fun requireRefundApprover(session: SessionContext) =
        requireRole(session, Role.Agent, Role.Administrator, operation = "refund.approve")

    /** Reviewer or Administrator for moderation/governance actions. */
    fun requireReviewerOrAdmin(session: SessionContext, operation: String) =
        requireRole(session, Role.Reviewer, Role.Administrator, operation = operation)

    /** Administrator-only privileged config (SLA, retention, deletion approval). */
    fun requireAdmin(session: SessionContext, operation: String) =
        requireRole(session, Role.Administrator, operation = operation)

    /**
     * Object-level ownership check: traveler can act on their own entity, or
     * any of [overrideRoles] (Agent/Reviewer/Admin per policy) can act regardless.
     */
    fun requireOwnerOrRole(
        session: SessionContext,
        ownerUserId: String,
        entityType: String,
        entityId: String,
        vararg overrideRoles: Role
    ) {
        if (session.userId == ownerUserId) return
        if (overrideRoles.any { session.hasRole(it) }) return
        throw OwnershipViolationException(session.userId, entityType, entityId)
    }
}
