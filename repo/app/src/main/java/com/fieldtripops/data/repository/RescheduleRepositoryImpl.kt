package com.fieldtripops.data.repository

import com.fieldtripops.data.dao.RescheduleRequestDao
import com.fieldtripops.data.entity.RescheduleRequestEntity
import com.fieldtripops.domain.model.RescheduleRequest
import com.fieldtripops.domain.model.RescheduleStatus
import com.fieldtripops.domain.repository.RescheduleRepository
import java.time.Instant
import java.time.LocalDate

class RescheduleRepositoryImpl(
    private val dao: RescheduleRequestDao
) : RescheduleRepository {

    override suspend fun save(request: RescheduleRequest) {
        dao.upsert(request.toEntity())
    }

    override suspend fun findById(id: String): RescheduleRequest? =
        dao.findById(id)?.toDomain()

    override suspend fun getByBooking(bookingOrderId: String): List<RescheduleRequest> =
        dao.getByBooking(bookingOrderId).map { it.toDomain() }

    private fun RescheduleRequestEntity.toDomain() = RescheduleRequest(
        id = id, bookingOrderId = bookingOrderId, requestedBy = requestedBy,
        requestedAt = Instant.ofEpochMilli(requestedAt),
        originalStartDate = LocalDate.ofEpochDay(originalStartDateEpochDay),
        originalEndDate = LocalDate.ofEpochDay(originalEndDateEpochDay),
        newStartDate = LocalDate.ofEpochDay(newStartDateEpochDay),
        newEndDate = LocalDate.ofEpochDay(newEndDateEpochDay),
        exceptionReason = exceptionReason,
        approvedBy = approvedBy,
        approvedAt = approvedAt?.let { Instant.ofEpochMilli(it) },
        status = RescheduleStatus.valueOf(status)
    )

    private fun RescheduleRequest.toEntity() = RescheduleRequestEntity(
        id = id, bookingOrderId = bookingOrderId, requestedBy = requestedBy,
        requestedAt = requestedAt.toEpochMilli(),
        originalStartDateEpochDay = originalStartDate.toEpochDay(),
        originalEndDateEpochDay = originalEndDate.toEpochDay(),
        newStartDateEpochDay = newStartDate.toEpochDay(),
        newEndDateEpochDay = newEndDate.toEpochDay(),
        exceptionReason = exceptionReason,
        approvedBy = approvedBy,
        approvedAt = approvedAt?.toEpochMilli(),
        status = status.name
    )
}
