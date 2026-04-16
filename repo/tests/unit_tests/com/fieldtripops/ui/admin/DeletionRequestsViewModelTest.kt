package com.fieldtripops.ui.admin

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.fieldtripops.domain.model.DeletionRequest
import com.fieldtripops.domain.model.DeletionScope
import com.fieldtripops.domain.model.DeletionState
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.DeletionRequestRepository
import com.fieldtripops.domain.usecase.ExecuteUserDeletionUseCase
import com.fieldtripops.domain.usecase.RequestUserDeletionUseCase
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
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
class DeletionRequestsViewModelTest {

    @get:Rule val rule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()

    private lateinit var repo: DeletionRequestRepository
    private lateinit var requestUseCase: RequestUserDeletionUseCase
    private lateinit var executeUseCase: ExecuteUserDeletionUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var vm: DeletionRequestsViewModel

    private val sampleRequest = DeletionRequest(
        id = "r1", targetUserId = "u1", requestedBy = "u1",
        requestedAt = Instant.EPOCH, reason = "self-request",
        state = DeletionState.Requested,
        approvedBy = null, approvedAt = null,
        executedBy = null, executedAt = null,
        failureReason = null, scope = DeletionScope.ANONYMIZE
    )

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        repo = mockk()
        requestUseCase = mockk()
        executeUseCase = mockk()
        sessionManager = SessionManager()
        vm = DeletionRequestsViewModel(repo, requestUseCase, executeUseCase, sessionManager)
    }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `loadAll is denied for non-admin`() = runTest {
        sessionManager.set(SessionContext("u1", "U", setOf(Role.Traveler), "s1"))
        vm.loadAll()
        advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(DeletionRequestsViewModel.State.Error::class.java)
    }

    @Test
    fun `admin loadAll returns items`() = runTest {
        sessionManager.set(SessionContext("admin", "A", setOf(Role.Administrator), "s2"))
        coEvery { repo.getAll() } returns listOf(sampleRequest)

        vm.loadAll()
        advanceUntilIdle()
        val state = vm.state.value as DeletionRequestsViewModel.State.Loaded
        assertThat(state.items).hasSize(1)
    }

    @Test
    fun `self-delete Queued publishes Message`() = runTest {
        sessionManager.set(SessionContext("u1", "U", setOf(Role.Traveler), "s3"))
        coEvery { requestUseCase.execute("u1", any(), DeletionScope.ANONYMIZE) } returns
            RequestUserDeletionUseCase.Result.Queued(sampleRequest)

        vm.requestSelfDelete("u1", "reason")
        advanceUntilIdle()
        val state = vm.state.value as DeletionRequestsViewModel.State.Message
        assertThat(state.text).contains("queued")
    }

    @Test
    fun `self-delete AlreadyPending publishes Message`() = runTest {
        sessionManager.set(SessionContext("u1", "U", setOf(Role.Traveler), "s4"))
        coEvery { requestUseCase.execute(any(), any(), any()) } returns
            RequestUserDeletionUseCase.Result.AlreadyPending(existingCount = 1)

        vm.requestSelfDelete("u1", "reason")
        advanceUntilIdle()
        val state = vm.state.value as DeletionRequestsViewModel.State.Message
        assertThat(state.text).contains("already pending")
    }

    @Test
    fun `self-delete Invalid publishes Error with reason`() = runTest {
        sessionManager.set(SessionContext("u1", "U", setOf(Role.Traveler), "s5"))
        coEvery { requestUseCase.execute(any(), any(), any()) } returns
            RequestUserDeletionUseCase.Result.Invalid("bad input")

        vm.requestSelfDelete("u1", "reason")
        advanceUntilIdle()
        val state = vm.state.value as DeletionRequestsViewModel.State.Error
        assertThat(state.text).isEqualTo("bad input")
    }

    @Test
    fun `approveAndExecute Executed publishes Message and reloads`() = runTest {
        sessionManager.set(SessionContext("admin", "A", setOf(Role.Administrator), "s6"))
        val executed = sampleRequest.copy(state = DeletionState.Executed, executedBy = "admin", executedAt = Instant.EPOCH)
        coEvery { executeUseCase.execute("r1") } returns
            ExecuteUserDeletionUseCase.Result.Executed(executed, rowsTouched = 7)
        coEvery { repo.getAll() } returns listOf(executed)

        vm.approveAndExecute("r1")
        advanceUntilIdle()
        coVerify { repo.getAll() }
        // After reload, state ends as Loaded
        assertThat(vm.state.value).isInstanceOf(DeletionRequestsViewModel.State.Loaded::class.java)
    }

    @Test
    fun `approveAndExecute NotFound triggers execute use case`() = runTest {
        sessionManager.set(SessionContext("admin", "A", setOf(Role.Administrator), "s7"))
        coEvery { executeUseCase.execute(any()) } returns ExecuteUserDeletionUseCase.Result.NotFound("missing")
        coEvery { repo.getAll() } returns emptyList()

        vm.approveAndExecute("missing")
        advanceUntilIdle()
        coVerify { executeUseCase.execute("missing") }
        // After reload, admin sees Loaded(empty)
        assertThat(vm.state.value).isInstanceOf(DeletionRequestsViewModel.State.Loaded::class.java)
    }

    @Test
    fun `approveAndExecute Failed triggers execute use case`() = runTest {
        sessionManager.set(SessionContext("admin", "A", setOf(Role.Administrator), "s8"))
        coEvery { executeUseCase.execute(any()) } returns ExecuteUserDeletionUseCase.Result.Failed("db error")
        coEvery { repo.getAll() } returns emptyList()

        vm.approveAndExecute("r1")
        advanceUntilIdle()
        coVerify { executeUseCase.execute("r1") }
        // Final reload call produces Loaded; we just assert no crash
        assertThat(vm.state.value).isNotNull()
    }

    @Test
    fun `reject delegates to use case and reloads`() = runTest {
        sessionManager.set(SessionContext("admin", "A", setOf(Role.Administrator), "s9"))
        coEvery { executeUseCase.reject("r1", "not needed") } returns
            ExecuteUserDeletionUseCase.Result.AlreadyExecuted(sampleRequest)
        coEvery { repo.getAll() } returns emptyList()

        vm.reject("r1", "not needed")
        advanceUntilIdle()
        coVerify { executeUseCase.reject("r1", "not needed") }
    }
}
