package com.fieldtripops.domain.model

import java.time.Instant
import java.time.LocalDate

data class RescheduleRequest(
    val id: String,
    val bookingOrderId: String,
    val requestedBy: String,
    val requestedAt: Instant,
    val originalStartDate: LocalDate,
    val originalEndDate: LocalDate,
    val newStartDate: LocalDate,
    val newEndDate: LocalDate,
    val exceptionReason: String?,
    val approvedBy: String?,
    val approvedAt: Instant?,
    val status: RescheduleStatus
)

enum class RescheduleStatus {
    PENDING, APPROVED, REJECTED
}
