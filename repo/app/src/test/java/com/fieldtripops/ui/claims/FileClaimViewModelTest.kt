package com.fieldtripops.ui.claims

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.fieldtripops.attachment.PendingAttachment
import com.fieldtripops.domain.model.AttachmentRef
import com.fieldtripops.domain.model.ClaimClassification
import com.fieldtripops.domain.model.ClaimStyle
import com.fieldtripops.domain.model.ClaimTicket
import com.fieldtripops.domain.model.Responsibility
import com.fieldtripops.domain.model.TicketState
import com.fieldtripops.domain.usecase.FileClaimUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
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
 *  - Staged attachments flow through to the use case
 *  - Classification-to-responsibility mapping is deterministic
 *  - Filed, BookingNotFound, ValidationFailed all map to correct State
 *  - SecurityException becomes an Error state (not a crash)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileClaimViewModelTest {

    @get:Rule val rule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private lateinit var useCase: FileClaimUseCase
    private lateinit var vm: FileClaimViewModel

    private val sampleTicket = ClaimTicket(
        id = "t1", travelerId = "u1", bookingOrderId = "b1",
        claimStyle = ClaimStyle.REFUND_ONLY,
        classification = ClaimClassification.PROVIDER_NO_SHOW,
        responsibility = Responsibility.PROVIDER,
        description = "desc", state = TicketState.Submitted,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        firstResponseAt = null, resolvedAt = null, closedAt = null,
        lastTravelerActivityAt = Instant.EPOCH
    )

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        useCase = mockk()
        vm = FileClaimViewModel(useCase)
    }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `stageAttachment tracks attachment count`() {
        assertThat(vm.attachmentCount).isEqualTo(0)
        vm.stageAttachment(PendingAttachment(fileName = "a.jpg", mimeType = "image/jpeg", data = ByteArray(10)))
        assertThat(vm.attachmentCount).isEqualTo(1)
        vm.stageAttachment(PendingAttachment(fileName = "b.pdf", mimeType = "application/pdf", data = ByteArray(10)))
        assertThat(vm.attachmentCount).isEqualTo(2)
    }

    @Test
    fun `clearAttachments resets count`() {
        vm.stageAttachment(PendingAttachment(fileName = "a.jpg", mimeType = "image/jpeg", data = ByteArray(10)))
        vm.clearAttachments()
        assertThat(vm.attachmentCount).isEqualTo(0)
    }

    @Test
    fun `successful submit publishes Submitted`() = runTest {
        coEvery { useCase.execute(any(), any(), any(), any(), any(), any()) } returns
            FileClaimUseCase.Result.Filed(sampleTicket, emptyList<AttachmentRef>())
        vm.submit("b1", ClaimStyle.REFUND_ONLY, ClaimClassification.PROVIDER_NO_SHOW, "desc")
        advanceUntilIdle()
        assertThat(vm.state.value).isEqualTo(FileClaimViewModel.State.Submitted)
    }

    @Test
    fun `BookingNotFound publishes Error`() = runTest {
        coEvery { useCase.execute(any(), any(), any(), any(), any(), any()) } returns
            FileClaimUseCase.Result.BookingNotFound
        vm.submit("missing", ClaimStyle.REFUND_ONLY, ClaimClassification.PROVIDER_NO_SHOW, "desc")
        advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(FileClaimViewModel.State.Error::class.java)
    }

    @Test
    fun `ValidationFailed publishes Error with joined messages`() = runTest {
        coEvery { useCase.execute(any(), any(), any(), any(), any(), any()) } returns
            FileClaimUseCase.Result.ValidationFailed(listOf("no proof", "too big"))
        vm.submit("b1", ClaimStyle.REFUND_ONLY, ClaimClassification.PROVIDER_NO_SHOW, "desc")
        advanceUntilIdle()
        val state = vm.state.value as FileClaimViewModel.State.Error
        assertThat(state.message).contains("no proof")
        assertThat(state.message).contains("too big")
    }

    @Test
    fun `SecurityException becomes Error not crash`() = runTest {
        coEvery { useCase.execute(any(), any(), any(), any(), any(), any()) } throws SecurityException("denied")
        vm.submit("b1", ClaimStyle.REFUND_ONLY, ClaimClassification.PROVIDER_NO_SHOW, "desc")
        advanceUntilIdle()
        val state = vm.state.value as FileClaimViewModel.State.Error
        assertThat(state.message).contains("Not authorized")
    }

    @Test
    fun `classification maps to deterministic responsibility`() = runTest {
        val respSlot = slot<Responsibility>()
        coEvery {
            useCase.execute(any(), any(), any(), capture(respSlot), any(), any())
        } returns FileClaimUseCase.Result.Filed(sampleTicket, emptyList())

        vm.submit("b1", ClaimStyle.REFUND_ONLY, ClaimClassification.SAFETY_CONCERN, "desc")
        advanceUntilIdle()
        assertThat(respSlot.captured).isEqualTo(Responsibility.UNKNOWN)
    }

    @Test
    fun `pricing discrepancy maps to AGENT responsibility`() = runTest {
        val respSlot = slot<Responsibility>()
        coEvery {
            useCase.execute(any(), any(), any(), capture(respSlot), any(), any())
        } returns FileClaimUseCase.Result.Filed(sampleTicket, emptyList())

        vm.submit("b1", ClaimStyle.REFUND_ONLY, ClaimClassification.PRICING_DISCREPANCY, "desc")
        advanceUntilIdle()
        assertThat(respSlot.captured).isEqualTo(Responsibility.AGENT)
    }

    @Test
    fun `submit passes staged attachments to use case`() = runTest {
        vm.stageAttachment(PendingAttachment(fileName = "x.jpg", mimeType = "image/jpeg", data = ByteArray(10)))
        coEvery { useCase.execute(any(), any(), any(), any(), any(), any()) } returns
            FileClaimUseCase.Result.Filed(sampleTicket, emptyList())

        vm.submit("b1", ClaimStyle.REFUND_ONLY, ClaimClassification.PROVIDER_NO_SHOW, "desc")
        advanceUntilIdle()
        coVerify { useCase.execute(any(), any(), any(), any(), any(), match<List<PendingAttachment>> { it.size == 1 }) }
    }
}
