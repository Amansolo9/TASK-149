package com.fieldtripops.ui.booking

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.fieldtripops.domain.model.BookingOrder
import com.fieldtripops.domain.model.BookingState
import com.fieldtripops.domain.model.FeeCategory
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.usecase.CancelBookingUseCase
import com.fieldtripops.domain.usecase.ConfirmBookingUseCase
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
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/**
 * Tests proving:
 *  - load() performs object-level authorization via SessionManager
 *  - Unauthorized load publishes an Error instead of exposing details
 *  - Agent/Admin can load any booking; Traveler only their own
 *  - Fee item validation errors propagate to Result.Error
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookingConfirmViewModelTest {

    @get:Rule val rule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()

    private lateinit var bookingRepo: BookingRepository
    private lateinit var confirmUseCase: ConfirmBookingUseCase
    private lateinit var cancelUseCase: CancelBookingUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var vm: BookingConfirmViewModel

    private fun booking(travelerId: String) = BookingOrder(
        id = "b1", itineraryId = "i1", travelerId = travelerId,
        inventorySlotId = "s1", partySize = 2,
        state = BookingState.PendingConfirmation,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        confirmedAt = null, confirmedBy = null,
        cancelledAt = null, cancelReason = null,
        lastActivityAt = Instant.EPOCH,
        tripStartAt = Instant.EPOCH.plus(Duration.ofDays(7)),
        tripEndAt = Instant.EPOCH.plus(Duration.ofDays(8)),
        paidTotal = BigDecimal.ZERO
    )

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        bookingRepo = mockk()
        confirmUseCase = mockk()
        cancelUseCase = mockk()
        sessionManager = SessionManager()
        vm = BookingConfirmViewModel(bookingRepo, confirmUseCase, cancelUseCase, sessionManager)
    }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `traveler loading own booking populates UI state`() = runTest {
        sessionManager.set(SessionContext("alice", "Alice", setOf(Role.Traveler), "s1"))
        coEvery { bookingRepo.findById("b1") } returns booking("alice")

        vm.load("b1")
        advanceUntilIdle()

        assertThat(vm.uiState.value?.order).isNotNull()
        assertThat(vm.uiState.value?.order?.id).isEqualTo("b1")
    }

    @Test
    fun `traveler loading another travelers booking publishes Error and does not populate state`() = runTest {
        sessionManager.set(SessionContext("bob", "Bob", setOf(Role.Traveler), "s2"))
        coEvery { bookingRepo.findById("b1") } returns booking("alice")

        vm.load("b1")
        advanceUntilIdle()

        // order must NOT leak into UI state
        assertThat(vm.uiState.value?.order).isNull()
        // result must be an Error
        assertThat(vm.result.value).isInstanceOf(BookingConfirmResult.Error::class.java)
    }

    @Test
    fun `agent can load any booking`() = runTest {
        sessionManager.set(SessionContext("agent-1", "Agent", setOf(Role.Agent), "s3"))
        coEvery { bookingRepo.findById("b1") } returns booking("alice")

        vm.load("b1")
        advanceUntilIdle()

        assertThat(vm.uiState.value?.order?.travelerId).isEqualTo("alice")
    }

    @Test
    fun `load without session publishes Error`() = runTest {
        // No session set
        coEvery { bookingRepo.findById("b1") } returns booking("alice")

        vm.load("b1")
        advanceUntilIdle()

        assertThat(vm.uiState.value?.order).isNull()
        assertThat(vm.result.value).isInstanceOf(BookingConfirmResult.Error::class.java)
    }

    @Test
    fun `load returns Error for missing booking id`() = runTest {
        sessionManager.set(SessionContext("alice", "Alice", setOf(Role.Traveler), "s4"))
        coEvery { bookingRepo.findById("missing") } returns null

        vm.load("missing")
        advanceUntilIdle()

        assertThat(vm.result.value).isInstanceOf(BookingConfirmResult.Error::class.java)
    }

    @Test
    fun `addFeeItem with blank description publishes Error`() {
        vm.addFeeItem("", "10.00", FeeCategory.BASE_FARE)
        assertThat(vm.result.value).isInstanceOf(BookingConfirmResult.Error::class.java)
    }

    @Test
    fun `addFeeItem with non-numeric amount publishes Error`() {
        vm.addFeeItem("Taxi", "not-a-number", FeeCategory.TAX_FEE)
        assertThat(vm.result.value).isInstanceOf(BookingConfirmResult.Error::class.java)
    }

    @Test
    fun `addFeeItem with too many decimals publishes Error`() {
        vm.addFeeItem("Taxi", "10.12345", FeeCategory.TAX_FEE)
        assertThat(vm.result.value).isInstanceOf(BookingConfirmResult.Error::class.java)
    }
}
