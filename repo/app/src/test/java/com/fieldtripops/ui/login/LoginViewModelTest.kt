package com.fieldtripops.ui.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.fieldtripops.domain.model.AuthResult
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.model.Session
import com.fieldtripops.domain.model.User
import com.fieldtripops.domain.usecase.LoginUseCase
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

/**
 * Tests proving:
 *  - Blank inputs are rejected without calling the use case
 *  - Success publishes user to state and stores id
 *  - Invalid credentials, locked, inactive all map to correct LoginState
 *  - Transient exceptions become Error state (no crash)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule val rule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private lateinit var useCase: LoginUseCase
    private lateinit var vm: LoginViewModel

    private val sampleUser = User(
        id = "u1", username = "alice", displayName = "Alice",
        roles = listOf(Role.Traveler), isActive = true,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
    )
    private val sampleSession = Session(
        id = "s1", userId = "u1",
        startedAt = Instant.EPOCH, lastActiveAt = Instant.EPOCH,
        endedAt = null, endReason = null
    )

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        useCase = mockk()
        vm = LoginViewModel(useCase)
    }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `blank username is rejected without calling use case`() {
        vm.login("", "anypass")
        assertThat(vm.state.value).isInstanceOf(LoginState.Error::class.java)
    }

    @Test
    fun `blank password is rejected`() {
        vm.login("alice", "")
        assertThat(vm.state.value).isInstanceOf(LoginState.Error::class.java)
    }

    @Test
    fun `successful login publishes Success and stores user id`() = runTest {
        coEvery { useCase.execute("alice", "pw") } returns AuthResult.Success(sampleSession, sampleUser)
        vm.login("alice", "pw")
        advanceUntilIdle()
        val state = vm.state.value
        assertThat(state).isInstanceOf(LoginState.Success::class.java)
        assertThat((state as LoginState.Success).user.id).isEqualTo("u1")
        assertThat(vm.loggedInUserId).isEqualTo("u1")
    }

    @Test
    fun `invalid credentials publishes Error`() = runTest {
        coEvery { useCase.execute(any(), any()) } returns AuthResult.InvalidCredentials
        vm.login("alice", "wrong")
        advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(LoginState.Error::class.java)
    }

    @Test
    fun `locked account publishes LockedOut with unlock instant`() = runTest {
        val unlockAt = Instant.parse("2026-04-16T12:00:00Z")
        coEvery { useCase.execute(any(), any()) } returns AuthResult.Locked(unlockAt)
        vm.login("alice", "wrong")
        advanceUntilIdle()
        val state = vm.state.value
        assertThat(state).isInstanceOf(LoginState.LockedOut::class.java)
        assertThat((state as LoginState.LockedOut).unlockAt).isEqualTo(unlockAt)
    }

    @Test
    fun `inactive user publishes Error`() = runTest {
        coEvery { useCase.execute(any(), any()) } returns AuthResult.UserInactive
        vm.login("inactive", "pw")
        advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(LoginState.Error::class.java)
    }

    @Test
    fun `exception from use case becomes Error state`() = runTest {
        coEvery { useCase.execute(any(), any()) } throws RuntimeException("db down")
        vm.login("alice", "pw")
        advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(LoginState.Error::class.java)
    }

    @Test
    fun `resetState returns to Idle`() {
        vm.login("", "")
        vm.resetState()
        assertThat(vm.state.value).isEqualTo(LoginState.Idle)
    }
}
