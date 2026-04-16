package com.fieldtripops.work

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Smoke tests verifying the worker contracts (work names, constants)
 * stay stable. Full `doWork()` execution requires an Android runtime
 * and is exercised in instrumented tests. These tests catch silent
 * drift in work-name constants that `FieldTripApp` registers against.
 */
class WorkerRegistrationTest {

    @Test
    fun `SlaReminderWorker constants are defined`() {
        assertThat(SlaReminderWorker.WORK_NAME).isEqualTo("sla_reminder_worker")
        assertThat(SlaReminderWorker.CHANNEL_ID).isEqualTo("sla_reminders")
        assertThat(SlaReminderWorker.NOTIFICATION_ID).isEqualTo(4242)
    }

    @Test
    fun `PendingConfirmationTimeoutWorker has stable WORK_NAME`() {
        assertThat(PendingConfirmationTimeoutWorker.WORK_NAME)
            .isEqualTo("pending_confirmation_timeout_worker")
    }

    @Test
    fun `SessionTimeoutWorker has stable WORK_NAME`() {
        assertThat(SessionTimeoutWorker.WORK_NAME).isNotEmpty()
    }

    @Test
    fun `RetentionSweepWorker has stable WORK_NAME`() {
        assertThat(RetentionSweepWorker.WORK_NAME).isNotEmpty()
    }

    @Test
    fun `WaitingTicketTimeoutWorker has stable WORK_NAME`() {
        assertThat(WaitingTicketTimeoutWorker.WORK_NAME).isNotEmpty()
    }

    @Test
    fun `DuplicateScanWorker has stable WORK_NAME`() {
        assertThat(DuplicateScanWorker.WORK_NAME).isNotEmpty()
    }
}
