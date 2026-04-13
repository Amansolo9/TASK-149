package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.fieldtripops.data.entity.RefundDecisionEntity

@Dao
interface RefundDecisionDao {
    @Insert
    suspend fun insert(decision: RefundDecisionEntity)

    @Query("SELECT * FROM refund_decisions WHERE bookingOrderId = :id ORDER BY decidedAt DESC")
    suspend fun getByBooking(id: String): List<RefundDecisionEntity>

    @Query("SELECT * FROM refund_decisions ORDER BY decidedAt DESC")
    suspend fun getAll(): List<RefundDecisionEntity>
}
