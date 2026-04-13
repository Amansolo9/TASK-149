package com.fieldtripops.data.repository

import androidx.room.withTransaction
import com.fieldtripops.data.dao.RefundRuleDao
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.entity.RefundRuleEntity
import com.fieldtripops.data.entity.RefundRuleHistoryEntity
import com.fieldtripops.domain.model.RefundRule
import com.fieldtripops.domain.repository.RefundRuleRepository
import java.time.Instant
import java.util.UUID

class RefundRuleRepositoryImpl(
    private val database: FieldTripDatabase,
    private val dao: RefundRuleDao
) : RefundRuleRepository {

    override suspend fun listActive(): List<RefundRule> =
        dao.listActive().map { it.toDomain() }

    override suspend fun listAll(): List<RefundRule> =
        dao.listAll().map { it.toDomain() }

    override suspend fun findByCode(code: String): RefundRule? =
        dao.findByCode(code)?.toDomain()

    override suspend fun upsert(rule: RefundRule) {
        database.withTransaction {
            dao.upsert(rule.toEntity())
            dao.insertHistory(
                RefundRuleHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    ruleId = rule.id,
                    code = rule.code,
                    minHoursBeforeStartExclusive = rule.minHoursBeforeStartExclusive,
                    maxHoursBeforeStartInclusive = rule.maxHoursBeforeStartInclusive,
                    refundPercent = rule.refundPercent,
                    description = rule.description,
                    active = rule.active,
                    updatedAt = rule.updatedAt.toEpochMilli(),
                    updatedBy = rule.updatedBy
                )
            )
        }
    }

    override suspend fun seedDefaultsIfEmpty() {
        if (dao.count() > 0) return
        val now = Instant.now().toEpochMilli()
        val seed = listOf(
            RefundRuleEntity(
                id = UUID.randomUUID().toString(),
                code = RefundRule.CODE_FULL,
                minHoursBeforeStartExclusive = 48,
                maxHoursBeforeStartInclusive = null,
                refundPercent = 100,
                description = "Cancel more than 48 hours before start — full refund.",
                active = true, updatedAt = now, updatedBy = "system"
            ),
            RefundRuleEntity(
                id = UUID.randomUUID().toString(),
                code = RefundRule.CODE_PARTIAL,
                minHoursBeforeStartExclusive = 24,
                maxHoursBeforeStartInclusive = 48,
                refundPercent = 50,
                description = "Cancel 24–48 hours before start — partial refund.",
                active = true, updatedAt = now, updatedBy = "system"
            ),
            RefundRuleEntity(
                id = UUID.randomUUID().toString(),
                code = RefundRule.CODE_NONE,
                minHoursBeforeStartExclusive = -1,
                maxHoursBeforeStartInclusive = 24,
                refundPercent = 0,
                description = "Cancel under 24 hours — non-refundable.",
                active = true, updatedAt = now, updatedBy = "system"
            )
        )
        database.withTransaction {
            seed.forEach { dao.upsert(it) }
        }
    }

    private fun RefundRuleEntity.toDomain() = RefundRule(
        id = id, code = code,
        minHoursBeforeStartExclusive = minHoursBeforeStartExclusive,
        maxHoursBeforeStartInclusive = maxHoursBeforeStartInclusive,
        refundPercent = refundPercent,
        description = description,
        active = active,
        updatedAt = Instant.ofEpochMilli(updatedAt),
        updatedBy = updatedBy
    )

    private fun RefundRule.toEntity() = RefundRuleEntity(
        id = id, code = code,
        minHoursBeforeStartExclusive = minHoursBeforeStartExclusive,
        maxHoursBeforeStartInclusive = maxHoursBeforeStartInclusive,
        refundPercent = refundPercent,
        description = description,
        active = active,
        updatedAt = updatedAt.toEpochMilli(),
        updatedBy = updatedBy
    )
}
