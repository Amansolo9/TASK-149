package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.SlaConfigRepository
import com.fieldtripops.domain.sla.SlaConfig
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import com.fieldtripops.security.auth.UnauthorizedException
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class UpdateSlaConfigUseCaseTest {

    private lateinit var repo: SlaConfigRepository
    private lateinit var audit: AuditLogger
    private lateinit var session: SessionManager
    private lateinit var uc: UpdateSlaConfigUseCase

    @Before
    fun setup() {
        repo = mockk(relaxed = true)
        coEvery { repo.get() } returns SlaConfig.DEFAULT
        audit = mockk(relaxed = true)
        session = SessionManager()
        uc = UpdateSlaConfigUseCase(repo, audit, session)
    }

    @Test(expected = UnauthorizedException::class)
    fun `non-admin cannot update SLA`() = runTest {
        session.set(SessionContext("u1", "U", setOf(Role.Reviewer), "s1"))
        uc.execute(60, 1440, 24)
    }

    @Test
    fun `admin can update SLA and audit fires`() = runTest {
        session.set(SessionContext("admin1", "Admin", setOf(Role.Administrator), "s1"))
        val r = uc.execute(60, 1440, 24) as UpdateSlaConfigUseCase.Result.Updated
        assertThat(r.config.firstResponseMinutes).isEqualTo(60)
        assertThat(r.config.resolutionMinutes).isEqualTo(1440)
        assertThat(r.config.travelerNoResponseHours).isEqualTo(24)
        assertThat(r.config.updatedBy).isEqualTo("admin1")
        coVerify { repo.save(any()) }
        coVerify { audit.log("admin1", any(), "SlaConfig", "current", any()) }
    }

    @Test
    fun `validation rejects non-positive values`() = runTest {
        session.set(SessionContext("admin1", "Admin", setOf(Role.Administrator), "s1"))
        assertThat(uc.execute(0, 60, 24)).isInstanceOf(UpdateSlaConfigUseCase.Result.Invalid::class.java)
        assertThat(uc.execute(60, 0, 24)).isInstanceOf(UpdateSlaConfigUseCase.Result.Invalid::class.java)
        assertThat(uc.execute(60, 60, 0)).isInstanceOf(UpdateSlaConfigUseCase.Result.Invalid::class.java)
    }

    @Test
    fun `resolution must be at least first response`() = runTest {
        session.set(SessionContext("admin1", "Admin", setOf(Role.Administrator), "s1"))
        val r = uc.execute(120, 60, 24)
        assertThat(r).isInstanceOf(UpdateSlaConfigUseCase.Result.Invalid::class.java)
    }
}
