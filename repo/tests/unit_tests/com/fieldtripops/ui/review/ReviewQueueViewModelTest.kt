package com.fieldtripops.ui.review

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.fieldtripops.domain.model.ClaimClassification
import com.fieldtripops.domain.model.ClaimStyle
import com.fieldtripops.domain.model.ClaimTicket
import com.fieldtripops.domain.model.Responsibility
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.model.TicketState
import com.fieldtripops.domain.repository.ClaimRepository
import com.fieldtripops.domain.usecase.TransitionClaimUseCase
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
class ReviewQueueViewModelTest {

    @get:Rule val rule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()

    private lateinit var claimRepo: ClaimRepository
    private lateinit var transitionUseCase: TransitionClaimUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var vm: ReviewQueueViewModel

    private fun ticket(id: String, state: TicketState, createdAt: Instant = Instant.EPOCH) = ClaimTicket(
        id = id, travelerId = "u1", bookingOrderId = "b1",
        claimStyle = ClaimStyle.REFUND_ONLY,
        classification = ClaimClassification.PROVIDER_NO_SHOW,
        responsibility = Responsibility.PROVIDER,
        description = "desc", state = state,
        createdAt = createdAt, updatedAt = createdAt,
        firstResponseAt = null, resolvedAt = null, closedAt = null,
        lastTravelerActivityAt = createdAt
    )

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        claimRepo = mockk()
        transitionUseCase = mockk()
        sessionManager = SessionManager()
        vm = ReviewQueueViewModel(claimRepo, transitionUseCase, sessionManager)
    }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `load without session publishes empty queue and message`() = runTest {
        vm.load()
        advanceUntilIdle()
        assertThat(vm.queue.value).isEmpty()
        assertThat(vm.message.value).contains("Not authorized")
    }

    @Test
    fun `traveler cannot load review queue`() = runTest {
        sessionManager.set(SessionContext("u1", "U", setOf(Role.Traveler), "s1"))
        vm.load()
        advanceUntilIdle()
        assertThat(vm.queue.value).isEmpty()
        assertThat(vm.message.value).contains("Not authorized")
    }

    @Test
    fun `reviewer loads submitted inReview and escalated tickets sorted by createdAt`() = runTest {
        sessionManager.set(SessionContext("rev", "Rev", setOf(Role.Reviewer), "s2"))
        val t3 = ticket("t3", TicketState.Submitted, Instant.ofEpochSecond(3000))
        val t1 = ticket("t1", TicketState.InReview, Instant.ofEpochSecond(1000))
        val t2 = ticket("t2", TicketState.Escalated, Instant.ofEpochSecond(2000))
        coEvery { claimRepo.findByState(TicketState.Submitted) } returns listOf(t3)
        coEvery { claimRepo.findByState(TicketState.InReview) } returns listOf(t1)
        coEvery { claimRepo.findByState(TicketState.Escalated) } returns listOf(t2)

        vm.load()
        advanceUntilIdle()
        assertThat(vm.queue.value?.map { it.id }).containsExactly("t1", "t2", "t3").inOrder()
    }

    @Test
    fun `moveTo Transitioned publishes success and reloads`() = runTest {
        sessionManager.set(SessionContext("rev", "Rev", setOf(Role.Reviewer), "s3"))
        coEvery { transitionUseCase.execute("t1", TicketState.InReview, any()) } returns
            TransitionClaimUseCase.Result.Transitioned
        coEvery { claimRepo.findByState(any()) } returns emptyList()

        vm.moveTo("t1", TicketState.InReview, "pickup")
        advanceUntilIdle()
        assertThat(vm.message.value).contains("InReview")
    }

    @Test
    fun `moveTo illegal transition publishes error`() = runTest {
        sessionManager.set(SessionContext("rev", "Rev", setOf(Role.Reviewer), "s4"))
        coEvery { transitionUseCase.execute(any(), any(), any()) } returns
            TransitionClaimUseCase.Result.IllegalTransition(TicketState.Closed, TicketState.InReview)

        vm.moveTo("t1", TicketState.InReview, null)
        advanceUntilIdle()
        assertThat(vm.message.value).contains("Illegal")
    }

    @Test
    fun `moveTo ticket not found publishes message`() = runTest {
        sessionManager.set(SessionContext("rev", "Rev", setOf(Role.Reviewer), "s5"))
        coEvery { transitionUseCase.execute(any(), any(), any()) } returns
            TransitionClaimUseCase.Result.TicketNotFound

        vm.moveTo("missing", TicketState.InReview, null)
        advanceUntilIdle()
        assertThat(vm.message.value).isEqualTo("Ticket not found")
    }

    @Test
    fun `security exception on moveTo becomes authz message`() = runTest {
        sessionManager.set(SessionContext("rev", "Rev", setOf(Role.Reviewer), "s6"))
        coEvery { transitionUseCase.execute(any(), any(), any()) } throws SecurityException("denied")

        vm.moveTo("t1", TicketState.InReview, null)
        advanceUntilIdle()
        assertThat(vm.message.value).contains("Not authorized")
    }
}
