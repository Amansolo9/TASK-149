package com.fieldtripops.domain.booking

import com.fieldtripops.domain.model.FeeCategory
import com.fieldtripops.domain.model.FeeItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class FeeCalculatorTest {

    private fun fee(category: FeeCategory, amt: String, desc: String = "x", order: Int = 0) =
        FeeItem("id-$category-$order", "bk1", category, desc, BigDecimal(amt).setScale(2), order)

    @Test
    fun `calculate sums categories correctly`() {
        val items = listOf(
            fee(FeeCategory.BASE_FARE, "100.00"),
            fee(FeeCategory.BASE_FARE, "50.00", order = 1),
            fee(FeeCategory.TAX_FEE, "12.50"),
            fee(FeeCategory.ADJUSTMENT, "-5.00")
        )
        val result = FeeCalculator.calculate(items)
        assertThat(result.baseFareTotal).isEqualTo(BigDecimal("150.00"))
        assertThat(result.taxFeeTotal).isEqualTo(BigDecimal("12.50"))
        assertThat(result.adjustmentTotal).isEqualTo(BigDecimal("-5.00"))
        assertThat(result.grandTotal).isEqualTo(BigDecimal("157.50"))
    }

    @Test
    fun `empty items total zero`() {
        val result = FeeCalculator.calculate(emptyList())
        assertThat(result.grandTotal).isEqualTo(BigDecimal("0.00"))
    }

    @Test
    fun `validate fails when no base fare`() {
        val items = listOf(fee(FeeCategory.TAX_FEE, "10.00"))
        val r = FeeCalculator.validate(items)
        assertThat(r).isInstanceOf(FeeCalculator.ValidationResult.Invalid::class.java)
    }

    @Test
    fun `validate fails when tax negative`() {
        val items = listOf(
            fee(FeeCategory.BASE_FARE, "100.00"),
            fee(FeeCategory.TAX_FEE, "-5.00")
        )
        val r = FeeCalculator.validate(items) as FeeCalculator.ValidationResult.Invalid
        assertThat(r.reasons).hasSize(1)
    }

    @Test
    fun `validate passes with base and non-negative taxes`() {
        val items = listOf(
            fee(FeeCategory.BASE_FARE, "100.00"),
            fee(FeeCategory.TAX_FEE, "0.00"),
            fee(FeeCategory.ADJUSTMENT, "-10.00")
        )
        assertThat(FeeCalculator.validate(items))
            .isEqualTo(FeeCalculator.ValidationResult.Valid)
    }

    @Test
    fun `adjustment may be positive or negative`() {
        val items = listOf(
            fee(FeeCategory.BASE_FARE, "100.00"),
            fee(FeeCategory.ADJUSTMENT, "25.00"),
            fee(FeeCategory.ADJUSTMENT, "-10.00", order = 1)
        )
        val r = FeeCalculator.calculate(items)
        assertThat(r.adjustmentTotal).isEqualTo(BigDecimal("15.00"))
        assertThat(r.grandTotal).isEqualTo(BigDecimal("115.00"))
    }
}
