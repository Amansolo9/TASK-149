package com.fieldtripops.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class InventorySlotTest {

    private fun slot(total: Int, reserved: Int, booked: Int) = InventorySlot(
        id = "s1",
        itineraryType = "standard",
        serviceName = "Test",
        startDate = LocalDate.of(2026, 5, 1),
        endDate = LocalDate.of(2026, 5, 5),
        totalQuota = total,
        reservedCount = reserved,
        bookedCount = booked,
        allowExceptionBooking = false
    )

    @Test
    fun `available equals total minus reserved and booked`() {
        assertThat(slot(10, 2, 3).availableCount).isEqualTo(5)
    }

    @Test
    fun `available never negative`() {
        assertThat(slot(5, 3, 5).availableCount).isEqualTo(0)
    }

    @Test
    fun `canAccept true when enough`() {
        assertThat(slot(10, 0, 0).canAccept(5)).isTrue()
    }

    @Test
    fun `canAccept false when insufficient`() {
        assertThat(slot(10, 8, 1).canAccept(5)).isFalse()
    }
}
