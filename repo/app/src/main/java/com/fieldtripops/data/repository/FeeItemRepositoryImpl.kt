package com.fieldtripops.data.repository

import androidx.room.withTransaction
import com.fieldtripops.data.dao.FeeItemDao
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.entity.FeeItemEntity
import com.fieldtripops.domain.model.FeeCategory
import com.fieldtripops.domain.model.FeeItem
import com.fieldtripops.domain.repository.FeeItemRepository
import java.math.BigDecimal
import java.math.RoundingMode

class FeeItemRepositoryImpl(
    private val database: FieldTripDatabase,
    private val dao: FeeItemDao
) : FeeItemRepository {

    override suspend fun saveAll(items: List<FeeItem>) {
        dao.insertAll(items.map { it.toEntity() })
    }

    override suspend fun replaceForBooking(bookingOrderId: String, items: List<FeeItem>) {
        database.withTransaction {
            dao.deleteByBooking(bookingOrderId)
            if (items.isNotEmpty()) dao.insertAll(items.map { it.toEntity() })
        }
    }

    override suspend fun findByBooking(bookingOrderId: String): List<FeeItem> {
        return dao.findByBooking(bookingOrderId).map { it.toDomain() }
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    private fun FeeItemEntity.toDomain() = FeeItem(
        id = id,
        bookingOrderId = bookingOrderId,
        category = FeeCategory.valueOf(category),
        description = description,
        amountUsd = BigDecimal(amountCents).movePointLeft(2).setScale(2, RoundingMode.HALF_EVEN),
        sortOrder = sortOrder
    )

    private fun FeeItem.toEntity() = FeeItemEntity(
        id = id,
        bookingOrderId = bookingOrderId,
        category = category.name,
        description = description,
        amountCents = amountUsd.setScale(2, RoundingMode.HALF_EVEN).movePointRight(2).toLong(),
        sortOrder = sortOrder
    )
}
