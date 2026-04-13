package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.RefundRule
import com.fieldtripops.domain.repository.RefundRuleRepository
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import java.time.Instant
import java.util.UUID

/**
 * Administrator-only editing of refund rules. Non-admin callers are rejected.
 * Every update appends a row to refund_rule_history and writes an audit event.
 */
class UpdateRefundRuleUseCase(
    private val repository: RefundRuleRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) {
    sealed class Result {
        data class Saved(val rule: RefundRule) : Result()
        data class Invalid(val reason: String) : Result()
    }

    suspend fun upsert(
        code: String,
        minHoursBeforeStartExclusive: Int,
        maxHoursBeforeStartInclusive: Int?,
        refundPercent: Int,
        description: String,
        active: Boolean
    ): Result {
        val session = sessionManager.requireSession()
        AccessControl.requireAdmin(session, operation = "refund.rule.update")

        if (code.isBlank()) return Result.Invalid("code required")
        if (refundPercent !in 0..100) return Result.Invalid("refundPercent must be 0..100")
        if (maxHoursBeforeStartInclusive != null &&
            maxHoursBeforeStartInclusive < minHoursBeforeStartExclusive) {
            return Result.Invalid("max must be >= min")
        }

        val existing = repository.findByCode(code)
        val now = Instant.now()
        val rule = RefundRule(
            id = existing?.id ?: UUID.randomUUID().toString(),
            code = code,
            minHoursBeforeStartExclusive = minHoursBeforeStartExclusive,
            maxHoursBeforeStartInclusive = maxHoursBeforeStartInclusive,
            refundPercent = refundPercent,
            description = description,
            active = active,
            updatedAt = now,
            updatedBy = session.userId
        )
        repository.upsert(rule)

        val action = if (existing == null) AuditAction.REFUND_RULE_CREATED
                     else AuditAction.REFUND_RULE_UPDATED
        auditLogger.log(
            session.userId, action, "RefundRule", rule.id,
            "code=$code, pct=$refundPercent, min=$minHoursBeforeStartExclusive, " +
                "max=${maxHoursBeforeStartInclusive ?: "∞"}, active=$active"
        )
        return Result.Saved(rule)
    }
}
