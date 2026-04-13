package com.fieldtripops.domain.repository

import com.fieldtripops.domain.model.RescheduleRequest

interface RescheduleRepository {
    suspend fun save(request: RescheduleRequest)
    suspend fun findById(id: String): RescheduleRequest?
    suspend fun getByBooking(bookingOrderId: String): List<RescheduleRequest>
}
