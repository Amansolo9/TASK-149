package com.fieldtripops.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.audit.RoomAuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.repository.AuditRepositoryImpl
import com.fieldtripops.data.repository.SlaConfigRepositoryImpl
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.reports.ReportRequest
import com.fieldtripops.domain.reports.ReportResult
import com.fieldtripops.security.AuditChecksum
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies retention reporting reads from real persisted audit events
 * (audit finding #9). No stub values are returned.
 */
@RunWith(AndroidJUnit4::class)
class RetentionReportTest {

    private lateinit var db: FieldTripDatabase
    private lateinit var useCase: GenerateReportUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var auditLogger: AuditLogger

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FieldTripDatabase::class.java
        ).allowMainThreadQueries().build()
        val auditRepo = AuditRepositoryImpl(db.auditLogDao())
        auditLogger = RoomAuditLogger(auditRepo, AuditChecksum())
        val slaRepo = SlaConfigRepositoryImpl(db, db.slaConfigDao())
        sessionManager = SessionManager().also {
            it.set(SessionContext("admin", "Admin", setOf(Role.Administrator), "s1"))
        }
        useCase = GenerateReportUseCase(
            bookingRepository = mockk(relaxed = true),
            claimRepository = mockk(relaxed = true),
            refundRepository = mockk(relaxed = true),
            governanceRepository = mockk(relaxed = true),
            slaConfigRepository = slaRepo,
            auditRepository = auditRepo,
            auditLogger = auditLogger,
            sessionManager = sessionManager
        )
    }

    @After fun teardown() { db.close() }

    @Test
    fun `retention report counts real audit events`() = runTest {
        // Seed audit events
        auditLogger.log("system", AuditAction.DATA_DELETED, "User", "u1", "test")
        auditLogger.log("system", AuditAction.DATA_DELETED, "User", "u2", "test")
        auditLogger.log("system", AuditAction.DATA_ANONYMIZED, "ClaimTicket", "t1", null)
        auditLogger.log("admin", AuditAction.EXPORT_CREATED, "ExportPackage", "e1", null)
        auditLogger.log("system", AuditAction.RETENTION_SWEEP_RUN, "RetentionSweep", "auto", null)

        val r = useCase.execute(ReportRequest.RetentionActivity)
            as ReportResult.RetentionActivityReport
        assertThat(r.deletionsCount).isEqualTo(2)
        assertThat(r.anonymizationCount).isEqualTo(1)
        assertThat(r.exportsCount).isEqualTo(1)
        assertThat(r.lastSweepAt).isNotNull()
    }

    @Test
    fun `empty audit produces zero counts (no stub)`() = runTest {
        val r = useCase.execute(ReportRequest.RetentionActivity)
            as ReportResult.RetentionActivityReport
        assertThat(r.deletionsCount).isEqualTo(0)
        assertThat(r.anonymizationCount).isEqualTo(0)
        assertThat(r.exportsCount).isEqualTo(0)
        assertThat(r.lastSweepAt).isNull()
    }
}
