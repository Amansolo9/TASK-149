package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "content_items",
    indices = [
        Index(value = ["state", "averageRating"]),
        Index(value = ["contentHash"]),
        Index(value = ["updatedAt"])
    ]
)
data class ContentItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val contentHash: String,
    val createdAt: Long,
    val updatedAt: Long,
    val state: String,
    val averageRating: Double,
    val ratingCount: Int,
    val favoriteCount: Int,
    val downloadCount: Int
)

@Entity(
    tableName = "content_metrics_daily",
    primaryKeys = ["contentId", "dateEpochDay"],
    indices = [Index(value = ["contentId", "dateEpochDay"])]
)
data class ContentMetricDailyEntity(
    val contentId: String,
    val dateEpochDay: Long,
    val ratingSum: Int,
    val ratingCount: Int,
    val commentCount: Int,
    val favoriteAdds: Int,
    val downloadCount: Int
)

@Entity(
    tableName = "content_ratings",
    indices = [
        Index(value = ["contentId", "userId"], unique = true),
        Index(value = ["userId"])
    ]
)
data class ContentRatingEntity(
    @PrimaryKey val id: String,
    val contentId: String,
    val userId: String,
    val stars: Int,
    val createdAt: Long
)

@Entity(
    tableName = "governance_decisions",
    indices = [Index(value = ["contentId", "decidedAt"])]
)
data class GovernanceDecisionEntity(
    @PrimaryKey val id: String,
    val contentId: String,
    val fromState: String,
    val toState: String,
    val actor: String,
    val reason: String,
    val threshold: String?,
    val decidedAt: Long
)

@Entity(
    tableName = "recommendation_suppressions",
    indices = [Index(value = ["contentId"], unique = true)]
)
data class RecommendationSuppressionEntity(
    @PrimaryKey val id: String,
    val contentId: String,
    val reason: String,
    val establishedAt: Long,
    val clearedAt: Long?
)

@Entity(
    tableName = "duplicate_clusters",
    indices = [
        Index(value = ["primaryContentId"]),
        Index(value = ["duplicateContentId"]),
        Index(value = ["primaryContentId", "duplicateContentId"], unique = true)
    ]
)
data class DuplicateClusterEntity(
    @PrimaryKey val id: String,
    val primaryContentId: String,
    val duplicateContentId: String,
    val similarity: Double,
    val establishedAt: Long,
    val resolved: Boolean
)

@Entity(
    tableName = "transaction_checkpoints",
    indices = [
        Index(value = ["entityType", "entityId"]),
        Index(value = ["createdAt"])
    ]
)
data class TransactionCheckpointEntity(
    @PrimaryKey val id: String,
    val label: String,
    val entityType: String,
    val entityId: String,
    val snapshotJson: String,
    val createdAt: Long,
    val rolledBack: Boolean
)
