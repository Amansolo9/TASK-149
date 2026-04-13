package com.fieldtripops.domain.model

import java.time.Instant

/**
 * Stored refund rule — replaces the previously hardcoded time-band constants
 * in RefundEngine. An admin can add/edit/disable rules via UpdateRefundRulesUseCase.
 *
 * Matching: a rule matches when hoursUntilStart is strictly greater than
 * [minHoursBeforeStartExclusive] and (when set) less than or equal to
 * [maxHoursBeforeStartInclusive]. Rules are evaluated in descending
 * [minHoursBeforeStartExclusive] order — first match wins.
 */
data class RefundRule(
    val id: String,
    val code: String,
    val minHoursBeforeStartExclusive: Int,
    val maxHoursBeforeStartInclusive: Int?,
    val refundPercent: Int,
    val description: String,
    val active: Boolean,
    val updatedAt: Instant,
    val updatedBy: String
) {
    companion object {
        const val CODE_FULL = "FULL_REFUND_OVER_48H"
        const val CODE_PARTIAL = "PARTIAL_REFUND_24_TO_48H"
        const val CODE_NONE = "NO_REFUND_UNDER_24H"
    }
}
