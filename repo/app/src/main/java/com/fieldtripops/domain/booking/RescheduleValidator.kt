package com.fieldtripops.domain.booking

import com.fieldtripops.domain.model.Role
import java.time.Duration
import java.time.Instant

/**
 * Validates reschedule requests per PRD §9.3:
 * - Must be filed at least 24 hours before start time
 * - Under 24 hours: only Agent may proceed with mandatory exception reason
 */
object RescheduleValidator {

    val MIN_LEAD_TIME: Duration = Duration.ofHours(24)

    sealed class Result {
        object Valid : Result()
        data class RequiresException(val requiredReason: Boolean = true) : Result()
        data class Invalid(val errors: List<String>) : Result()
    }

    fun validate(
        tripStart: Instant,
        now: Instant,
        requesterRoles: List<Role>,
        exceptionReason: String?
    ): Result {
        val timeToStart = Duration.between(now, tripStart)

        if (timeToStart.isNegative) {
            return Result.Invalid(listOf("Cannot reschedule after trip start"))
        }

        val underLeadTime = timeToStart < MIN_LEAD_TIME

        if (!underLeadTime) return Result.Valid

        // Under 24h — only Agents may proceed with exception reason
        val isAgent = requesterRoles.contains(Role.Agent) || requesterRoles.contains(Role.Administrator)
        if (!isAgent) {
            return Result.Invalid(
                listOf("Reschedule must be made at least 24 hours before trip start")
            )
        }

        if (exceptionReason.isNullOrBlank()) {
            return Result.RequiresException(requiredReason = true)
        }

        return Result.Valid
    }
}
