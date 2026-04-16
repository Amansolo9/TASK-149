package com.fieldtripops.ui.admin

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.SlaConfigRepository
import com.fieldtripops.domain.sla.SlaConfig
import com.fieldtripops.domain.usecase.UpdateSlaConfigUseCase
import com.fieldtripops.security.auth.SessionContext
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SlaConfigViewModelTest {

    @get:Rule val rule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()

    private lateinit var repo: SlaConfigRepository
    private lateinit var updateUseCase: UpdateSlaConfigUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var vm: SlaConfigViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        repo = mockk()
        updateUseCase = mockk()
        sessionManager = SessionManager()
        vm = SlaConfigViewModel(repo, updateUseCase, sessionManager)
    }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `traveler is denied reading SLA config`() = runTest {
        sessionManager.set(SessionContext("u1", "U", setOf(Role.Traveler), "s1"))

        vm.load()
        advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(SlaConfigViewModel.State.Error::class.java)
    }

    @Test
    fun `admin can read current SLA`() = runTest {
        sessionManager.set(SessionContext("admin", "A", setOf(Role.Administrator), "s2"))
        coEvery { repo.get() } returns SlaConfig.DEFAULT

        vm.load()
        advanceUntilIdle()
        val state = vm.state.value as SlaConfigViewModel.State.Loaded
        assertThat(state.config.firstResponseMinutes).isEqualTo(SlaConfig.DEFAULT.firstResponseMinutes)
    }

    @Test
    fun `successful save publishes Saved`() = runTest {
        sessionManager.set(SessionContext("admin", "A", setOf(Role.Administrator), "s3"))
        val updated = SlaConfig(60, 120, 24, Instant.EPOCH, "admin", 9, 17, true)
        coEvery {
            updateUseCase.execute(60, 120, 24, 9, 17, true)
        } returns UpdateSlaConfigUseCase.Result.Updated(updated)

        vm.save(60, 120, 24, 9, 17, true)
        advanceUntilIdle()
        val state = vm.state.value as SlaConfigViewModel.State.Saved
        assertThat(state.config.firstResponseMinutes).isEqualTo(60)
    }

    @Test
    fun `invalid save publishes Error with reason`() = runTest {
        sessionManager.set(SessionContext("admin", "A", setOf(Role.Administrator), "s4"))
        coEvery { updateUseCase.execute(any(), any(), any(), any(), any(), any()) } returns
            UpdateSlaConfigUseCase.Result.Invalid("firstResponseMinutes must be > 0")

        vm.save(0, 120, 24, 9, 17, true)
        advanceUntilIdle()
        val state = vm.state.value as SlaConfigViewModel.State.Error
        assertThat(state.message).contains("> 0")
    }

    @Test
    fun `security exception on save becomes Error`() = runTest {
        sessionManager.set(SessionContext("admin", "A", setOf(Role.Administrator), "s5"))
        coEvery { updateUseCase.execute(any(), any(), any(), any(), any(), any()) } throws SecurityException("denied")

        vm.save(60, 120, 24)
        advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(SlaConfigViewModel.State.Error::class.java)
    }
}
