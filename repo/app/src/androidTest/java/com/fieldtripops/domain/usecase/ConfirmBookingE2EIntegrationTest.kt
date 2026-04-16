package com.fieldtripops.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.entity.InventorySlotEntity
import com.fieldtripops.data.entity.UserEntity
import com.fieldtripops.data.repository.BookingRepositoryImpl
import com.fieldtripops.data.repository.FeeItemRepositoryImpl
import com.fieldtripops.data.repository.InventoryRepositoryImpl
import com.fieldtripops.data.repository.ItineraryRepositoryImpl
import com.fieldtripops.domain.model.BookingState
import com.fieldtripops.domain.model.FeeCategory
import com.fieldtripops.domain.model.FeeItem
import com.fieldtripops.domain.model.ItineraryDraft
import com.fieldtripops.domain.model.Role
import com.fieldtripops.security.NoopSensitiveFieldCodec
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * End-to-end booking lifecycle test against real in-memory Room DB.
 * Submits → confirms → validates paidTotal is persisted for refund use.
 * No mocks for repositories; only the audit logger is stubbed.
 */
@RunWith(AndroidJUnit4::class)
class ConfirmBookingE2EIntegrationTest {

    private lateinit var db: FieldTripDatabase
    private lateinit var submit: SubmitBookingUseCase
    private lateinit var confirm: ConfirmBookingUseCase
    private lateinit var bookingRepo: BookingRepositoryImpl
    private lateinit var feeRepo: FeeItemRepositoryImpl
    private lateinit var inventoryRepo: InventoryRepositoryImpl
    private lateinit var itineraryRepo: ItineraryRepositoryImpl
    private lateinit var session: SessionManager

    private val slotId = "slot-1"
    private val tripStart = Instant.now().plusSeconds(7 * 86400)
    private val tripEnd = tripStart.plusSeconds(86400)

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FieldTripDatabase::class.java
        ).allowMainThreadQueries().build()

        itineraryRepo = ItineraryRepositoryImpl(db.itineraryDraftDao(), NoopSensitiveFieldCodec())
        bookingRepo = BookingRepositoryImpl(db.bookingOrderDao())
        inventoryRepo = InventoryRepositoryImpl(db, db.inventorySlotDao(), db.quotaLedgerDao())
        feeRepo = FeeItemRepositoryImpl(db.feeItemDao(), db.bookingOrderDao())
        session = SessionManager()

        val logger: AuditLogger = mockk(relaxed = true)
        submit = SubmitBookingUseCase(db, itineraryRepo, bookingRepo, inventoryRepo, logger, session)
        confirm = ConfirmBookingUseCase(db, bookingRepo, inventoryRepo, feeRepo, logger, session)

        runBlocking {
            db.userDao().insert(
                UserEntity("alice", "alice", "Alice", true,
                    System.currentTimeMillis(), System.currentTimeMillis())
            )
            db.userDao().insert(
                UserEntity("agent-1", "agent", "Agent A", true,
                    System.currentTimeMillis(), System.currentTimeMillis())
            )
            db.inventorySlotDao().upsert(
                InventorySlotEntity(
                    id = slotId,
                    itineraryType = "standard",
                    serviceName = "Standard Tour",
                    startDateEpochDay = LocalDate.now().plusDays(7).toEpochDay(),
                    endDateEpochDay = LocalDate.now().plusDays(8).toEpochDay(),
                    totalQuota = 10,
                    reservedCount = 0, bookedCount = 0,
                    allowExceptionBooking = false
                )
            )
        }
    }

    @After fun teardown() { db.close() }

    @Test
    fun `submit then confirm transitions booking to Booked with paidTotal`() = runTest {
        session.set(SessionContext("alice", "Alice", setOf(Role.Traveler), "s1"))
        val draft = ItineraryDraft(
            id = UUID.randomUUID().toString(),
            travelerId = "",
            travelerInitials = "A",
            partySize = 2,
            startDate = LocalDate.now().plusDays(7),
            endDate = LocalDate.now().plusDays(8),
            notes = null,
            itineraryType = "standard",
            createdAt = Instant.now(), updatedAt = Instant.now(),
            submitted = false
        )
        itineraryRepo.save(draft)
        val submitResult = submit.execute(draft.id, slotId)
        assertThat(submitResult).isInstanceOf(SubmitBookingUseCase.Result.Submitted::class.java)
        val orderId = (submitResult as SubmitBookingUseCase.Result.Submitted).order.id

        // Switch to agent for confirm
        session.set(SessionContext("agent-1", "Agent A", setOf(Role.Agent), "s2"))
        val fees = listOf(
            FeeItem(id = UUID.randomUUID().toString(), bookingOrderId = orderId,
                category = FeeCategory.BASE_FARE, description = "Base",
                amountUsd = BigDecimal("50.00"), sortOrder = 0),
            FeeItem(id = UUID.randomUUID().toString(), bookingOrderId = orderId,
                category = FeeCategory.TAX_FEE, description = "Tax",
                amountUsd = BigDecimal("5.00"), sortOrder = 1)
        )
        val confirmResult = confirm.execute(orderId, fees)
        assertThat(confirmResult).isInstanceOf(ConfirmBookingUseCase.Result.Confirmed::class.java)

        val persisted = bookingRepo.findById(orderId)!!
        assertThat(persisted.state).isEqualTo(BookingState.Booked)
        assertThat(persisted.paidTotal).isEqualTo(BigDecimal("55.00"))
    }

    @Test
    fun `confirm fails when booking already in terminal state`() = runTest {
        session.set(SessionContext("alice", "Alice", setOf(Role.Traveler), "s1"))
        val draft = ItineraryDraft(
            id = UUID.randomUUID().toString(),
            travelerId = "",
            travelerInitials = "A",
            partySize = 1,
            startDate = LocalDate.now().plusDays(7),
            endDate = LocalDate.now().plusDays(8),
            notes = null,
            itineraryType = "standard",
            createdAt = Instant.now(), updatedAt = Instant.now(),
            submitted = false
        )
        itineraryRepo.save(draft)
        val submitted = submit.execute(draft.id, slotId) as SubmitBookingUseCase.Result.Submitted

        // Force-transition to Closed by updating state (simulating terminal)
        bookingRepo.updateState(submitted.order.id, BookingState.Cancelled, Instant.now(), "system", "test")

        session.set(SessionContext("agent-1", "Agent A", setOf(Role.Agent), "s2"))
        val result = confirm.execute(submitted.order.id, emptyList())
        assertThat(result).isInstanceOf(ConfirmBookingUseCase.Result.InvalidState::class.java)
    }

    @Test
    fun `unauthorized confirmation by traveler is rejected`() = runTest {
        session.set(SessionContext("alice", "Alice", setOf(Role.Traveler), "s1"))
        val draft = ItineraryDraft(
            id = UUID.randomUUID().toString(),
            travelerId = "",
            travelerInitials = "A",
            partySize = 1,
            startDate = LocalDate.now().plusDays(7),
            endDate = LocalDate.now().plusDays(8),
            notes = null,
            itineraryType = "standard",
            createdAt = Instant.now(), updatedAt = Instant.now(),
            submitted = false
        )
        itineraryRepo.save(draft)
        val submitted = submit.execute(draft.id, slotId) as SubmitBookingUseCase.Result.Submitted

        try {
            confirm.execute(submitted.order.id, emptyList())
            assert(false) { "Should have thrown UnauthorizedException" }
        } catch (_: SecurityException) {
            // expected — travelers cannot confirm
        }
    }
}
