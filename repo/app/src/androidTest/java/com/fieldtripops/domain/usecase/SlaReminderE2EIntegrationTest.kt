package com.fieldtripops.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.entity.ClaimTicketEntity
import com.fieldtripops.data.repository.SlaConfigRepositoryImpl
import com.fieldtripops.data.repository.SlaReminderRepositoryImpl
import com.fieldtripops.domain.model.SlaReminderKind
import com.fieldtripops.domain.sla.SlaConfig
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/**
 * E2E SLA reminder generation against real Room DB and real
 * SlaConfig + SlaReminder repositories. Validates:
 *  - Pre-breach reminder generated when time-remaining drops below threshold
 *  - Breach reminder generated when SLA exceeded
 *  - Dedup on second invocation (no duplicate rows)
 */
@RunWith(AndroidJUnit4::class)
class SlaReminderE2EIntegrationTest {

    private lateinit var db: FieldTripDatabase
    private lateinit var slaConfigRepo: SlaConfigRepositoryImpl
    private lateinit var slaReminderRepo: SlaReminderRepositoryImpl
    private lateinit var useCase: GenerateSlaRemindersUseCase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FieldTripDatabase::class.java
        ).allowMainThreadQueries().build()
        slaConfigRepo = SlaConfigRepositoryImpl(db, db.slaConfigDao())
        slaReminderRepo = SlaReminderRepositoryImpl(db.slaReminderDao())
        val logger: AuditLogger = mockk(relaxed = true)
        useCase = GenerateSlaRemindersUseCase(
            slaConfigRepo, slaReminderRepo, db.claimTicketDao(), logger
        )

        // Elapsed-time mode (0–24 work window, no weekend exclusion) so tests
        // are deterministic regardless of day-of-week.
        runBlocking {
            slaConfigRepo.save(
                SlaConfig(
                    firstResponseMinutes = 240,
                    resolutionMinutes = 4320,
                    travelerNoResponseHours = 72,
                    updatedAt = Instant.now(),
                    updatedBy = "system",
                    workDayStartHour = 0,
                    workDayEndHour = 24,
                    excludeWeekends = false
                )
            )
        }
    }

    @After fun teardown() { db.close() }

    private fun insertSubmittedTicket(id: String, createdAt: Instant) {
        runBlocking {
            db.claimTicketDao().upsert(
                ClaimTicketEntity(
                    id = id, travelerId = "alice", bookingOrderId = "b1",
                    claimStyle = "REFUND_ONLY",
                    classification = "PROVIDER_NO_SHOW",
                    responsibility = "PROVIDER",
                    description = "desc",
                    state = "Submitted",
                    createdAt = createdAt.toEpochMilli(),
                    updatedAt = createdAt.toEpochMilli(),
                    firstResponseAt = null, resolvedAt = null, closedAt = null,
                    lastTravelerActivityAt = createdAt.toEpochMilli(),
                    compensationAmountCents = null, compensationCurrency = null,
                    compensationBasis = null, compensationApproverId = null,
                    compensationApproverName = null, compensationDecidedAt = null,
                    compensationNote = null
                )
            )
        }
    }

    @Test
    fun `pre-breach reminder generated near SLA deadline`() = runTest {
        // Ticket created 3h 10min ago; 240m SLA → 50 min remaining, well within 25% lead
        val created = Instant.now().minus(Duration.ofMinutes(190))
        insertSubmittedTicket("t1", created)

        val result = useCase.execute()
        assertThat(result.generated).isAtLeast(1)

        val reminders = slaReminderRepo.getByTicket("t1")
        assertThat(reminders.any { it.kind == SlaReminderKind.FIRST_RESPONSE_PRE_BREACH }).isTrue()
    }

    @Test
    fun `breach reminder generated when past first-response SLA`() = runTest {
        // Ticket created 5 hours ago; 240m SLA breached already
        val created = Instant.now().minus(Duration.ofHours(5))
        insertSubmittedTicket("t2", created)

        val result = useCase.execute()
        assertThat(result.generated).isAtLeast(1)

        val reminders = slaReminderRepo.getByTicket("t2")
        assertThat(reminders.any { it.kind == SlaReminderKind.FIRST_RESPONSE_BREACHED }).isTrue()
    }

    @Test
    fun `dedup prevents duplicate reminders on re-invocation`() = runTest {
        val created = Instant.now().minus(Duration.ofHours(5))
        insertSubmittedTicket("t3", created)

        useCase.execute()
        val firstCount = slaReminderRepo.getByTicket("t3").size
        useCase.execute()
        val secondCount = slaReminderRepo.getByTicket("t3").size

        assertThat(secondCount).isEqualTo(firstCount)
    }

    @Test
    fun `no reminder generated for fresh ticket well within SLA`() = runTest {
        val created = Instant.now().minus(Duration.ofMinutes(10))
        insertSubmittedTicket("t4", created)

        useCase.execute()
        val reminders = slaReminderRepo.getByTicket("t4")
        // 10/240 = 4% consumed; pre-breach fires at 75% consumed, so none yet.
        assertThat(reminders.none { it.kind == SlaReminderKind.FIRST_RESPONSE_PRE_BREACH }).isTrue()
    }
}
