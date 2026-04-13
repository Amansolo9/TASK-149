package com.fieldtripops.domain.usecase

import androidx.room.withTransaction
import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.domain.governance.GovernanceEvaluator
import com.fieldtripops.domain.model.ContentRating
import com.fieldtripops.domain.model.ContentState
import com.fieldtripops.domain.model.GovernanceDecision
import com.fieldtripops.domain.model.RecommendationSuppression
import com.fieldtripops.domain.repository.CheckpointRepository
import com.fieldtripops.domain.repository.ContentRepository
import com.fieldtripops.domain.repository.GovernanceRepository
import java.time.Instant
import java.util.UUID

class RateContentUseCase(
    private val database: FieldTripDatabase,
    private val contentRepository: ContentRepository,
    private val governanceRepository: GovernanceRepository,
    private val auditLogger: AuditLogger,
    private val checkpointRepository: CheckpointRepository
) {

    sealed class Result {
        data class Rated(val demoted: Boolean) : Result()
        object ContentNotFound : Result()
        object InvalidStars : Result()
    }

    suspend fun execute(contentId: String, userId: String, stars: Int): Result {
        if (stars < 1 || stars > 5) return Result.InvalidStars
        val content = contentRepository.findById(contentId) ?: return Result.ContentNotFound

        val now = Instant.now()
        val rating = ContentRating(
            id = UUID.randomUUID().toString(),
            contentId = contentId, userId = userId, stars = stars, createdAt = now
        )

        var demoted = false
        database.withTransaction {
            contentRepository.rate(rating)
            contentRepository.recomputeRating(contentId, now)
            val refreshed = contentRepository.findById(contentId) ?: return@withTransaction

            val decision = GovernanceEvaluator.evaluate(refreshed)
            if (decision is GovernanceEvaluator.Decision.Demote) {
                val fromState = refreshed.state
                // Checkpoint before demotion so rollback can restore
                checkpointRepository.createCheckpoint(
                    label = "pre-demotion",
                    entityType = "ContentItem",
                    entityId = contentId,
                    snapshotJson = """{"state":"${fromState.name}","averageRating":${refreshed.averageRating},"ratingCount":${refreshed.ratingCount}}"""
                )
                contentRepository.updateState(contentId, ContentState.Demoted, now)
                governanceRepository.recordDecision(
                    GovernanceDecision(
                        id = UUID.randomUUID().toString(),
                        contentId = contentId, fromState = fromState,
                        toState = ContentState.Demoted, actor = "system",
                        reason = decision.reason,
                        threshold = "avg<2.5 with n>=10",
                        decidedAt = now
                    )
                )
                governanceRepository.establishSuppression(
                    RecommendationSuppression(
                        id = UUID.randomUUID().toString(),
                        contentId = contentId, reason = decision.reason,
                        establishedAt = now, clearedAt = null
                    )
                )
                demoted = true
            }
        }

        auditLogger.log(
            actor = userId, action = AuditAction.CONTENT_RATED,
            entityType = "ContentItem", entityId = contentId,
            details = "$stars stars" + if (demoted) "; auto-demoted" else ""
        )
        if (demoted) {
            auditLogger.log(
                actor = "system", action = AuditAction.CONTENT_DEMOTED,
                entityType = "ContentItem", entityId = contentId,
                details = "Auto-demoted by rating threshold"
            )
        }
        return Result.Rated(demoted)
    }
}
