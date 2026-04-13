package com.fieldtripops.data.repository

import androidx.room.withTransaction
import com.fieldtripops.data.dao.ContentItemDao
import com.fieldtripops.data.dao.ContentMetricDao
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.entity.ContentItemEntity
import com.fieldtripops.data.entity.ContentRatingEntity
import com.fieldtripops.domain.model.ContentItem
import com.fieldtripops.domain.model.ContentRating
import com.fieldtripops.domain.model.ContentState
import com.fieldtripops.domain.repository.ContentRepository
import java.time.Instant

class ContentRepositoryImpl(
    private val database: FieldTripDatabase,
    private val contentItemDao: ContentItemDao,
    private val contentMetricDao: ContentMetricDao
) : ContentRepository {

    override suspend fun save(item: ContentItem) {
        contentItemDao.upsert(item.toEntity())
    }

    override suspend fun findById(id: String): ContentItem? =
        contentItemDao.findById(id)?.toDomain()

    override suspend fun findByHash(hash: String): List<ContentItem> =
        contentItemDao.findByHash(hash).map { it.toDomain() }

    override suspend fun getAll(): List<ContentItem> =
        contentItemDao.getAll().map { it.toDomain() }

    override suspend fun findRecommendable(): List<ContentItem> =
        contentItemDao.findRecommendable().map { it.toDomain() }

    override suspend fun updateState(id: String, state: ContentState, at: Instant) {
        contentItemDao.updateState(id, state.name, at.toEpochMilli())
    }

    override suspend fun rate(rating: ContentRating) {
        contentMetricDao.insertRating(
            ContentRatingEntity(
                id = rating.id, contentId = rating.contentId, userId = rating.userId,
                stars = rating.stars, createdAt = rating.createdAt.toEpochMilli()
            )
        )
    }

    override suspend fun recomputeRating(contentId: String, at: Instant) {
        database.withTransaction {
            val avg = contentMetricDao.getAverageRating(contentId) ?: 0.0
            val count = contentMetricDao.getRatingCount(contentId)
            contentItemDao.updateRating(contentId, avg, count, at.toEpochMilli())
        }
    }

    private fun ContentItemEntity.toDomain() = ContentItem(
        id = id, title = title, body = body, contentHash = contentHash,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        state = ContentState.valueOf(state),
        averageRating = averageRating, ratingCount = ratingCount,
        favoriteCount = favoriteCount, downloadCount = downloadCount
    )

    private fun ContentItem.toEntity() = ContentItemEntity(
        id = id, title = title, body = body, contentHash = contentHash,
        createdAt = createdAt.toEpochMilli(), updatedAt = updatedAt.toEpochMilli(),
        state = state.name, averageRating = averageRating, ratingCount = ratingCount,
        favoriteCount = favoriteCount, downloadCount = downloadCount
    )
}
