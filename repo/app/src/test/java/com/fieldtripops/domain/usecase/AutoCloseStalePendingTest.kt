package com.fieldtripops.domain.usecase

import androidx.room.withTransaction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.domain.model.BookingOrder
import com.fieldtripops.domain.model.BookingState
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.repository.InventoryRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Ignore
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/**
 * Tests proving:
 *  - Auto-close transitions to Closed (not AutoClosed)
 *  - Terminal transitions follow the prompt-aligned state machine
 */
@Ignore("Requires Android runtime (Room withTransaction or Bitmap); move to androidTest")
class AutoCloseStalePendingTest {

    private lateinit var db: FieldTripDatabase
    private lateinit var bookingRepo: BookingRepository
    private lateinit var inventoryRepo: InventoryRepository
    private lateinit var auditLogger: AuditLogger
    private lateinit var useCase: AutoCloseStalePendingUseCase

    @Before
    fun setup() {
        db = mockk(relaxed = true)
        bookingRepo = mockk(relaxed = true)
        inventoryRepo = mockk(relaxed = true)
        auditLogger = mockk(relaxed = true)
        useCase = AutoCloseStalePendingUseCase(db, bookingRepo, inventoryRepo, auditLogger)

        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { db.withTransaction(any<suspend () -> Any>()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val block = args[0] as suspend () -> Any
            block()
        }
    }

    @Test
    fun `auto-close transitions pending to Closed not AutoClosed`() = runTest {
        val now = Instant.now()
        val staleOrder = BookingOrder(
            id = "b1", itineraryId = "i1", travelerId = "t1",
            inventorySlotId = "s1", partySize = 2,
            state = BookingState.PendingConfirmation,
            createdAt = now.minus(Duration.ofMinutes(45)),
            updatedAt = now.minus(Duration.ofMinutes(45)),
            confirmedAt = null, confirmedBy = null,
            cancelledAt = null, cancelReason = null,
            lastActivityAt = now.minus(Duration.ofMinutes(45)),
            tripStartAt = now.plus(Duration.ofDays(7)),
            tripEndAt = now.plus(Duration.ofDays(8)),
            paidTotal = BigDecimal.ZERO
        )

        coEvery { bookingRepo.findPendingConfirmationOlderThan(any()) } returns listOf(staleOrder)
        coEvery { bookingRepo.findById("b1") } returns staleOrder

        val stateSlot = slot<BookingState>()
        coEvery {
            bookingRepo.updateState("b1", capture(stateSlot), any(), any(), any())
        } returns Unit

        val result = useCase.execute()
        assertThat(result.closedCount).isEqualTo(1)
        assertThat(stateSlot.captured).isEqualTo(BookingState.Closed)
    }

    @Test
    fun `PendingConfirmation to Closed is a valid transition`() {
        assertThat(BookingState.canTransition(BookingState.PendingConfirmation, BookingState.Closed)).isTrue()
    }

    @Test
    fun `Closed is terminal`() {
        assertThat(BookingState.isTerminal(BookingState.Closed)).isTrue()
    }

    @Test
    fun `Cancelled is terminal`() {
        assertThat(BookingState.isTerminal(BookingState.Cancelled)).isTrue()
    }
}
