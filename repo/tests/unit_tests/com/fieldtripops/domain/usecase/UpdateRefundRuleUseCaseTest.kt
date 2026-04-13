package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.RefundRule
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.RefundRuleRepository
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import com.fieldtripops.security.auth.UnauthorizedException
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class UpdateRefundRuleUseCaseTest {

    private lateinit var repo: RefundRuleRepository
    private lateinit var audit: AuditLogger
    private lateinit var session: SessionManager
    private lateinit var uc: UpdateRefundRuleUseCase

    @Before
    fun setup() {
        repo = mockk(relaxed = true)
        coEvery { repo.findByCode(any()) } returns null
        audit = mockk(relaxed = true)
        session = SessionManager()
        uc = UpdateRefundRuleUseCase(repo, audit, session)
    }

    @Test(expected = UnauthorizedException::class)
    fun `reviewer cannot update refund rules`() = runTest {
        session.set(SessionContext("rev1", "R", setOf(Role.Reviewer), "s1"))
        uc.upsert(RefundRule.CODE_FULL, 48, null, 100, "full", true)
    }

    @Test(expected = UnauthorizedException::class)
    fun `agent cannot update refund rules`() = runTest {
        session.set(SessionContext("a1", "Agent", setOf(Role.Agent), "s1"))
        uc.upsert(RefundRule.CODE_FULL, 48, null, 100, "full", true)
    }

    @Test
    fun `admin can create new refund rule`() = runTest {
        session.set(SessionContext("admin1", "Admin", setOf(Role.Administrator), "s1"))
        val r = uc.upsert(RefundRule.CODE_FULL, 48, null, 100, "full", true)
            as UpdateRefundRuleUseCase.Result.Saved
        assertThat(r.rule.code).isEqualTo(RefundRule.CODE_FULL)
        assertThat(r.rule.refundPercent).isEqualTo(100)
        coVerify { repo.upsert(any()) }
        coVerify { audit.log("admin1", AuditAction.REFUND_RULE_CREATED, "RefundRule", any(), any()) }
    }

    @Test
    fun `update emits REFUND_RULE_UPDATED when rule exists`() = runTest {
        coEvery { repo.findByCode(RefundRule.CODE_PARTIAL) } returns RefundRule(
            id = "x", code = RefundRule.CODE_PARTIAL,
            minHoursBeforeStartExclusive = 24, maxHoursBeforeStartInclusive = 48,
            refundPercent = 50, description = "partial", active = true,
            updatedAt = java.time.Instant.EPOCH, updatedBy = "system"
        )
        session.set(SessionContext("admin1", "Admin", setOf(Role.Administrator), "s1"))
        uc.upsert(RefundRule.CODE_PARTIAL, 24, 48, 75, "bump", true)
        coVerify { audit.log("admin1", AuditAction.REFUND_RULE_UPDATED, "RefundRule", "x", any()) }
    }

    @Test
    fun `invalid percent rejected`() = runTest {
        session.set(SessionContext("admin1", "Admin", setOf(Role.Administrator), "s1"))
        val r = uc.upsert("X", 0, null, 200, "bad", true)
        assertThat(r).isInstanceOf(UpdateRefundRuleUseCase.Result.Invalid::class.java)
    }

    @Test
    fun `max less than min rejected`() = runTest {
        session.set(SessionContext("admin1", "Admin", setOf(Role.Administrator), "s1"))
        val r = uc.upsert("X", 48, 24, 100, "bad range", true)
        assertThat(r).isInstanceOf(UpdateRefundRuleUseCase.Result.Invalid::class.java)
    }
}
