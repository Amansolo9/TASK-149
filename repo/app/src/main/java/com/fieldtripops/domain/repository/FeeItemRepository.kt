package com.fieldtripops.domain.repository

import com.fieldtripops.domain.model.FeeItem

interface FeeItemRepository {
    suspend fun saveAll(items: List<FeeItem>)
    suspend fun replaceForBooking(bookingOrderId: String, items: List<FeeItem>)
    suspend fun findByBooking(bookingOrderId: String): List<FeeItem>
    suspend fun delete(id: String)
}
