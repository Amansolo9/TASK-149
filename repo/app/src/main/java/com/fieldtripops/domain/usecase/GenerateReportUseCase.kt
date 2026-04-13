package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.BookingState
import com.fieldtripops.domain.model.ContentState
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.model.TicketState
import com.fieldtripops.domain.reports.ReportRequest
import com.fieldtripops.domain.reports.ReportResult
import com.fieldtripops.domain.repository.AuditRepository
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.repository.ClaimRepository
import com.fieldtripops.domain.repository.GovernanceRepository
import com.fieldtripops.domain.repository.RefundDecisionRepository
import com.fieldtripops.domain.repository.SlaConfigRepository
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reports per PRD §16. SLA percentages are computed against persisted SlaConfig
 * (audit finding #6). Retention reporting reads from real audit events
 * (audit finding #9) instead of returning a stub.
 */
class GenerateReportUseCase(
    private val bookingRepository: BookingRepository,
    private val claimRepository: ClaimRepository,
    private val refundRepository: RefundDecisionRepository,
    private val governanceRepository: GovernanceRepository,
    private val slaConfigRepository: SlaConfigRepository,
    private val auditRepository: AuditRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) {

    suspend fun execute(request: ReportRequest): ReportResult {
        val session = sessionManager.requireSession()
        AccessControl.requireRole(
            session, Role.Reviewer, Role.Administrator,
            operation = "report.view"
        )

        val result = when (request) {
            is ReportRequest.BookingsByState -> bookingsReport(request.from, request.to)
            is ReportRequest.ClaimsByType -> claimsReport(request.from, request.to)
            is ReportRequest.RefundTotals -> refundsReport(request.from, request.to)
            is ReportRequest.GovernanceActions -> governanceReport(request.from, request.to)
            ReportRequest.RetentionActivity -> retentionReport()
        }
        auditLogger.log(
            actor = session.userId, action = AuditAction.REPORT_GENERATED,
            entityType = "Report", entityId = request::class.simpleName ?: "unknown",
            details = result::class.simpleName
        )
        return result
    }

    private suspend fun bookingsReport(
        from: LocalDate, to: LocalDate
    ): ReportResult.BookingsByStateReport {
        val byState = mutableMapOf<BookingState, Int>()
        var total = 0
        for (state in BookingState.values()) {
            val orders = bookingRepository.findByState(state).filter {
                dateInRange(it.createdAt.atZone(ZoneId.systemDefault()).toLocalDate(), from, to)
            }
            byState[state] = orders.size
            total += orders.size
        }
        val pendingClosed = (byState[BookingState.Closed] ?: 0)
        val pendingAll = pendingClosed + (byState[BookingState.Booked] ?: 0)
        val timeoutRate = if (pendingAll > 0) pendingClosed.toDouble() / pendingAll else 0.0
        return ReportResult.BookingsByStateReport(byState, total, timeoutRate)
    }

    private suspend fun claimsReport(
        from: LocalDate, to: LocalDate
    ): ReportResult.ClaimsByTypeReport {
        val sla = slaConfigRepository.get()
        val firstResponseSlaMinutes = sla.firstResponseMinutes.toLong()

        val all = TicketState.values().flatMap { state ->
            claimRepository.findByState(state)
        }.filter {
            dateInRange(it.createdAt.atZone(ZoneId.systemDefault()).toLocalDate(), from, to)
        }

        val byClass = all.groupingBy { it.classification.name }.eachCount()
        val byResp = all.groupingBy { it.responsibility }.eachCount()
        val resolved = all.filter { it.firstResponseAt != null }
        val withinSla = resolved.count {
            val mins = Duration.between(it.createdAt, it.firstResponseAt).toMinutes()
            mins <= firstResponseSlaMinutes
        }
        val pct = if (resolved.isNotEmpty())
            (withinSla.toDouble() / resolved.size * 100.0) else 0.0
        val avgMinutes = if (resolved.isNotEmpty()) {
            resolved.map { Duration.between(it.createdAt, it.firstResponseAt).toMinutes() }
                .average().toLong()
        } else 0L
        return ReportResult.ClaimsByTypeReport(byClass, byResp, pct, avgMinutes)
    }

    private suspend fun refundsReport(
        from: LocalDate, to: LocalDate
    ): ReportResult.RefundTotalsReport {
        val all = refundRepository.getAll().filter {
            dateInRange(it.decidedAt.atZone(ZoneId.systemDefault()).toLocalDate(), from, to)
        }
        val total = all.fold(BigDecimal.ZERO) { acc, d -> acc + d.refundAmount }
        val overrides = all.count { it.manualOverrideReason != null }
        return ReportResult.RefundTotalsReport(total, all.size, overrides)
    }

    private suspend fun governanceReport(
        from: LocalDate, to: LocalDate
    ): ReportResult.GovernanceActionsReport {
        val decisions = governanceRepository.recentDecisions(1000).filter {
            dateInRange(it.decidedAt.atZone(ZoneId.systemDefault()).toLocalDate(), from, to)
        }
        val demote = decisions.count { it.toState == ContentState.Demoted }
        val quar = decisions.count { it.toState == ContentState.Quarantined }
        val excl = decisions.count { it.toState == ContentState.Excluded }
        val overrides = decisions.count { it.threshold == null }
        return ReportResult.GovernanceActionsReport(demote, quar, excl, overrides)
    }

    /**
     * Real retention reporting (audit finding #9). Counts deletion,
     * anonymization, and export events from the audit log, and surfaces the
     * timestamp of the most recent retention sweep so operators can verify
     * the worker is running on schedule.
     */
    private suspend fun retentionReport(): ReportResult.RetentionActivityReport {
        val deletions = auditRepository.findByActions(
            listOf(AuditAction.DATA_DELETED.name), limit = 5000
        ).size
        val anonymizations = auditRepository.findByActions(
            listOf(AuditAction.DATA_ANONYMIZED.name), limit = 5000
        ).size
        val exports = auditRepository.findByActions(
            listOf(AuditAction.EXPORT_CREATED.name), limit = 5000
        ).size
        val lastSweep = auditRepository.findByActions(
            listOf(AuditAction.RETENTION_SWEEP_RUN.name), limit = 1
        ).firstOrNull()?.timestamp
        return ReportResult.RetentionActivityReport(
            deletionsCount = deletions,
            anonymizationCount = anonymizations,
            exportsCount = exports,
            lastSweepAt = lastSweep
        )
    }

    private fun dateInRange(date: LocalDate, from: LocalDate, to: LocalDate): Boolean =
        !date.isBefore(from) && !date.isAfter(to)
}
