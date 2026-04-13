package com.fieldtripops.domain.sla

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Computes elapsed business-hour durations and breach deadlines
 * relative to a configurable working-hours window.
 *
 * When [config] has `excludeWeekends = false` and a 0–24 work window,
 * this degrades to simple elapsed-time calculation, preserving backward
 * compatibility with the previous elapsed-only model.
 *
 * All calculations use the device's default time zone (offline app,
 * single-device, no server). Holiday exclusions are not modeled —
 * only weekend exclusion is supported.
 */
object BusinessHourCalculator {

    /**
     * Returns the number of elapsed business-hour minutes between [from] and [to].
     * If config specifies no weekend exclusion and a full 24-hour work window,
     * this is equivalent to `Duration.between(from, to).toMinutes()`.
     */
    fun elapsedBusinessMinutes(from: Instant, to: Instant, config: SlaConfig): Long {
        if (!config.excludeWeekends && config.workDayStartHour == 0 && config.workDayEndHour == 24) {
            return Duration.between(from, to).toMinutes()
        }

        val zone = ZoneId.systemDefault()
        var cursor = LocalDateTime.ofInstant(from, zone)
        val end = LocalDateTime.ofInstant(to, zone)
        var totalMinutes = 0L

        while (cursor.isBefore(end)) {
            if (isWorkingTime(cursor, config)) {
                totalMinutes++
            }
            cursor = cursor.plusMinutes(1)
        }
        return totalMinutes
    }

    /**
     * Given a [start] instant and a desired [businessMinutes] SLA window,
     * returns the absolute [Instant] at which the SLA breaches.
     *
     * Advances through calendar time minute-by-minute, counting only
     * minutes that fall within business hours. When the count reaches
     * [businessMinutes], the current wall-clock instant is the breach point.
     */
    fun breachInstant(start: Instant, businessMinutes: Long, config: SlaConfig): Instant {
        if (!config.excludeWeekends && config.workDayStartHour == 0 && config.workDayEndHour == 24) {
            return start.plus(Duration.ofMinutes(businessMinutes))
        }

        val zone = ZoneId.systemDefault()
        var cursor = LocalDateTime.ofInstant(start, zone)
        var remaining = businessMinutes

        while (remaining > 0) {
            cursor = cursor.plusMinutes(1)
            if (isWorkingTime(cursor, config)) {
                remaining--
            }
        }
        return cursor.atZone(zone).toInstant()
    }

    private fun isWorkingTime(dt: LocalDateTime, config: SlaConfig): Boolean {
        if (config.excludeWeekends) {
            val dow = dt.dayOfWeek
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return false
        }
        val hour = dt.hour
        return hour >= config.workDayStartHour && hour < config.workDayEndHour
    }
}
