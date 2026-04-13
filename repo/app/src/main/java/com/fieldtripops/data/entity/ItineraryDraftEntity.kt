package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "itinerary_drafts",
    indices = [Index(value = ["travelerId", "createdAt"])]
)
data class ItineraryDraftEntity(
    @PrimaryKey val id: String,
    val travelerId: String,
    val travelerInitials: String,
    val partySize: Int,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long,
    val notes: String?,
    val itineraryType: String,
    val createdAt: Long,
    val updatedAt: Long,
    val submitted: Boolean
)
