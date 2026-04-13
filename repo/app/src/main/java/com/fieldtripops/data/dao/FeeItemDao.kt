package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fieldtripops.data.entity.FeeItemEntity

@Dao
interface FeeItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FeeItemEntity>)

    @Query("DELETE FROM fee_items WHERE bookingOrderId = :bookingOrderId")
    suspend fun deleteByBooking(bookingOrderId: String)

    @Query("SELECT * FROM fee_items WHERE bookingOrderId = :bookingOrderId ORDER BY sortOrder ASC")
    suspend fun findByBooking(bookingOrderId: String): List<FeeItemEntity>

    @Query("DELETE FROM fee_items WHERE id = :id")
    suspend fun deleteById(id: String)
}
