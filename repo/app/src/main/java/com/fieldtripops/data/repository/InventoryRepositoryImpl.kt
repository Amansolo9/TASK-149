package com.fieldtripops.data.repository

import androidx.room.withTransaction
import com.fieldtripops.data.dao.InventorySlotDao
import com.fieldtripops.data.dao.QuotaLedgerDao
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.entity.InventorySlotEntity
import com.fieldtripops.data.entity.QuotaLedgerEntity
import com.fieldtripops.domain.model.InventorySlot
import com.fieldtripops.domain.model.QuotaLedgerEntry
import com.fieldtripops.domain.model.QuotaOperation
import com.fieldtripops.domain.repository.InventoryRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class InventoryRepositoryImpl(
    private val database: FieldTripDatabase,
    private val inventorySlotDao: InventorySlotDao,
    private val quotaLedgerDao: QuotaLedgerDao
) : InventoryRepository {

    override suspend fun findSlot(slotId: String): InventorySlot? {
        return inventorySlotDao.findById(slotId)?.toDomain()
    }

    override suspend fun findAvailableSlots(itineraryType: String, partySize: Int): List<InventorySlot> {
        return inventorySlotDao.findAvailableByType(itineraryType, partySize).map { it.toDomain() }
    }

    override suspend fun saveSlot(slot: InventorySlot) {
        inventorySlotDao.upsert(slot.toEntity())
    }

    override suspend fun applyQuotaOperation(
        slotId: String,
        operation: QuotaOperation,
        units: Int,
        bookingOrderId: String,
        actor: String,
        reason: String?
    ): QuotaLedgerEntry {
        return database.withTransaction {
            val slot = inventorySlotDao.findById(slotId)
                ?: throw IllegalStateException("Inventory slot $slotId not found")

            when (operation) {
                QuotaOperation.RESERVE -> {
                    val available = slot.totalQuota - slot.reservedCount - slot.bookedCount
                    if (available < units) {
                        throw IllegalStateException(
                            "Insufficient quota: requested $units, available $available"
                        )
                    }
                    inventorySlotDao.adjustReserved(slotId, units)
                }
                QuotaOperation.CONFIRM -> {
                    if (slot.reservedCount < units) {
                        throw IllegalStateException(
                            "Cannot confirm $units units; only ${slot.reservedCount} reserved"
                        )
                    }
                    inventorySlotDao.adjustCounts(slotId, reservedDelta = -units, bookedDelta = units)
                }
                QuotaOperation.RELEASE -> {
                    // Release: determine which counter to decrement based on recent ledger entries.
                    val ledger = quotaLedgerDao.getByBooking(bookingOrderId)
                    val hasConfirm = ledger.any { it.operation == QuotaOperation.CONFIRM.name }
                    if (hasConfirm) {
                        if (slot.bookedCount < units) {
                            throw IllegalStateException(
                                "Cannot release $units booked units; only ${slot.bookedCount} present"
                            )
                        }
                        inventorySlotDao.adjustBooked(slotId, -units)
                    } else {
                        if (slot.reservedCount < units) {
                            throw IllegalStateException(
                                "Cannot release $units reserved units; only ${slot.reservedCount} present"
                            )
                        }
                        inventorySlotDao.adjustReserved(slotId, -units)
                    }
                }
                QuotaOperation.EXCEPTION -> {
                    if (!slot.allowExceptionBooking) {
                        throw IllegalStateException(
                            "Exception booking is not enabled for slot $slotId"
                        )
                    }
                    inventorySlotDao.adjustBooked(slotId, units)
                }
            }

            val entry = QuotaLedgerEntry(
                id = UUID.randomUUID().toString(),
                inventorySlotId = slotId,
                bookingOrderId = bookingOrderId,
                operation = operation,
                units = units,
                actor = actor,
                timestamp = Instant.now(),
                reason = reason
            )
            quotaLedgerDao.insert(entry.toEntity())
            entry
        }
    }

    override suspend fun getLedgerForBooking(bookingOrderId: String): List<QuotaLedgerEntry> {
        return quotaLedgerDao.getByBooking(bookingOrderId).map { it.toDomain() }
    }

    private fun InventorySlotEntity.toDomain() = InventorySlot(
        id = id,
        itineraryType = itineraryType,
        serviceName = serviceName,
        startDate = LocalDate.ofEpochDay(startDateEpochDay),
        endDate = LocalDate.ofEpochDay(endDateEpochDay),
        totalQuota = totalQuota,
        reservedCount = reservedCount,
        bookedCount = bookedCount,
        allowExceptionBooking = allowExceptionBooking
    )

    private fun InventorySlot.toEntity() = InventorySlotEntity(
        id = id,
        itineraryType = itineraryType,
        serviceName = serviceName,
        startDateEpochDay = startDate.toEpochDay(),
        endDateEpochDay = endDate.toEpochDay(),
        totalQuota = totalQuota,
        reservedCount = reservedCount,
        bookedCount = bookedCount,
        allowExceptionBooking = allowExceptionBooking
    )

    private fun QuotaLedgerEntity.toDomain() = QuotaLedgerEntry(
        id = id,
        inventorySlotId = inventorySlotId,
        bookingOrderId = bookingOrderId,
        operation = QuotaOperation.valueOf(operation),
        units = units,
        actor = actor,
        timestamp = Instant.ofEpochMilli(timestamp),
        reason = reason
    )

    private fun QuotaLedgerEntry.toEntity() = QuotaLedgerEntity(
        id = id,
        inventorySlotId = inventorySlotId,
        bookingOrderId = bookingOrderId,
        operation = operation.name,
        units = units,
        actor = actor,
        timestamp = timestamp.toEpochMilli(),
        reason = reason
    )
}
