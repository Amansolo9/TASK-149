package com.fieldtripops.domain.booking

import com.fieldtripops.domain.model.RefundRule
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

/**
 * Computes refund amounts using PERSISTED rules supplied by RefundRuleRepository.
 *
 * Matching order:
 *  - rules sorted by minHoursBeforeStartExclusive DESC
 *  - the first rule whose (minHoursBeforeStartExclusive < hoursUntilStart)
 *    AND (maxHoursBeforeStartInclusive == null || hoursUntilStart <= max)
 *    is selected
 *
 * Boundary semantics that the seeded defaults preserve:
 *  - > 48h  → 100% (FULL)
 *  - 24h < h ≤ 48h → 50% (PARTIAL)
 *  - ≤ 24h  → 0% (NONE)
 *
 * "48h exact" falls into 24–48; "24h exact" into <24 nonrefundable.
 * All identical to pre-refactor behavior; source changed from hardcoded
 * constants to persisted rows so admins can edit rules.
 */
object RefundEngine {

    data class Quote(
        val refundPercent: Int,
        val refundAmount: BigDecimal,
        val ruleUsed: String
    )

    // Legacy constants retained for existing tests and callers.
    const val RULE_FULL = RefundRule.CODE_FULL
    const val RULE_PARTIAL = RefundRule.CODE_PARTIAL
    const val RULE_NONE = RefundRule.CODE_NONE

    /**
     * Build the default in-memory rule set — used by tests and by
     * the legacy `compute(paidTotal, tripStart, now)` overload.
     */
    fun defaultRules(): List<RefundRule> = listOf(
        RefundRule(
            id = "default-full", code = RULE_FULL,
            minHoursBeforeStartExclusive = 48, maxHoursBeforeStartInclusive = null,
            refundPercent = 100, description = "> 48h", active = true,
            updatedAt = Instant.EPOCH, updatedBy = "system"
        ),
        RefundRule(
            id = "default-partial", code = RULE_PARTIAL,
            minHoursBeforeStartExclusive = 24, maxHoursBeforeStartInclusive = 48,
            refundPercent = 50, description = "24–48h", active = true,
            updatedAt = Instant.EPOCH, updatedBy = "system"
        ),
        RefundRule(
            id = "default-none", code = RULE_NONE,
            minHoursBeforeStartExclusive = -1, maxHoursBeforeStartInclusive = 24,
            refundPercent = 0, description = "< 24h", active = true,
            updatedAt = Instant.EPOCH, updatedBy = "system"
        )
    )

    fun compute(
        paidTotal: BigDecimal, tripStart: Instant, now: Instant = Instant.now()
    ): Quote = compute(paidTotal, tripStart, now, defaultRules())

    fun compute(
        paidTotal: BigDecimal,
        tripStart: Instant,
        now: Instant,
        rules: List<RefundRule>
    ): Quote {
        require(rules.isNotEmpty()) { "RefundEngine requires at least one rule" }

        // Exact millis are used for both boundaries; this avoids the rounding
        // artifacts where toHours() truncation would put 48h+1min into the
        // partial band (original bug fixed here).
        val millisUntil = Duration.between(now, tripStart).toMillis()
        val hoursD = millisUntil / (1000.0 * 60 * 60)

        val active = rules.filter { it.active }
            .sortedByDescending { it.minHoursBeforeStartExclusive }

        val chosen = active.firstOrNull { rule ->
            val hi = rule.maxHoursBeforeStartInclusive?.toDouble()
            hoursD > rule.minHoursBeforeStartExclusive.toDouble() &&
                (hi == null || hoursD <= hi)
        } ?: active.lastOrNull { it.refundPercent == 0 }
            ?: active.last()

        val pctBd = BigDecimal(chosen.refundPercent)
        val refund = paidTotal.multiply(pctBd)
            .divide(BigDecimal(100), 2, RoundingMode.HALF_EVEN)
            .coerceAtMost(paidTotal)

        return Quote(
            refundPercent = chosen.refundPercent,
            refundAmount = refund.setScale(2, RoundingMode.HALF_EVEN),
            ruleUsed = chosen.code
        )
    }

    private fun BigDecimal.coerceAtMost(max: BigDecimal): BigDecimal =
        if (this > max) max else this
}
