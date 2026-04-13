package com.fieldtripops.domain.sla

import java.time.Instant

/**
 * Persisted, administrator-editable SLA configuration. There is exactly one
 * row (key = "current") in the `sla_config` table; updates produce a new
 * version-stamped row in `sla_config_history` so prior values are auditable.
 *
 * **Timing model: configurable business hours.**
 * SLA windows are expressed in minutes. The [BusinessHourCalculator] computes
 * breach deadlines using the configured working-hours window and weekend
 * exclusion. When [excludeWeekends] is false and the work window spans 0–24,
 * computation degrades to simple elapsed wall-clock time for backward
 * compatibility.
 *
 * Default values:
 *  - first response: 240 minutes (4 business hours)
 *  - resolution: 4320 minutes (3 business days)
 *  - traveler no-response auto close: 72 hours elapsed (always wall-clock)
 *  - work day: 09:00–17:00 (8 business hours per day)
 *  - weekends excluded: true
 */
data class SlaConfig(
    val firstResponseMinutes: Int,
    val resolutionMinutes: Int,
    val travelerNoResponseHours: Int,
    val updatedAt: Instant,
    val updatedBy: String,
    /** Start of the business day (inclusive), 0–23. Default 9. */
    val workDayStartHour: Int = 9,
    /** End of the business day (exclusive), 1–24. Default 17. */
    val workDayEndHour: Int = 17,
    /** Whether to exclude Saturday/Sunday from SLA computation. Default true. */
    val excludeWeekends: Boolean = true
) {
    companion object {
        val DEFAULT = SlaConfig(
            firstResponseMinutes = 4 * 60,
            resolutionMinutes = 3 * 24 * 60,
            travelerNoResponseHours = 72,
            updatedAt = Instant.EPOCH,
            updatedBy = "system",
            workDayStartHour = 9,
            workDayEndHour = 17,
            excludeWeekends = true
        )
    }
}
