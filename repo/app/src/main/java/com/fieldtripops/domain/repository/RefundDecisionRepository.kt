package com.fieldtripops.domain.repository

import com.fieldtripops.domain.model.RefundDecision

interface RefundDecisionRepository {
    suspend fun record(decision: RefundDecision)
    suspend fun getByBooking(bookingOrderId: String): List<RefundDecision>
    suspend fun getAll(): List<RefundDecision>
}
