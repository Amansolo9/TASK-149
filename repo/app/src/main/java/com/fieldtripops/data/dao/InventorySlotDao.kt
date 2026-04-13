package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fieldtripops.data.entity.InventorySlotEntity

@Dao
interface InventorySlotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(slot: InventorySlotEntity)

    @Query("SELECT * FROM inventory_slots WHERE id = :id")
    suspend fun findById(id: String): InventorySlotEntity?

    @Query(
        """SELECT * FROM inventory_slots
           WHERE itineraryType = :type
           AND (totalQuota - reservedCount - bookedCount) >= :partySize
           ORDER BY startDateEpochDay ASC"""
    )
    suspend fun findAvailableByType(type: String, partySize: Int): List<InventorySlotEntity>

    @Query(
        """UPDATE inventory_slots
           SET reservedCount = reservedCount + :delta
           WHERE id = :id"""
    )
    suspend fun adjustReserved(id: String, delta: Int)

    @Query(
        """UPDATE inventory_slots
           SET bookedCount = bookedCount + :delta
           WHERE id = :id"""
    )
    suspend fun adjustBooked(id: String, delta: Int)

    @Query(
        """UPDATE inventory_slots
           SET reservedCount = reservedCount + :reservedDelta,
               bookedCount = bookedCount + :bookedDelta
           WHERE id = :id"""
    )
    suspend fun adjustCounts(id: String, reservedDelta: Int, bookedDelta: Int)
}
