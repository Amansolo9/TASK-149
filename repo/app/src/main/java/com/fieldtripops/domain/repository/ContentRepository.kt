package com.fieldtripops.domain.repository

import com.fieldtripops.domain.model.ContentItem
import com.fieldtripops.domain.model.ContentRating
import com.fieldtripops.domain.model.ContentState
import com.fieldtripops.domain.model.DuplicateCluster
import com.fieldtripops.domain.model.GovernanceDecision
import com.fieldtripops.domain.model.RecommendationSuppression
import java.time.Instant

interface ContentRepository {
    suspend fun save(item: ContentItem)
    suspend fun findById(id: String): ContentItem?
    suspend fun findByHash(hash: String): List<ContentItem>
    suspend fun getAll(): List<ContentItem>
    suspend fun findRecommendable(): List<ContentItem>
    suspend fun updateState(id: String, state: ContentState, at: Instant)
    suspend fun rate(rating: ContentRating)
    suspend fun recomputeRating(contentId: String, at: Instant)
}

interface GovernanceRepository {
    suspend fun recordDecision(decision: GovernanceDecision)
    suspend fun decisionsForContent(contentId: String): List<GovernanceDecision>
    suspend fun recentDecisions(limit: Int = 50): List<GovernanceDecision>

    suspend fun establishSuppression(suppression: RecommendationSuppression)
    suspend fun clearSuppression(contentId: String, at: Instant)
    suspend fun isSuppressed(contentId: String): Boolean

    suspend fun recordDuplicate(cluster: DuplicateCluster)
    suspend fun getUnresolvedDuplicates(): List<DuplicateCluster>
    suspend fun getDuplicatesForContent(contentId: String): List<DuplicateCluster>
    suspend fun markDuplicateResolved(clusterId: String)
}

interface CheckpointRepository {
    suspend fun createCheckpoint(
        label: String, entityType: String, entityId: String, snapshotJson: String
    ): com.fieldtripops.domain.model.TransactionCheckpoint
    suspend fun findLastValid(entityType: String, entityId: String): com.fieldtripops.domain.model.TransactionCheckpoint?
    suspend fun markRolledBack(checkpointId: String)
    suspend fun getHistory(entityType: String, entityId: String): List<com.fieldtripops.domain.model.TransactionCheckpoint>
}
