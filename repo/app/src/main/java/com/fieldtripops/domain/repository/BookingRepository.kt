package com.fieldtripops.domain.repository

import com.fieldtripops.domain.model.BookingOrder
import com.fieldtripops.domain.model.BookingState
import java.time.Instant

interface BookingRepository {
    suspend fun save(order: BookingOrder)
    suspend fun findById(id: String): BookingOrder?
    suspend fun findByTraveler(travelerId: String): List<BookingOrder>
    suspend fun findByState(state: BookingState): List<BookingOrder>
    suspend fun updateState(id: String, newState: BookingState, at: Instant, actor: String, reason: String?)
    suspend fun updateActivity(id: String, at: Instant)
    suspend fun findPendingConfirmationOlderThan(threshold: Instant): List<BookingOrder>
    suspend fun setPaidTotal(id: String, paidTotalCents: Long)
}
