package com.fieldtripops.domain.reports

import com.fieldtripops.domain.model.BookingState
import com.fieldtripops.domain.model.Responsibility
import com.fieldtripops.domain.model.TicketState
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Offline report definitions per PRD §16.
 */
sealed class ReportRequest {
    data class BookingsByState(val from: LocalDate, val to: LocalDate) : ReportRequest()
    data class ClaimsByType(val from: LocalDate, val to: LocalDate) : ReportRequest()
    data class RefundTotals(val from: LocalDate, val to: LocalDate) : ReportRequest()
    data class GovernanceActions(val from: LocalDate, val to: LocalDate) : ReportRequest()
    object RetentionActivity : ReportRequest()
}

sealed class ReportResult {
    data class BookingsByStateReport(
        val byState: Map<BookingState, Int>,
        val totalBookings: Int,
        val pendingTimeoutRate: Double
    ) : ReportResult()

    data class ClaimsByTypeReport(
        val byClassification: Map<String, Int>,
        val byResponsibility: Map<Responsibility, Int>,
        val withinSlaPercent: Double,
        val averageFirstResponseMinutes: Long
    ) : ReportResult()

    data class RefundTotalsReport(
        val totalRefundedUsd: BigDecimal,
        val refundCount: Int,
        val overrideCount: Int
    ) : ReportResult()

    data class GovernanceActionsReport(
        val demotionCount: Int,
        val quarantineCount: Int,
        val excludedCount: Int,
        val overrideCount: Int
    ) : ReportResult()

    data class RetentionActivityReport(
        val deletionsCount: Int,
        val anonymizationCount: Int,
        val exportsCount: Int,
        val lastSweepAt: Instant?
    ) : ReportResult()
}
