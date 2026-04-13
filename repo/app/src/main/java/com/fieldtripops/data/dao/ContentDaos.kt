package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fieldtripops.data.entity.ContentItemEntity
import com.fieldtripops.data.entity.ContentMetricDailyEntity
import com.fieldtripops.data.entity.ContentRatingEntity
import com.fieldtripops.data.entity.DuplicateClusterEntity
import com.fieldtripops.data.entity.GovernanceDecisionEntity
import com.fieldtripops.data.entity.RecommendationSuppressionEntity
import com.fieldtripops.data.entity.TransactionCheckpointEntity

@Dao
interface ContentItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ContentItemEntity)

    @Query("SELECT * FROM content_items WHERE id = :id")
    suspend fun findById(id: String): ContentItemEntity?

    @Query("SELECT * FROM content_items WHERE contentHash = :hash")
    suspend fun findByHash(hash: String): List<ContentItemEntity>

    @Query("SELECT * FROM content_items WHERE state = :state")
    suspend fun findByState(state: String): List<ContentItemEntity>

    @Query("SELECT * FROM content_items")
    suspend fun getAll(): List<ContentItemEntity>

    @Query(
        """SELECT * FROM content_items
           WHERE state IN ('Active', 'Demoted')
           AND id NOT IN (SELECT contentId FROM recommendation_suppressions WHERE clearedAt IS NULL)"""
    )
    suspend fun findRecommendable(): List<ContentItemEntity>

    @Query(
        """UPDATE content_items SET state = :state, updatedAt = :at WHERE id = :id"""
    )
    suspend fun updateState(id: String, state: String, at: Long)

    @Query(
        """UPDATE content_items SET averageRating = :avg, ratingCount = :count, updatedAt = :at WHERE id = :id"""
    )
    suspend fun updateRating(id: String, avg: Double, count: Int, at: Long)
}

@Dao
interface ContentMetricDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metric: ContentMetricDailyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRating(rating: ContentRatingEntity)

    @Query("SELECT * FROM content_metrics_daily WHERE contentId = :id ORDER BY dateEpochDay ASC")
    suspend fun getByContent(id: String): List<ContentMetricDailyEntity>

    @Query("SELECT * FROM content_ratings WHERE contentId = :id")
    suspend fun getRatings(id: String): List<ContentRatingEntity>

    @Query("SELECT AVG(stars) FROM content_ratings WHERE contentId = :id")
    suspend fun getAverageRating(id: String): Double?

    @Query("SELECT COUNT(*) FROM content_ratings WHERE contentId = :id")
    suspend fun getRatingCount(id: String): Int
}

@Dao
interface GovernanceDecisionDao {
    @Insert
    suspend fun insert(decision: GovernanceDecisionEntity)

    @Query("SELECT * FROM governance_decisions WHERE contentId = :id ORDER BY decidedAt DESC")
    suspend fun getByContent(id: String): List<GovernanceDecisionEntity>

    @Query("SELECT * FROM governance_decisions ORDER BY decidedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<GovernanceDecisionEntity>
}

@Dao
interface RecommendationSuppressionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(suppression: RecommendationSuppressionEntity)

    @Query("SELECT * FROM recommendation_suppressions WHERE contentId = :id AND clearedAt IS NULL")
    suspend fun findActive(id: String): RecommendationSuppressionEntity?

    @Query("UPDATE recommendation_suppressions SET clearedAt = :at WHERE contentId = :id AND clearedAt IS NULL")
    suspend fun clear(id: String, at: Long)

    @Query("SELECT * FROM recommendation_suppressions")
    suspend fun getAll(): List<RecommendationSuppressionEntity>
}

@Dao
interface DuplicateClusterDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(cluster: DuplicateClusterEntity)

    @Query("SELECT * FROM duplicate_clusters WHERE primaryContentId = :id OR duplicateContentId = :id")
    suspend fun findForContent(id: String): List<DuplicateClusterEntity>

    @Query("SELECT * FROM duplicate_clusters WHERE resolved = 0")
    suspend fun getUnresolved(): List<DuplicateClusterEntity>

    @Query("UPDATE duplicate_clusters SET resolved = 1 WHERE id = :id")
    suspend fun markResolved(id: String)
}

@Dao
interface TransactionCheckpointDao {
    @Insert
    suspend fun insert(checkpoint: TransactionCheckpointEntity)

    @Query("SELECT * FROM transaction_checkpoints WHERE entityType = :type AND entityId = :id ORDER BY createdAt DESC")
    suspend fun getByEntity(type: String, id: String): List<TransactionCheckpointEntity>

    @Query("SELECT * FROM transaction_checkpoints WHERE entityType = :type AND entityId = :id AND rolledBack = 0 ORDER BY createdAt DESC LIMIT 1")
    suspend fun findLastValid(type: String, id: String): TransactionCheckpointEntity?

    @Query("UPDATE transaction_checkpoints SET rolledBack = 1 WHERE id = :id")
    suspend fun markRolledBack(id: String)
}
