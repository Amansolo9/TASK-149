package com.fieldtripops.domain.usecase

import androidx.room.withTransaction
import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.domain.model.TransactionCheckpoint
import com.fieldtripops.domain.repository.CheckpointRepository

/**
 * Restores the last valid transaction for an entity via compensating writes.
 * Does not remove audit history per PRD §9.9.
 *
 * The caller provides a `restorer` lambda that applies the snapshot.
 * This keeps rollback logic domain-agnostic.
 */
class RollbackUseCase(
    private val database: FieldTripDatabase,
    private val checkpointRepository: CheckpointRepository,
    private val auditLogger: AuditLogger
) {

    sealed class Result {
        data class RolledBack(val checkpoint: TransactionCheckpoint) : Result()
        object NoCheckpointFound : Result()
    }

    /**
     * @param restorer synchronous function that applies the snapshot JSON within the same
     *                 transaction to restore entity state.
     */
    suspend fun execute(
        entityType: String, entityId: String, actor: String,
        restorer: suspend (snapshotJson: String) -> Unit
    ): Result {
        val last = checkpointRepository.findLastValid(entityType, entityId)
            ?: return Result.NoCheckpointFound

        database.withTransaction {
            restorer(last.snapshotJson)
            checkpointRepository.markRolledBack(last.id)
        }

        auditLogger.log(
            actor = actor, action = AuditAction.ROLLBACK_EXECUTED,
            entityType = entityType, entityId = entityId,
            details = "Rolled back to checkpoint ${last.id} (${last.label})"
        )
        return Result.RolledBack(last)
    }
}
