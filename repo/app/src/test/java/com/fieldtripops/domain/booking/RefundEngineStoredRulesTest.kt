package com.fieldtripops.domain.booking

import com.fieldtripops.domain.model.RefundRule
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/**
 * Proves that RefundEngine outcomes are driven by supplied rules (i.e. the
 * persisted set), not by hardcoded constants. The default rule set preserves
 * the original time bands. Changing the rule set changes the outcome.
 */
class RefundEngineStoredRulesTest {

    private val paid = BigDecimal("200.00")

    private val customRules = listOf(
        RefundRule(
            id = "r1", code = "GENEROUS_OVER_24H",
            minHoursBeforeStartExclusive = 24, maxHoursBeforeStartInclusive = null,
            refundPercent = 80, description = "> 24h 80%", active = true,
            updatedAt = Instant.EPOCH, updatedBy = "admin"
        ),
        RefundRule(
            id = "r2", code = "NONE_UNDER_24H",
            minHoursBeforeStartExclusive = -1, maxHoursBeforeStartInclusive = 24,
            refundPercent = 0, description = "≤ 24h 0%", active = true,
            updatedAt = Instant.EPOCH, updatedBy = "admin"
        )
    )

    @Test
    fun `default rules preserve greater than 48 hour 100 percent boundary`() {
        val now = Instant.parse("2026-05-01T00:00:00Z")
        val q = RefundEngine.compute(paid, now.plus(Duration.ofHours(72)), now, RefundEngine.defaultRules())
        assertThat(q.refundPercent).isEqualTo(100)
        assertThat(q.ruleUsed).isEqualTo(RefundRule.CODE_FULL)
    }

    @Test
    fun `default rules preserve 24 to 48 hour 50 percent`() {
        val now = Instant.parse("2026-05-01T00:00:00Z")
        val q = RefundEngine.compute(paid, now.plus(Duration.ofHours(36)), now, RefundEngine.defaultRules())
        assertThat(q.refundPercent).isEqualTo(50)
        assertThat(q.ruleUsed).isEqualTo(RefundRule.CODE_PARTIAL)
    }

    @Test
    fun `default rules preserve under 24 hour non-refundable`() {
        val now = Instant.parse("2026-05-01T00:00:00Z")
        val q = RefundEngine.compute(paid, now.plus(Duration.ofHours(12)), now, RefundEngine.defaultRules())
        assertThat(q.refundPercent).isEqualTo(0)
        assertThat(q.ruleUsed).isEqualTo(RefundRule.CODE_NONE)
    }

    @Test
    fun `outcome changes when stored rules change`() {
        val now = Instant.parse("2026-05-01T00:00:00Z")
        val q = RefundEngine.compute(paid, now.plus(Duration.ofHours(36)), now, customRules)
        // Under custom rules, 36h > 24h → 80% refund
        assertThat(q.refundPercent).isEqualTo(80)
        assertThat(q.ruleUsed).isEqualTo("GENEROUS_OVER_24H")
    }

    @Test
    fun `exact 24h still non-refundable under defaults`() {
        val now = Instant.parse("2026-05-01T00:00:00Z")
        val q = RefundEngine.compute(paid, now.plus(Duration.ofHours(24)), now, RefundEngine.defaultRules())
        assertThat(q.refundPercent).isEqualTo(0)
    }
}
