package com.fieldtripops.domain.booking

import com.fieldtripops.domain.model.Role
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RescheduleValidatorTest {

    @Test
    fun `more than 24h lead time is valid for any role`() {
        val now = Instant.now()
        val tripStart = now.plus(Duration.ofDays(3))
        val r = RescheduleValidator.validate(tripStart, now, listOf(Role.Traveler), null)
        assertThat(r).isEqualTo(RescheduleValidator.Result.Valid)
    }

    @Test
    fun `under 24h traveler cannot reschedule`() {
        val now = Instant.now()
        val tripStart = now.plus(Duration.ofHours(12))
        val r = RescheduleValidator.validate(tripStart, now, listOf(Role.Traveler), null)
        assertThat(r).isInstanceOf(RescheduleValidator.Result.Invalid::class.java)
    }

    @Test
    fun `under 24h agent requires exception reason`() {
        val now = Instant.now()
        val tripStart = now.plus(Duration.ofHours(12))
        val r = RescheduleValidator.validate(tripStart, now, listOf(Role.Agent), null)
        assertThat(r).isInstanceOf(RescheduleValidator.Result.RequiresException::class.java)
    }

    @Test
    fun `under 24h agent with reason is valid`() {
        val now = Instant.now()
        val tripStart = now.plus(Duration.ofHours(12))
        val r = RescheduleValidator.validate(
            tripStart, now, listOf(Role.Agent), "Weather emergency"
        )
        assertThat(r).isEqualTo(RescheduleValidator.Result.Valid)
    }

    @Test
    fun `under 24h admin with reason is valid`() {
        val now = Instant.now()
        val tripStart = now.plus(Duration.ofHours(5))
        val r = RescheduleValidator.validate(
            tripStart, now, listOf(Role.Administrator), "Medical emergency"
        )
        assertThat(r).isEqualTo(RescheduleValidator.Result.Valid)
    }

    @Test
    fun `past trip start is invalid`() {
        val now = Instant.now()
        val tripStart = now.minus(Duration.ofHours(1))
        val r = RescheduleValidator.validate(tripStart, now, listOf(Role.Agent), "reason")
        assertThat(r).isInstanceOf(RescheduleValidator.Result.Invalid::class.java)
    }
}
