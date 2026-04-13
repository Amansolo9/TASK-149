package com.fieldtripops.domain.booking

import com.fieldtripops.domain.model.FeeCategory
import com.fieldtripops.domain.model.FeeItem
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Deterministic fee total calculation per PRD §9.4.
 * total_due = sum(base_fare_lines) + sum(tax_fee_lines) + sum(adjustment_lines)
 */
object FeeCalculator {

    data class FeeBreakdown(
        val baseFareTotal: BigDecimal,
        val taxFeeTotal: BigDecimal,
        val adjustmentTotal: BigDecimal,
        val grandTotal: BigDecimal
    )

    fun calculate(items: List<FeeItem>): FeeBreakdown {
        val baseFareTotal = sumCategory(items, FeeCategory.BASE_FARE)
        val taxFeeTotal = sumCategory(items, FeeCategory.TAX_FEE)
        val adjustmentTotal = sumCategory(items, FeeCategory.ADJUSTMENT)
        val grandTotal = (baseFareTotal + taxFeeTotal + adjustmentTotal)
            .setScale(2, RoundingMode.HALF_EVEN)
        return FeeBreakdown(baseFareTotal, taxFeeTotal, adjustmentTotal, grandTotal)
    }

    private fun sumCategory(items: List<FeeItem>, category: FeeCategory): BigDecimal {
        return items.filter { it.category == category }
            .fold(BigDecimal.ZERO) { acc, item -> acc + item.amountUsd }
            .setScale(2, RoundingMode.HALF_EVEN)
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reasons: List<String>) : ValidationResult()
    }

    /**
     * Validates fee itemization per PRD §9.4:
     * - At least one base fare line required
     * - Taxes/fees must be non-negative
     * - Adjustments may be positive or negative
     */
    fun validate(items: List<FeeItem>): ValidationResult {
        val errors = mutableListOf<String>()

        val baseFareLines = items.filter { it.category == FeeCategory.BASE_FARE }
        if (baseFareLines.isEmpty()) {
            errors += "At least one base fare line is required"
        }

        items.filter { it.category == FeeCategory.TAX_FEE }.forEach { fee ->
            if (fee.amountUsd.signum() < 0) {
                errors += "Tax/fee line '${fee.description}' cannot be negative"
            }
        }

        items.forEach { item ->
            if (item.amountUsd.scale() > 2) {
                errors += "Fee line '${item.description}' has more than 2 decimal places"
            }
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}
