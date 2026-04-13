package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fieldtripops.data.entity.DeletionRequestEntity

@Dao
interface DeletionRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(request: DeletionRequestEntity)

    @Query("SELECT * FROM deletion_requests WHERE id = :id")
    suspend fun findById(id: String): DeletionRequestEntity?

    @Query("SELECT * FROM deletion_requests WHERE targetUserId = :userId ORDER BY requestedAt DESC")
    suspend fun findByTarget(userId: String): List<DeletionRequestEntity>

    @Query("SELECT * FROM deletion_requests WHERE state = :state ORDER BY requestedAt DESC")
    suspend fun findByState(state: String): List<DeletionRequestEntity>

    @Query("SELECT * FROM deletion_requests ORDER BY requestedAt DESC")
    suspend fun getAll(): List<DeletionRequestEntity>

    @Query(
        """SELECT COUNT(*) FROM deletion_requests
           WHERE targetUserId = :userId AND state IN ('Requested','Approved')"""
    )
    suspend fun countOpenFor(userId: String): Int

    // Per-table anonymization/deletion helpers for transactional execution.
    @Query("DELETE FROM claim_tickets WHERE travelerId = :userId")
    suspend fun deleteClaimsByUser(userId: String)

    @Query(
        """UPDATE claim_tickets
           SET travelerId = :anonId, description = '[anonymized]'
           WHERE travelerId = :userId"""
    )
    suspend fun anonymizeClaimsByUser(userId: String, anonId: String)

    @Query("DELETE FROM booking_orders WHERE travelerId = :userId")
    suspend fun deleteBookingsByUser(userId: String)

    @Query("UPDATE booking_orders SET travelerId = :anonId WHERE travelerId = :userId")
    suspend fun anonymizeBookingsByUser(userId: String, anonId: String)

    @Query("DELETE FROM itinerary_drafts WHERE travelerId = :userId")
    suspend fun deleteItinerariesByUser(userId: String)

    @Query(
        """UPDATE itinerary_drafts SET travelerId = :anonId, notes = NULL
           WHERE travelerId = :userId"""
    )
    suspend fun anonymizeItinerariesByUser(userId: String, anonId: String)

    @Query("DELETE FROM consent_records WHERE userId = :userId")
    suspend fun deleteConsentsByUser(userId: String)

    @Query("DELETE FROM export_packages WHERE generatedBy = :userId")
    suspend fun deleteExportsByUser(userId: String)

    @Query("DELETE FROM role_assignments WHERE userId = :userId")
    suspend fun deleteRolesByUser(userId: String)

    @Query("DELETE FROM credentials WHERE userId = :userId")
    suspend fun deleteCredentialsByUser(userId: String)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: String)

    @Query(
        """UPDATE users SET username = :anonUsername, displayName = '[deleted user]',
           isActive = 0 WHERE id = :userId"""
    )
    suspend fun anonymizeUser(userId: String, anonUsername: String)
}
