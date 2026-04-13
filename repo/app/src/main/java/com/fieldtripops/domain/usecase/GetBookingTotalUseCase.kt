package com.fieldtripops.domain.usecase

import com.fieldtripops.domain.booking.FeeCalculator
import com.fieldtripops.domain.repository.FeeItemRepository

class GetBookingTotalUseCase(
    private val feeItemRepository: FeeItemRepository
) {
    suspend fun execute(bookingOrderId: String): FeeCalculator.FeeBreakdown {
        val items = feeItemRepository.findByBooking(bookingOrderId)
        return FeeCalculator.calculate(items)
    }
}
