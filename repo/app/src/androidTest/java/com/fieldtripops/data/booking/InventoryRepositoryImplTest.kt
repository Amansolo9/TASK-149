package com.fieldtripops.data.booking

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.repository.InventoryRepositoryImpl
import com.fieldtripops.domain.model.InventorySlot
import com.fieldtripops.domain.model.QuotaOperation
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class InventoryRepositoryImplTest {

    private lateinit var db: FieldTripDatabase
    private lateinit var repo: InventoryRepositoryImpl

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FieldTripDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = InventoryRepositoryImpl(db, db.inventorySlotDao(), db.quotaLedgerDao())
    }

    @After
    fun teardown() { db.close() }

    private val baseSlot = InventorySlot(
        id = "slot-1",
        itineraryType = "standard",
        serviceName = "Test Tour",
        startDate = LocalDate.of(2026, 6, 1),
        endDate = LocalDate.of(2026, 6, 5),
        totalQuota = 10,
        reservedCount = 0,
        bookedCount = 0,
        allowExceptionBooking = false
    )

    @Test
    fun reserve_decrements_available() = runTest {
        repo.saveSlot(baseSlot)
        repo.applyQuotaOperation("slot-1", QuotaOperation.RESERVE, 3, "b1", "u1")
        val slot = repo.findSlot("slot-1")!!
        assertThat(slot.reservedCount).isEqualTo(3)
        assertThat(slot.availableCount).isEqualTo(7)
    }

    @Test
    fun confirm_moves_reserved_to_booked() = runTest {
        repo.saveSlot(baseSlot)
        repo.applyQuotaOperation("slot-1", QuotaOperation.RESERVE, 3, "b1", "u1")
        repo.applyQuotaOperation("slot-1", QuotaOperation.CONFIRM, 3, "b1", "u1")
        val slot = repo.findSlot("slot-1")!!
        assertThat(slot.reservedCount).isEqualTo(0)
        assertThat(slot.bookedCount).isEqualTo(3)
    }

    @Test
    fun release_after_reserve_decrements_reserved() = runTest {
        repo.saveSlot(baseSlot)
        repo.applyQuotaOperation("slot-1", QuotaOperation.RESERVE, 4, "b1", "u1")
        repo.applyQuotaOperation("slot-1", QuotaOperation.RELEASE, 4, "b1", "u1")
        val slot = repo.findSlot("slot-1")!!
        assertThat(slot.reservedCount).isEqualTo(0)
        assertThat(slot.bookedCount).isEqualTo(0)
    }

    @Test
    fun release_after_confirm_decrements_booked() = runTest {
        repo.saveSlot(baseSlot)
        repo.applyQuotaOperation("slot-1", QuotaOperation.RESERVE, 4, "b1", "u1")
        repo.applyQuotaOperation("slot-1", QuotaOperation.CONFIRM, 4, "b1", "u1")
        repo.applyQuotaOperation("slot-1", QuotaOperation.RELEASE, 4, "b1", "u1")
        val slot = repo.findSlot("slot-1")!!
        assertThat(slot.reservedCount).isEqualTo(0)
        assertThat(slot.bookedCount).isEqualTo(0)
    }

    @Test(expected = IllegalStateException::class)
    fun reserve_beyond_quota_throws() = runTest {
        repo.saveSlot(baseSlot.copy(totalQuota = 2))
        repo.applyQuotaOperation("slot-1", QuotaOperation.RESERVE, 5, "b1", "u1")
    }

    @Test(expected = IllegalStateException::class)
    fun exception_booking_without_permission_throws() = runTest {
        repo.saveSlot(baseSlot.copy(totalQuota = 0))
        repo.applyQuotaOperation("slot-1", QuotaOperation.EXCEPTION, 1, "b1", "agent")
    }

    @Test
    fun exception_booking_with_permission_increments_booked() = runTest {
        repo.saveSlot(baseSlot.copy(totalQuota = 0, allowExceptionBooking = true))
        repo.applyQuotaOperation("slot-1", QuotaOperation.EXCEPTION, 1, "b1", "agent")
        val slot = repo.findSlot("slot-1")!!
        assertThat(slot.bookedCount).isEqualTo(1)
    }

    @Test
    fun ledger_records_all_operations() = runTest {
        repo.saveSlot(baseSlot)
        repo.applyQuotaOperation("slot-1", QuotaOperation.RESERVE, 2, "b1", "u1")
        repo.applyQuotaOperation("slot-1", QuotaOperation.CONFIRM, 2, "b1", "u1")
        val ledger = repo.getLedgerForBooking("b1")
        assertThat(ledger).hasSize(2)
        assertThat(ledger.map { it.operation })
            .containsExactly(QuotaOperation.RESERVE, QuotaOperation.CONFIRM).inOrder()
    }
}
