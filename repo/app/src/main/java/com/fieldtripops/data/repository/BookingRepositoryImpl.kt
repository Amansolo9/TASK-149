package com.fieldtripops.data.repository

import com.fieldtripops.data.dao.BookingOrderDao
import com.fieldtripops.data.entity.BookingOrderEntity
import com.fieldtripops.domain.model.BookingOrder
import com.fieldtripops.domain.model.BookingState
import com.fieldtripops.domain.repository.BookingRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

class BookingRepositoryImpl(
    private val dao: BookingOrderDao
) : BookingRepository {

    override suspend fun save(order: BookingOrder) {
        dao.upsert(order.toEntity())
    }

    override suspend fun findById(id: String): BookingOrder? = dao.findById(id)?.toDomain()

    override suspend fun findByTraveler(travelerId: String): List<BookingOrder> =
        dao.findByTraveler(travelerId).map { it.toDomain() }

    override suspend fun findByState(state: BookingState): List<BookingOrder> =
        dao.findByState(state.name).map { it.toDomain() }

    override suspend fun updateState(
        id: String, newState: BookingState, at: Instant, actor: String, reason: String?
    ) {
        dao.updateState(id, newState.name, at.toEpochMilli(), actor, reason)
    }

    override suspend fun updateActivity(id: String, at: Instant) {
        dao.updateActivity(id, at.toEpochMilli())
    }

    override suspend fun findPendingConfirmationOlderThan(threshold: Instant): List<BookingOrder> =
        dao.findPendingConfirmationOlderThan(threshold.toEpochMilli()).map { it.toDomain() }

    override suspend fun setPaidTotal(id: String, paidTotalCents: Long) {
        dao.setPaidTotal(id, paidTotalCents)
    }

    private fun BookingOrderEntity.toDomain() = BookingOrder(
        id = id,
        itineraryId = itineraryId,
        travelerId = travelerId,
        inventorySlotId = inventorySlotId,
        partySize = partySize,
        state = BookingState.valueOf(state),
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        confirmedAt = confirmedAt?.let { Instant.ofEpochMilli(it) },
        confirmedBy = confirmedBy,
        cancelledAt = cancelledAt?.let { Instant.ofEpochMilli(it) },
        cancelReason = cancelReason,
        lastActivityAt = Instant.ofEpochMilli(lastActivityAt),
        tripStartAt = Instant.ofEpochMilli(tripStartAt),
        tripEndAt = Instant.ofEpochMilli(tripEndAt),
        paidTotal = BigDecimal(paidTotalCents).movePointLeft(2).setScale(2, RoundingMode.HALF_EVEN)
    )

    private fun BookingOrder.toEntity() = BookingOrderEntity(
        id = id,
        itineraryId = itineraryId,
        travelerId = travelerId,
        inventorySlotId = inventorySlotId,
        partySize = partySize,
        state = state.name,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        confirmedAt = confirmedAt?.toEpochMilli(),
        confirmedBy = confirmedBy,
        cancelledAt = cancelledAt?.toEpochMilli(),
        cancelReason = cancelReason,
        lastActivityAt = lastActivityAt.toEpochMilli(),
        tripStartAt = tripStartAt.toEpochMilli(),
        tripEndAt = tripEndAt.toEpochMilli(),
        paidTotalCents = paidTotal.setScale(2, RoundingMode.HALF_EVEN).movePointRight(2).toLong()
    )
}
