package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fieldtripops.data.entity.ItineraryDraftEntity

@Dao
interface ItineraryDraftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: ItineraryDraftEntity)

    @Query("SELECT * FROM itinerary_drafts WHERE id = :id")
    suspend fun findById(id: String): ItineraryDraftEntity?

    @Query("SELECT * FROM itinerary_drafts WHERE travelerId = :travelerId ORDER BY createdAt DESC")
    suspend fun findByTraveler(travelerId: String): List<ItineraryDraftEntity>

    @Query("UPDATE itinerary_drafts SET submitted = 1, updatedAt = :at WHERE id = :id")
    suspend fun markSubmitted(id: String, at: Long)

    @Query("DELETE FROM itinerary_drafts WHERE id = :id")
    suspend fun delete(id: String)
}
