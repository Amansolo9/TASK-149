package com.fieldtripops.ui.reports

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtripops.domain.reports.ReportRequest
import com.fieldtripops.domain.reports.ReportResult
import com.fieldtripops.domain.usecase.GenerateExportUseCase
import com.fieldtripops.domain.usecase.GenerateReportUseCase
import com.fieldtripops.security.auth.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Keeps report/export heavy work off the main thread (audit finding #9).
 *
 * Report and export generation each involve multiple DAO calls, CSV string
 * building, SHA-256 hashing, and file IO. We run them on [ioDispatcher]
 * (Dispatchers.IO by default) and only hand the small result string back to
 * LiveData on the main thread — LiveData.setValue is invoked after
 * `withContext(ioDispatcher)` returns, so the publication already happens on
 * the dispatcher that called `launch` (which defaults to
 * `Dispatchers.Main.immediate` via `viewModelScope`).
 *
 * The dispatcher is constructor-injected so tests can pass a test dispatcher.
 */
class ReportsViewModel(
    private val generateReportUseCase: GenerateReportUseCase,
    private val generateExportUseCase: GenerateExportUseCase,
    private val sessionManager: SessionManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _output = MutableLiveData<String>("")
    val output: LiveData<String> = _output

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    fun runReport(request: ReportRequest) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val formatted = withContext(ioDispatcher) {
                    val r = generateReportUseCase.execute(request)
                    formatResult(r)
                }
                _output.value = formatted
            } catch (e: SecurityException) {
                _output.value = "Not authorized: ${e.message}"
            } catch (t: Throwable) {
                _output.value = "Failed: ${t.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun exportSelf() {
        val session = sessionManager.current() ?: return
        viewModelScope.launch {
            _loading.value = true
            try {
                val summary = withContext(ioDispatcher) {
                    when (val r = generateExportUseCase.execute(session.userId)) {
                        is GenerateExportUseCase.Result.Generated ->
                            "Exported ${r.pkg.rowCount} rows to ${r.pkg.filePath}\n" +
                                "checksum=${r.pkg.checksum}\n" +
                                "profile=${r.pkg.maskingProfile}"
                        is GenerateExportUseCase.Result.UserNotFound -> "User not found"
                    }
                }
                _output.value = summary
            } catch (e: SecurityException) {
                _output.value = "Not authorized: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun reportRequestForLast90Days(kind: ReportKind): ReportRequest {
        val to = LocalDate.now()
        val from = to.minusDays(90)
        return when (kind) {
            ReportKind.Bookings -> ReportRequest.BookingsByState(from, to)
            ReportKind.Claims -> ReportRequest.ClaimsByType(from, to)
            ReportKind.Refunds -> ReportRequest.RefundTotals(from, to)
            ReportKind.Governance -> ReportRequest.GovernanceActions(from, to)
            ReportKind.Retention -> ReportRequest.RetentionActivity
        }
    }

    private fun formatResult(r: ReportResult): String = when (r) {
        is ReportResult.BookingsByStateReport ->
            "Total: ${r.totalBookings}\nTimeout rate: ${"%.2f".format(r.pendingTimeoutRate)}\n" +
                r.byState.entries.joinToString("\n") { "  ${it.key.name}: ${it.value}" }
        is ReportResult.ClaimsByTypeReport ->
            "Within SLA: ${"%.2f".format(r.withinSlaPercent)}%\n" +
                "Avg first response: ${r.averageFirstResponseMinutes} min\n" +
                "By class: ${r.byClassification}\nBy resp: ${r.byResponsibility}"
        is ReportResult.RefundTotalsReport ->
            "Refunds: ${r.refundCount}, total $${r.totalRefundedUsd}, overrides ${r.overrideCount}"
        is ReportResult.GovernanceActionsReport ->
            "Demote: ${r.demotionCount}, Quarantine: ${r.quarantineCount}, " +
                "Excluded: ${r.excludedCount}, Overrides: ${r.overrideCount}"
        is ReportResult.RetentionActivityReport ->
            "Deletions: ${r.deletionsCount}, Anonymizations: ${r.anonymizationCount}, " +
                "Exports: ${r.exportsCount}\nLast sweep: ${r.lastSweepAt ?: "never"}"
    }

    enum class ReportKind { Bookings, Claims, Refunds, Governance, Retention }
}
