package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.fieldtripops.data.entity.QuotaLedgerEntity

@Dao
interface QuotaLedgerDao {
    @Insert
    suspend fun insert(entry: QuotaLedgerEntity)

    @Query("SELECT * FROM quota_ledger WHERE bookingOrderId = :bookingOrderId ORDER BY timestamp ASC")
    suspend fun getByBooking(bookingOrderId: String): List<QuotaLedgerEntity>

    @Query("SELECT * FROM quota_ledger WHERE inventorySlotId = :slotId ORDER BY timestamp ASC")
    suspend fun getBySlot(slotId: String): List<QuotaLedgerEntity>
}
