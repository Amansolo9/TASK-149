package com.fieldtripops.domain.repository

import com.fieldtripops.domain.model.ItineraryDraft

interface ItineraryRepository {
    suspend fun save(draft: ItineraryDraft)
    suspend fun findById(id: String): ItineraryDraft?
    suspend fun findByTraveler(travelerId: String): List<ItineraryDraft>
    suspend fun markSubmitted(id: String)
    suspend fun delete(id: String)
}
