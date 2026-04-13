package com.fieldtripops.domain.model

import java.math.BigDecimal

/**
 * A line item in a booking's fee itemization.
 * Separated into base fare, taxes/fees, and adjustments per PRD §9.4.
 * All amounts stored in USD with scale 2.
 */
data class FeeItem(
    val id: String,
    val bookingOrderId: String,
    val category: FeeCategory,
    val description: String,
    val amountUsd: BigDecimal,
    val sortOrder: Int
) {
    init {
        require(amountUsd.scale() <= 2) {
            "Fee amounts must have max 2 decimal places (got scale ${amountUsd.scale()})"
        }
    }
}

enum class FeeCategory {
    BASE_FARE,
    TAX_FEE,
    ADJUSTMENT
}
