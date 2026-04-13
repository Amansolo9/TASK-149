package com.fieldtripops.data.repository

import com.fieldtripops.data.dao.DuplicateClusterDao
import com.fieldtripops.data.dao.GovernanceDecisionDao
import com.fieldtripops.data.dao.RecommendationSuppressionDao
import com.fieldtripops.data.entity.DuplicateClusterEntity
import com.fieldtripops.data.entity.GovernanceDecisionEntity
import com.fieldtripops.data.entity.RecommendationSuppressionEntity
import com.fieldtripops.domain.model.ContentState
import com.fieldtripops.domain.model.DuplicateCluster
import com.fieldtripops.domain.model.GovernanceDecision
import com.fieldtripops.domain.model.RecommendationSuppression
import com.fieldtripops.domain.repository.GovernanceRepository
import java.time.Instant

class GovernanceRepositoryImpl(
    private val decisionDao: GovernanceDecisionDao,
    private val suppressionDao: RecommendationSuppressionDao,
    private val clusterDao: DuplicateClusterDao
) : GovernanceRepository {

    override suspend fun recordDecision(decision: GovernanceDecision) {
        decisionDao.insert(decision.toEntity())
    }

    override suspend fun decisionsForContent(contentId: String): List<GovernanceDecision> =
        decisionDao.getByContent(contentId).map { it.toDomain() }

    override suspend fun recentDecisions(limit: Int): List<GovernanceDecision> =
        decisionDao.recent(limit).map { it.toDomain() }

    override suspend fun establishSuppression(suppression: RecommendationSuppression) {
        suppressionDao.upsert(suppression.toEntity())
    }

    override suspend fun clearSuppression(contentId: String, at: Instant) {
        suppressionDao.clear(contentId, at.toEpochMilli())
    }

    override suspend fun isSuppressed(contentId: String): Boolean =
        suppressionDao.findActive(contentId) != null

    override suspend fun recordDuplicate(cluster: DuplicateCluster) {
        clusterDao.insert(cluster.toEntity())
    }

    override suspend fun getUnresolvedDuplicates(): List<DuplicateCluster> =
        clusterDao.getUnresolved().map { it.toDomain() }

    override suspend fun getDuplicatesForContent(contentId: String): List<DuplicateCluster> =
        clusterDao.findForContent(contentId).map { it.toDomain() }

    override suspend fun markDuplicateResolved(clusterId: String) {
        clusterDao.markResolved(clusterId)
    }

    private fun GovernanceDecisionEntity.toDomain() = GovernanceDecision(
        id = id, contentId = contentId,
        fromState = ContentState.valueOf(fromState),
        toState = ContentState.valueOf(toState),
        actor = actor, reason = reason, threshold = threshold,
        decidedAt = Instant.ofEpochMilli(decidedAt)
    )

    private fun GovernanceDecision.toEntity() = GovernanceDecisionEntity(
        id = id, contentId = contentId,
        fromState = fromState.name, toState = toState.name,
        actor = actor, reason = reason, threshold = threshold,
        decidedAt = decidedAt.toEpochMilli()
    )

    private fun RecommendationSuppressionEntity.toDomain() = RecommendationSuppression(
        id = id, contentId = contentId, reason = reason,
        establishedAt = Instant.ofEpochMilli(establishedAt),
        clearedAt = clearedAt?.let { Instant.ofEpochMilli(it) }
    )

    private fun RecommendationSuppression.toEntity() = RecommendationSuppressionEntity(
        id = id, contentId = contentId, reason = reason,
        establishedAt = establishedAt.toEpochMilli(),
        clearedAt = clearedAt?.toEpochMilli()
    )

    private fun DuplicateClusterEntity.toDomain() = DuplicateCluster(
        id = id, primaryContentId = primaryContentId,
        duplicateContentId = duplicateContentId, similarity = similarity,
        establishedAt = Instant.ofEpochMilli(establishedAt), resolved = resolved
    )

    private fun DuplicateCluster.toEntity() = DuplicateClusterEntity(
        id = id, primaryContentId = primaryContentId,
        duplicateContentId = duplicateContentId, similarity = similarity,
        establishedAt = establishedAt.toEpochMilli(), resolved = resolved
    )
}
