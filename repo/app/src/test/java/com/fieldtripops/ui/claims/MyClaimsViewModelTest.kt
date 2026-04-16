package com.fieldtripops.ui.claims

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.fieldtripops.domain.model.AppealRecord
import com.fieldtripops.domain.model.ClaimClassification
import com.fieldtripops.domain.model.ClaimStyle
import com.fieldtripops.domain.model.ClaimTicket
import com.fieldtripops.domain.model.Responsibility
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.model.TicketState
import com.fieldtripops.domain.repository.ClaimRepository
import com.fieldtripops.domain.usecase.FileAppealUseCase
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
class MyClaimsViewModelTest {

    @get:Rule val rule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()

    private lateinit var claimRepo: ClaimRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var appealUseCase: FileAppealUseCase
    private lateinit var vm: MyClaimsViewModel

    private fun ticket(id: String, travelerId: String, state: TicketState = TicketState.Submitted) = ClaimTicket(
        id = id, travelerId = travelerId, bookingOrderId = "b1",
        claimStyle = ClaimStyle.REFUND_ONLY,
        classification = ClaimClassification.PROVIDER_NO_SHOW,
        responsibility = Responsibility.PROVIDER,
        description = "desc", state = state,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        firstResponseAt = null, resolvedAt = null, closedAt = null,
        lastTravelerActivityAt = Instant.EPOCH
    )

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        claimRepo = mockk()
        sessionManager = SessionManager()
        appealUseCase = mockk()
        vm = MyClaimsViewModel(claimRepo, sessionManager, appealUseCase)
    }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `load without session is a no-op`() = runTest {
        vm.load()
        advanceUntilIdle()
        assertThat(vm.tickets.value).isEmpty()
    }

    @Test
    fun `load with session returns own tickets`() = runTest {
        sessionManager.set(SessionContext("alice", "Alice", setOf(Role.Traveler), "s1"))
        coEvery { claimRepo.findByTraveler("alice") } returns listOf(ticket("t1", "alice"))

        vm.load()
        advanceUntilIdle()
        assertThat(vm.tickets.value).hasSize(1)
        assertThat(vm.tickets.value?.first()?.travelerId).isEqualTo("alice")
    }

    @Test
    fun `successful appeal publishes confirmation message`() = runTest {
        sessionManager.set(SessionContext("alice", "Alice", setOf(Role.Traveler), "s1"))
        val appeal = AppealRecord(
            id = "a1", ticketId = "t1",
            filedBy = "alice", filedAt = Instant.EPOCH,
            reason = "dispute",
            resolvedAt = null, resolution = null
        )
        coEvery { appealUseCase.execute("t1", "dispute") } returns
            FileAppealUseCase.Result.Filed(appeal, "q1234567890")
        coEvery { claimRepo.findByTraveler("alice") } returns emptyList()

        vm.fileAppeal("t1", "dispute")
        advanceUntilIdle()
        assertThat(vm.message.value).contains("Appeal filed")
    }

    @Test
    fun `invalid state publishes message`() = runTest {
        sessionManager.set(SessionContext("alice", "Alice", setOf(Role.Traveler), "s1"))
        coEvery { appealUseCase.execute(any(), any()) } returns
            FileAppealUseCase.Result.InvalidState(TicketState.Closed)
        coEvery { claimRepo.findByTraveler("alice") } returns emptyList()

        vm.fileAppeal("t1", "dispute")
        advanceUntilIdle()
        assertThat(vm.message.value).contains("Closed")
    }

    @Test
    fun `ticket not found publishes message`() = runTest {
        sessionManager.set(SessionContext("alice", "Alice", setOf(Role.Traveler), "s1"))
        coEvery { appealUseCase.execute(any(), any()) } returns FileAppealUseCase.Result.TicketNotFound
        coEvery { claimRepo.findByTraveler("alice") } returns emptyList()

        vm.fileAppeal("missing", "dispute")
        advanceUntilIdle()
        assertThat(vm.message.value).isEqualTo("Ticket not found")
    }

    @Test
    fun `reason required publishes message`() = runTest {
        sessionManager.set(SessionContext("alice", "Alice", setOf(Role.Traveler), "s1"))
        coEvery { appealUseCase.execute(any(), any()) } returns FileAppealUseCase.Result.ReasonRequired
        coEvery { claimRepo.findByTraveler("alice") } returns emptyList()

        vm.fileAppeal("t1", "")
        advanceUntilIdle()
        assertThat(vm.message.value).contains("reason")
    }

    @Test
    fun `security exception becomes message`() = runTest {
        sessionManager.set(SessionContext("alice", "Alice", setOf(Role.Traveler), "s1"))
        coEvery { appealUseCase.execute(any(), any()) } throws SecurityException("denied")

        vm.fileAppeal("t1", "dispute")
        advanceUntilIdle()
        assertThat(vm.message.value).contains("Not authorized")
    }

    @Test
    fun `clearMessage resets message state`() = runTest {
        sessionManager.set(SessionContext("alice", "Alice", setOf(Role.Traveler), "s1"))
        coEvery { appealUseCase.execute(any(), any()) } returns FileAppealUseCase.Result.TicketNotFound
        coEvery { claimRepo.findByTraveler("alice") } returns emptyList()
        vm.fileAppeal("missing", "dispute")
        advanceUntilIdle()
        vm.clearMessage()
        assertThat(vm.message.value).isNull()
    }
}
