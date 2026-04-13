package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fieldtripops.data.entity.RescheduleRequestEntity

@Dao
interface RescheduleRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(request: RescheduleRequestEntity)

    @Query("SELECT * FROM reschedule_requests WHERE bookingOrderId = :id ORDER BY requestedAt DESC")
    suspend fun getByBooking(id: String): List<RescheduleRequestEntity>

    @Query("SELECT * FROM reschedule_requests WHERE id = :id")
    suspend fun findById(id: String): RescheduleRequestEntity?
}
