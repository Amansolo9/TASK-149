package com.fieldtripops.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton row table; the single row uses key "current". History rows are
 * tracked via the audit log (action = SLA_CHANGED) and via the dedicated
 * sla_config_history table for time-series reporting.
 */
@Entity(tableName = "sla_config")
data class SlaConfigEntity(
    @PrimaryKey val key: String,
    val firstResponseMinutes: Int,
    val resolutionMinutes: Int,
    val travelerNoResponseHours: Int,
    val updatedAt: Long,
    val updatedBy: String,
    @ColumnInfo(defaultValue = "9") val workDayStartHour: Int = 9,
    @ColumnInfo(defaultValue = "17") val workDayEndHour: Int = 17,
    @ColumnInfo(defaultValue = "1") val excludeWeekends: Int = 1
)

@Entity(tableName = "sla_config_history")
data class SlaConfigHistoryEntity(
    @PrimaryKey val id: String,
    val firstResponseMinutes: Int,
    val resolutionMinutes: Int,
    val travelerNoResponseHours: Int,
    val updatedAt: Long,
    val updatedBy: String,
    @ColumnInfo(defaultValue = "9") val workDayStartHour: Int = 9,
    @ColumnInfo(defaultValue = "17") val workDayEndHour: Int = 17,
    @ColumnInfo(defaultValue = "1") val excludeWeekends: Int = 1
)
