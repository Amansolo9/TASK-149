package com.fieldtripops.ui.reports

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.fieldtripops.domain.reports.ReportRequest
import com.fieldtripops.domain.reports.ReportResult
import com.fieldtripops.domain.usecase.GenerateExportUseCase
import com.fieldtripops.domain.usecase.GenerateReportUseCase
import com.fieldtripops.security.auth.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelDispatcherTest {

    @get:Rule val rule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var reportUseCase: GenerateReportUseCase
    private lateinit var exportUseCase: GenerateExportUseCase
    private lateinit var session: SessionManager

    @Before fun setup() {
        Dispatchers.setMain(testDispatcher)
        reportUseCase = mockk()
        exportUseCase = mockk()
        session = SessionManager()
    }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `runReport publishes output after io work completes`() = runTest {
        coEvery { reportUseCase.execute(any()) } returns
            ReportResult.RetentionActivityReport(3, 1, 2, null)

        val vm = ReportsViewModel(reportUseCase, exportUseCase, session, testDispatcher)
        vm.runReport(ReportRequest.RetentionActivity)

        // Advance all coroutines so both the IO and main dispatchers drain.
        advanceUntilIdle()

        // After draining, loading is false and the result is published.
        assertThat(vm.loading.value).isFalse()
        assertThat(vm.output.value).contains("Deletions: 3")
        assertThat(vm.output.value).contains("Anonymizations: 1")
    }
}
