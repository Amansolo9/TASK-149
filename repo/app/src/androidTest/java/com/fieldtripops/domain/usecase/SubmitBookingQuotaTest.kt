package com.fieldtripops.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.entity.InventorySlotEntity
import com.fieldtripops.data.repository.BookingRepositoryImpl
import com.fieldtripops.data.repository.InventoryRepositoryImpl
import com.fieldtripops.data.repository.ItineraryRepositoryImpl
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
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SubmitBookingQuotaTest {

    private lateinit var db: FieldTripDatabase
    private lateinit var submitUseCase: SubmitBookingUseCase
    private lateinit var itineraryRepo: ItineraryRepositoryImpl
    private lateinit var bookingRepo: BookingRepositoryImpl
    private lateinit var inventoryRepo: InventoryRepositoryImpl
    private lateinit var session: SessionManager

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FieldTripDatabase::class.java
        ).allowMainThreadQueries().build()
        itineraryRepo = ItineraryRepositoryImpl(db.itineraryDraftDao(), NoopSensitiveFieldCodec())
        bookingRepo = BookingRepositoryImpl(db.bookingOrderDao())
        inventoryRepo = InventoryRepositoryImpl(db, db.inventorySlotDao(), db.quotaLedgerDao())
        session = SessionManager().also {
            it.set(SessionContext("alice", "Alice", setOf(Role.Traveler), "s1"))
        }
        val logger: AuditLogger = mockk(relaxed = true)
        submitUseCase = SubmitBookingUseCase(db, itineraryRepo, bookingRepo, inventoryRepo, logger, session)

        runBlocking {
            db.userDao().insert(
                com.fieldtripops.data.entity.UserEntity(
                    "alice", "alice", "Alice", true,
                    System.currentTimeMillis(), System.currentTimeMillis()
                )
            )
            db.inventorySlotDao().upsert(
                InventorySlotEntity(
                    id = "slot-1", itineraryType = "standard",
                    serviceName = "Test", startDateEpochDay = LocalDate.now().plusDays(30).toEpochDay(),
                    endDateEpochDay = LocalDate.now().plusDays(33).toEpochDay(),
                    totalQuota = 2, reservedCount = 0, bookedCount = 0,
                    allowExceptionBooking = false
                )
            )
        }
    }
    @After fun teardown() { db.close() }

    private suspend fun draft(party: Int): ItineraryDraft {
        val d = ItineraryDraft(
            id = "d1", travelerId = "alice", travelerInitials = "AA",
            partySize = party, startDate = LocalDate.now().plusDays(30),
            endDate = LocalDate.now().plusDays(33), notes = null,
            itineraryType = "standard",
            createdAt = Instant.now(), updatedAt = Instant.now(), submitted = false
        )
        itineraryRepo.save(d)
        return d
    }

    @Test
    fun `submit with available quota succeeds and persists trip window`() = runTest {
        val d = draft(2)
        val r = submitUseCase.execute(d.id, "slot-1") as SubmitBookingUseCase.Result.Submitted
        assertThat(r.order.state.name).isEqualTo("PendingConfirmation")
        // Trip window copied from the slot, not from wall-clock.
        assertThat(r.order.tripStartAt).isNotEqualTo(r.order.createdAt)
    }

    @Test
    fun `submit when sold-out reports remaining and seat shortage`() = runTest {
        val d = draft(3)
        val r = submitUseCase.execute(d.id, "slot-1")
        assertThat(r).isInstanceOf(SubmitBookingUseCase.Result.QuotaUnavailable::class.java)
        val q = r as SubmitBookingUseCase.Result.QuotaUnavailable
        assertThat(q.remaining).isEqualTo(2)
        assertThat(q.requested).isEqualTo(3)
    }

    @Test
    fun `repeated submit does not create a second active booking`() = runTest {
        val d = draft(1)
        val first = submitUseCase.execute(d.id, "slot-1")
        assertThat(first).isInstanceOf(SubmitBookingUseCase.Result.Submitted::class.java)
        val second = submitUseCase.execute(d.id, "slot-1")
        assertThat(second).isEqualTo(SubmitBookingUseCase.Result.DuplicateSubmission)
    }

    @Test
    fun `quota not consumed when submission is rejected`() = runTest {
        val d = draft(5) // bigger than totalQuota
        submitUseCase.execute(d.id, "slot-1")
        val slot = inventoryRepo.findSlot("slot-1")!!
        assertThat(slot.reservedCount).isEqualTo(0)
    }
}
