package com.fieldtripops.data.repository

import androidx.room.withTransaction
import com.fieldtripops.data.dao.DeletionRequestDao
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.entity.DeletionRequestEntity
import com.fieldtripops.domain.model.DeletionRequest
import com.fieldtripops.domain.model.DeletionScope
import com.fieldtripops.domain.model.DeletionState
import com.fieldtripops.domain.repository.DeletionRequestRepository
import java.time.Instant

class DeletionRequestRepositoryImpl(
    private val database: FieldTripDatabase,
    private val dao: DeletionRequestDao
) : DeletionRequestRepository {

    override suspend fun save(request: DeletionRequest) = dao.upsert(request.toEntity())

    override suspend fun findById(id: String): DeletionRequest? =
        dao.findById(id)?.toDomain()

    override suspend fun findByTarget(userId: String): List<DeletionRequest> =
        dao.findByTarget(userId).map { it.toDomain() }

    override suspend fun listPending(): List<DeletionRequest> =
        dao.findByState(DeletionState.Requested.name).map { it.toDomain() } +
            dao.findByState(DeletionState.Approved.name).map { it.toDomain() }

    override suspend fun getAll(): List<DeletionRequest> =
        dao.getAll().map { it.toDomain() }

    override suspend fun hasOpenFor(userId: String): Boolean =
        dao.countOpenFor(userId) > 0

    override suspend fun executeDeletion(targetUserId: String, scope: DeletionScope): Int {
        var touched = 0
        database.withTransaction {
            when (scope) {
                DeletionScope.HARD_DELETE -> {
                    dao.deleteClaimsByUser(targetUserId)
                    dao.deleteBookingsByUser(targetUserId)
                    dao.deleteItinerariesByUser(targetUserId)
                    dao.deleteConsentsByUser(targetUserId)
                    dao.deleteExportsByUser(targetUserId)
                    dao.deleteRolesByUser(targetUserId)
                    dao.deleteCredentialsByUser(targetUserId)
                    dao.deleteUser(targetUserId)
                    touched = 1
                }
                DeletionScope.ANONYMIZE -> {
                    val anonId = "anon-$targetUserId"
                    dao.anonymizeClaimsByUser(targetUserId, anonId)
                    dao.anonymizeBookingsByUser(targetUserId, anonId)
                    dao.anonymizeItinerariesByUser(targetUserId, anonId)
                    dao.deleteConsentsByUser(targetUserId)
                    dao.deleteExportsByUser(targetUserId)
                    dao.deleteRolesByUser(targetUserId)
                    dao.deleteCredentialsByUser(targetUserId)
                    dao.anonymizeUser(targetUserId, anonUsername = "deleted-$targetUserId")
                    touched = 1
                }
            }
        }
        return touched
    }

    private fun DeletionRequestEntity.toDomain() = DeletionRequest(
        id = id, targetUserId = targetUserId, requestedBy = requestedBy,
        requestedAt = Instant.ofEpochMilli(requestedAt),
        reason = reason, state = DeletionState.valueOf(state),
        approvedBy = approvedBy,
        approvedAt = approvedAt?.let { Instant.ofEpochMilli(it) },
        executedBy = executedBy,
        executedAt = executedAt?.let { Instant.ofEpochMilli(it) },
        failureReason = failureReason,
        scope = DeletionScope.valueOf(scope)
    )

    private fun DeletionRequest.toEntity() = DeletionRequestEntity(
        id = id, targetUserId = targetUserId, requestedBy = requestedBy,
        requestedAt = requestedAt.toEpochMilli(),
        reason = reason, state = state.name,
        approvedBy = approvedBy, approvedAt = approvedAt?.toEpochMilli(),
        executedBy = executedBy, executedAt = executedAt?.toEpochMilli(),
        failureReason = failureReason, scope = scope.name
    )
}
