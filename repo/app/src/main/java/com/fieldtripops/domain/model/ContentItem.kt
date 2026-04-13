package com.fieldtripops.domain.model

import java.time.Instant
import java.time.LocalDate

data class ContentItem(
    val id: String,
    val title: String,
    val body: String,
    val contentHash: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val state: ContentState,
    val averageRating: Double,
    val ratingCount: Int,
    val favoriteCount: Int,
    val downloadCount: Int
)

enum class ContentState {
    Active, Demoted, Quarantined, Excluded;

    companion object {
        private val TRANSITIONS: Map<ContentState, Set<ContentState>> = mapOf(
            Active to setOf(Demoted, Quarantined),
            Demoted to setOf(Active, Quarantined, Excluded),
            Quarantined to setOf(Active, Excluded),
            Excluded to setOf(Active)
        )

        fun canTransition(from: ContentState, to: ContentState): Boolean =
            TRANSITIONS[from]?.contains(to) ?: false
    }
}

data class ContentMetricDaily(
    val id: String,
    val contentId: String,
    val date: LocalDate,
    val ratingSum: Int,
    val ratingCount: Int,
    val commentCount: Int,
    val favoriteAdds: Int,
    val downloadCount: Int
)

data class ContentRating(
    val id: String,
    val contentId: String,
    val userId: String,
    val stars: Int,
    val createdAt: Instant
)

data class GovernanceDecision(
    val id: String,
    val contentId: String,
    val fromState: ContentState,
    val toState: ContentState,
    val actor: String,
    val reason: String,
    val threshold: String?,
    val decidedAt: Instant
)

data class RecommendationSuppression(
    val id: String,
    val contentId: String,
    val reason: String,
    val establishedAt: Instant,
    val clearedAt: Instant?
)

data class DuplicateCluster(
    val id: String,
    val primaryContentId: String,
    val duplicateContentId: String,
    val similarity: Double,
    val establishedAt: Instant,
    val resolved: Boolean
)

data class TransactionCheckpoint(
    val id: String,
    val label: String,
    val entityType: String,
    val entityId: String,
    val snapshotJson: String,
    val createdAt: Instant,
    val rolledBack: Boolean
)
