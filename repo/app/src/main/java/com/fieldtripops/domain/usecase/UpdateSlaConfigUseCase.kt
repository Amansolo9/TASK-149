package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.repository.SlaConfigRepository
import com.fieldtripops.domain.sla.SlaConfig
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import java.time.Instant

/**
 * Administrator-only update of SLA config per PRD §11/§14. Each successful
 * update writes an audit row and a history row.
 */
class UpdateSlaConfigUseCase(
    private val repository: SlaConfigRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) {

    sealed class Result {
        data class Updated(val config: SlaConfig) : Result()
        data class Invalid(val reason: String) : Result()
    }

    suspend fun execute(
        firstResponseMinutes: Int,
        resolutionMinutes: Int,
        travelerNoResponseHours: Int,
        workDayStartHour: Int = 9,
        workDayEndHour: Int = 17,
        excludeWeekends: Boolean = true
    ): Result {
        val session = sessionManager.requireSession()
        AccessControl.requireAdmin(session, operation = "sla.update")

        if (firstResponseMinutes <= 0) return Result.Invalid("firstResponseMinutes must be > 0")
        if (resolutionMinutes <= 0) return Result.Invalid("resolutionMinutes must be > 0")
        if (travelerNoResponseHours <= 0) return Result.Invalid("travelerNoResponseHours must be > 0")
        if (resolutionMinutes < firstResponseMinutes) {
            return Result.Invalid("resolutionMinutes must be >= firstResponseMinutes")
        }
        if (workDayStartHour < 0 || workDayStartHour > 23) {
            return Result.Invalid("workDayStartHour must be 0–23")
        }
        if (workDayEndHour < 1 || workDayEndHour > 24) {
            return Result.Invalid("workDayEndHour must be 1–24")
        }
        if (workDayEndHour <= workDayStartHour) {
            return Result.Invalid("workDayEndHour must be > workDayStartHour")
        }

        val previous = repository.get()
        val updated = SlaConfig(
            firstResponseMinutes = firstResponseMinutes,
            resolutionMinutes = resolutionMinutes,
            travelerNoResponseHours = travelerNoResponseHours,
            updatedAt = Instant.now(),
            updatedBy = session.userId,
            workDayStartHour = workDayStartHour,
            workDayEndHour = workDayEndHour,
            excludeWeekends = excludeWeekends
        )
        repository.save(updated)

        auditLogger.log(
            actor = session.userId,
            action = AuditAction.SLA_CHANGED,
            entityType = "SlaConfig",
            entityId = "current",
            details = "first=${previous.firstResponseMinutes}->${updated.firstResponseMinutes}min, " +
                "resolve=${previous.resolutionMinutes}->${updated.resolutionMinutes}min, " +
                "noResp=${previous.travelerNoResponseHours}->${updated.travelerNoResponseHours}h, " +
                "workHours=${updated.workDayStartHour}-${updated.workDayEndHour}, " +
                "excludeWeekends=${updated.excludeWeekends}, " +
                "by=${session.displayName}"
        )

        return Result.Updated(updated)
    }
}
