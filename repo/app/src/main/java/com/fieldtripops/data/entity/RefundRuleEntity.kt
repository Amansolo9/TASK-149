package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted refund rules that drive RefundEngine. Rows are ordered by
 * [minHoursBeforeStartExclusive] descending; the first rule whose
 * `minHoursBeforeStartExclusive < hoursUntilStart` (and whose upper bound is
 * satisfied) is selected. Defaults are seeded on first launch.
 */
@Entity(
    tableName = "refund_rules",
    indices = [
        Index(value = ["active"]),
        Index(value = ["minHoursBeforeStartExclusive"]),
        Index(value = ["code"], unique = true)
    ]
)
data class RefundRuleEntity(
    @PrimaryKey val id: String,
    /** Stable code used as `ruleUsed` in decisions. */
    val code: String,
    /** The rule matches when hoursUntilStart is strictly greater than this. */
    val minHoursBeforeStartExclusive: Int,
    /** The rule matches when hoursUntilStart is less than or equal to this. null = unbounded. */
    val maxHoursBeforeStartInclusive: Int?,
    /** Refund percent, 0–100. */
    val refundPercent: Int,
    val description: String,
    val active: Boolean,
    val updatedAt: Long,
    val updatedBy: String
)

@Entity(tableName = "refund_rule_history")
data class RefundRuleHistoryEntity(
    @PrimaryKey val id: String,
    val ruleId: String,
    val code: String,
    val minHoursBeforeStartExclusive: Int,
    val maxHoursBeforeStartInclusive: Int?,
    val refundPercent: Int,
    val description: String,
    val active: Boolean,
    val updatedAt: Long,
    val updatedBy: String
)
