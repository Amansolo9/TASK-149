package com.fieldtripops.domain.model

import java.math.BigDecimal
import java.time.Instant

/**
 * Append-only refund decision record per PRD §9.5.
 */
data class RefundDecision(
    val id: String,
    val bookingOrderId: String,
    val paidTotal: BigDecimal,
    val refundAmount: BigDecimal,
    val refundPercent: Int,
    val ruleUsed: String,
    val approverUserId: String,
    val approverName: String,
    val decidedAt: Instant,
    val manualOverrideReason: String?
)
