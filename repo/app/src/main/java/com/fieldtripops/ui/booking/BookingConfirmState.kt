package com.fieldtripops.ui.booking

import com.fieldtripops.domain.model.BookingOrder
import com.fieldtripops.domain.model.FeeItem
import java.math.BigDecimal

data class BookingConfirmUiState(
    val order: BookingOrder? = null,
    val feeItems: List<FeeItem> = emptyList(),
    val total: BigDecimal = BigDecimal.ZERO.setScale(2)
)

sealed class BookingConfirmResult {
    object Idle : BookingConfirmResult()
    data class Confirmed(val total: BigDecimal) : BookingConfirmResult()
    object Cancelled : BookingConfirmResult()
    data class Error(val errors: List<String>) : BookingConfirmResult()
}
