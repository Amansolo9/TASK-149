package com.fieldtripops.domain.usecase

import androidx.room.withTransaction
import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.domain.governance.GovernanceEvaluator
import com.fieldtripops.domain.governance.SimilarityCalculator
import com.fieldtripops.domain.model.ContentState
import com.fieldtripops.domain.model.DuplicateCluster
import com.fieldtripops.domain.model.GovernanceDecision
import com.fieldtripops.domain.model.RecommendationSuppression
import com.fieldtripops.domain.repository.CheckpointRepository
import com.fieldtripops.domain.repository.ContentRepository
import com.fieldtripops.domain.repository.GovernanceRepository
import java.time.Instant
import java.util.UUID

/**
 * Scans content for duplicates using hash equality + Jaccard similarity.
 * Quarantines items with similarity > 80% per PRD §9.8.
 */
class RunDuplicateScanUseCase(
    private val database: FieldTripDatabase,
    private val contentRepository: ContentRepository,
    private val governanceRepository: GovernanceRepository,
    private val auditLogger: AuditLogger,
    private val checkpointRepository: CheckpointRepository
) {
    data class Result(val duplicatesFound: Int, val quarantined: Int)

    suspend fun execute(): Result {
        val all = contentRepository.getAll()
            .filter { it.state != ContentState.Quarantined && it.state != ContentState.Excluded }
        var duplicates = 0
        var quarantined = 0
        val now = Instant.now()

        for (i in all.indices) {
            for (j in i + 1 until all.size) {
                val a = all[i]; val b = all[j]
                val similarity = when {
                    a.contentHash == b.contentHash -> 1.0
                    else -> SimilarityCalculator.similarity(a.body, b.body)
                }
                if (GovernanceEvaluator.evaluateDuplicate(similarity)) {
                    duplicates++
                    database.withTransaction {
                        governanceRepository.recordDuplicate(
                            DuplicateCluster(
                                id = UUID.randomUUID().toString(),
                                primaryContentId = a.id, duplicateContentId = b.id,
                                similarity = similarity, establishedAt = now, resolved = false
                            )
                        )
                        // Quarantine the newer item (b)
                        if (b.state != ContentState.Quarantined) {
                            // Checkpoint before quarantine so rollback can restore
                            checkpointRepository.createCheckpoint(
                                label = "pre-quarantine",
                                entityType = "ContentItem",
                                entityId = b.id,
                                snapshotJson = """{"state":"${b.state.name}","averageRating":${b.averageRating},"ratingCount":${b.ratingCount}}"""
                            )
                            contentRepository.updateState(b.id, ContentState.Quarantined, now)
                            governanceRepository.recordDecision(
                                GovernanceDecision(
                                    id = UUID.randomUUID().toString(),
                                    contentId = b.id, fromState = b.state,
                                    toState = ContentState.Quarantined, actor = "system",
                                    reason = "Similarity ${"%.2f".format(similarity)} with ${a.id}",
                                    threshold = "similarity>0.80",
                                    decidedAt = now
                                )
                            )
                            governanceRepository.establishSuppression(
                                RecommendationSuppression(
                                    id = UUID.randomUUID().toString(),
                                    contentId = b.id, reason = "Duplicate of ${a.id}",
                                    establishedAt = now, clearedAt = null
                                )
                            )
                            quarantined++
                        }
                    }
                    auditLogger.log(
                        actor = "system", action = AuditAction.DUPLICATE_DETECTED,
                        entityType = "ContentItem", entityId = b.id,
                        details = "Similarity ${"%.2f".format(similarity)} with ${a.id}"
                    )
                }
            }
        }
        return Result(duplicates, quarantined)
    }
}
