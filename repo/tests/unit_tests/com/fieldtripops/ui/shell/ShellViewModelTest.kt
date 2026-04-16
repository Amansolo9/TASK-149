package com.fieldtripops.ui.shell

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.model.Session
import com.fieldtripops.domain.model.User
import com.fieldtripops.domain.repository.UserRepository
import com.fieldtripops.domain.usecase.LogoutUseCase
import com.fieldtripops.domain.usecase.ValidateSessionUseCase
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
class ShellViewModelTest {

    @get:Rule val rule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()

    private lateinit var validate: ValidateSessionUseCase
    private lateinit var logout: LogoutUseCase
    private lateinit var userRepo: UserRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var vm: ShellViewModel

    private val aliceUser = User(
        id = "u1", username = "alice", displayName = "Alice",
        roles = listOf(Role.Traveler), isActive = true,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
    )
    private val aliceSession = Session(
        id = "s1", userId = "u1",
        startedAt = Instant.EPOCH, lastActiveAt = Instant.EPOCH,
        endedAt = null, endReason = null
    )

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        validate = mockk()
        logout = mockk(relaxed = true)
        userRepo = mockk()
        sessionManager = SessionManager()
        vm = ShellViewModel(validate, logout, userRepo, sessionManager)
    }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `initialize without session publishes SessionExpired`() {
        vm.initialize()
        assertThat(vm.state.value).isEqualTo(ShellState.SessionExpired)
    }

    @Test
    fun `valid session with user publishes Active`() = runTest {
        sessionManager.set(SessionContext("u1", "Alice", setOf(Role.Traveler), "s1"))
        coEvery { validate.execute("u1") } returns ValidateSessionUseCase.Result.Valid(aliceSession)
        coEvery { userRepo.findById("u1") } returns aliceUser

        vm.initialize()
        advanceUntilIdle()

        val state = vm.state.value as ShellState.Active
        assertThat(state.user.id).isEqualTo("u1")
    }

    @Test
    fun `valid session but user missing publishes SessionExpired`() = runTest {
        sessionManager.set(SessionContext("u1", "Alice", setOf(Role.Traveler), "s1"))
        coEvery { validate.execute("u1") } returns ValidateSessionUseCase.Result.Valid(aliceSession)
        coEvery { userRepo.findById("u1") } returns null

        vm.initialize()
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(ShellState.SessionExpired)
    }

    @Test
    fun `expired session result publishes SessionExpired`() = runTest {
        sessionManager.set(SessionContext("u1", "Alice", setOf(Role.Traveler), "s1"))
        coEvery { validate.execute("u1") } returns ValidateSessionUseCase.Result.Expired

        vm.initialize()
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(ShellState.SessionExpired)
    }

    @Test
    fun `logout calls use case and publishes LoggedOut`() = runTest {
        coEvery { logout.execute() } returns Unit
        vm.logout()
        advanceUntilIdle()

        coVerify { logout.execute() }
        assertThat(vm.state.value).isEqualTo(ShellState.LoggedOut)
    }
}
