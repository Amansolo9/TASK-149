package com.fieldtripops.security.auth

import com.fieldtripops.domain.model.Role
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccessControlTest {

    private fun session(vararg roles: Role) = SessionContext(
        userId = "u1", displayName = "User One",
        roles = roles.toSet(), sessionId = "s1"
    )

    @Test(expected = UnauthorizedException::class)
    fun `non-admin denied admin operation`() {
        AccessControl.requireAdmin(session(Role.Traveler), "test.op")
    }

    @Test
    fun `admin allowed admin operation`() {
        AccessControl.requireAdmin(session(Role.Administrator), "test.op")
    }

    @Test(expected = UnauthorizedException::class)
    fun `traveler denied agent-or-admin booking confirm`() {
        AccessControl.requireAgentOrAdmin(session(Role.Traveler), "booking.confirm")
    }

    @Test
    fun `agent allowed agent-or-admin operations`() {
        AccessControl.requireAgentOrAdmin(session(Role.Agent), "booking.confirm")
    }

    @Test
    fun `owner allowed without override role`() {
        val s = session(Role.Traveler)
        AccessControl.requireOwnerOrRole(s, "u1", "Booking", "b1", Role.Agent, Role.Administrator)
    }

    @Test(expected = OwnershipViolationException::class)
    fun `non-owner without override role denied`() {
        val s = session(Role.Traveler)
        AccessControl.requireOwnerOrRole(s, "u2", "Booking", "b1", Role.Agent, Role.Administrator)
    }

    @Test
    fun `non-owner with override role allowed`() {
        val s = session(Role.Agent)
        AccessControl.requireOwnerOrRole(s, "u2", "Booking", "b1", Role.Agent, Role.Administrator)
    }
}

class SessionManagerTest {
    @Test(expected = NotAuthenticatedException::class)
    fun `requireSession throws when empty`() {
        SessionManager().requireSession()
    }

    @Test
    fun `set then current returns session`() {
        val mgr = SessionManager()
        val ctx = SessionContext("u1", "User", setOf(Role.Agent), "s1")
        mgr.set(ctx)
        assertThat(mgr.requireSession()).isEqualTo(ctx)
    }

    @Test
    fun `clear removes session`() {
        val mgr = SessionManager()
        mgr.set(SessionContext("u1", "User", setOf(Role.Agent), "s1"))
        mgr.clear()
        assertThat(mgr.current()).isNull()
    }
}
