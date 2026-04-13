package com.fieldtripops.domain.repository

import com.fieldtripops.domain.model.DeletionRequest
import com.fieldtripops.domain.model.DeletionScope

interface DeletionRequestRepository {
    suspend fun save(request: DeletionRequest)
    suspend fun findById(id: String): DeletionRequest?
    suspend fun findByTarget(userId: String): List<DeletionRequest>
    suspend fun listPending(): List<DeletionRequest>
    suspend fun getAll(): List<DeletionRequest>
    suspend fun hasOpenFor(userId: String): Boolean

    /**
     * Transactionally applies the requested deletion to all user-owned tables.
     * Audit logs are intentionally preserved. Returns number of downstream rows
     * touched (informational).
     */
    suspend fun executeDeletion(targetUserId: String, scope: DeletionScope): Int
}
