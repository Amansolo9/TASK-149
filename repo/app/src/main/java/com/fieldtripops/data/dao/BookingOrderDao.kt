package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fieldtripops.data.entity.BookingOrderEntity

@Dao
interface BookingOrderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(order: BookingOrderEntity)

    @Query("SELECT * FROM booking_orders WHERE id = :id")
    suspend fun findById(id: String): BookingOrderEntity?

    @Query("SELECT * FROM booking_orders WHERE travelerId = :travelerId ORDER BY createdAt DESC")
    suspend fun findByTraveler(travelerId: String): List<BookingOrderEntity>

    @Query("SELECT * FROM booking_orders WHERE state = :state ORDER BY updatedAt DESC")
    suspend fun findByState(state: String): List<BookingOrderEntity>

    @Query(
        """UPDATE booking_orders
           SET state = :newState, updatedAt = :at, lastActivityAt = :at,
               confirmedAt = CASE WHEN :newState = 'Booked' AND confirmedAt IS NULL THEN :at ELSE confirmedAt END,
               confirmedBy = CASE WHEN :newState = 'Booked' AND confirmedBy IS NULL THEN :actor ELSE confirmedBy END,
               cancelledAt = CASE WHEN :newState = 'Cancelled' THEN :at ELSE cancelledAt END,
               cancelReason = CASE WHEN :newState = 'Cancelled' THEN :reason ELSE cancelReason END
           WHERE id = :id"""
    )
    suspend fun updateState(id: String, newState: String, at: Long, actor: String, reason: String?)

    @Query("UPDATE booking_orders SET lastActivityAt = :at WHERE id = :id")
    suspend fun updateActivity(id: String, at: Long)

    @Query(
        """SELECT * FROM booking_orders
           WHERE state = 'PendingConfirmation' AND lastActivityAt < :threshold"""
    )
    suspend fun findPendingConfirmationOlderThan(threshold: Long): List<BookingOrderEntity>

    @Query("UPDATE booking_orders SET paidTotalCents = :cents WHERE id = :id")
    suspend fun setPaidTotal(id: String, cents: Long)
}
