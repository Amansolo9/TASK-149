package com.fieldtripops.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.fieldtripops.domain.repository.SlaReminderRepository
import com.fieldtripops.domain.usecase.AutoCloseStalePendingUseCase
import com.fieldtripops.domain.usecase.AutoCloseWaitingTicketsUseCase
import com.fieldtripops.domain.usecase.GenerateSlaRemindersUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Behavior-level tests of `doWork()` for periodic workers using
 * `TestListenableWorkerBuilder`. Verifies that each worker:
 *  - Calls its injected use case
 *  - Returns Result.success() on normal completion
 *  - Returns Result.retry() when the use case throws
 *
 * These are instrumented tests (require Android runtime for WorkManager).
 */
@RunWith(AndroidJUnit4::class)
class WorkerBehaviorTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        stopKoin() // Ensure a clean slate in case app Koin has started
    }

    private fun startKoinWith(vararg beans: org.koin.core.module.Module) {
        stopKoin()
        startKoin { modules(*beans) }
    }

    @Test
    fun pendingConfirmationTimeoutWorker_success_when_usecase_completes() = runBlocking {
        val autoClose: AutoCloseStalePendingUseCase = mockk()
        coEvery { autoClose.execute() } returns AutoCloseStalePendingUseCase.Result(closedCount = 3)

        startKoinWith(module { single { autoClose } })

        val worker = TestListenableWorkerBuilder<PendingConfirmationTimeoutWorker>(context).build()
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        coVerify { autoClose.execute() }
    }

    @Test
    fun pendingConfirmationTimeoutWorker_retry_on_failure() = runBlocking {
        val autoClose: AutoCloseStalePendingUseCase = mockk()
        coEvery { autoClose.execute() } throws RuntimeException("db down")

        startKoinWith(module { single { autoClose } })

        val worker = TestListenableWorkerBuilder<PendingConfirmationTimeoutWorker>(context).build()
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.retry())
    }

    @Test
    fun waitingTicketTimeoutWorker_success_path_calls_usecase() = runBlocking {
        val autoClose: AutoCloseWaitingTicketsUseCase = mockk()
        coEvery { autoClose.execute() } returns AutoCloseWaitingTicketsUseCase.Result(closedCount = 0, thresholdHours = 72)

        startKoinWith(module { single { autoClose } })

        val worker = TestListenableWorkerBuilder<WaitingTicketTimeoutWorker>(context).build()
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        coVerify { autoClose.execute() }
    }

    @Test
    fun slaReminderWorker_success_calls_generate_usecase() = runBlocking {
        val generate: GenerateSlaRemindersUseCase = mockk()
        val reminders: SlaReminderRepository = mockk(relaxed = true)
        coEvery { generate.execute(any()) } returns GenerateSlaRemindersUseCase.Result(generated = 0, slaConfigVersion = 0L)

        startKoinWith(module {
            single { generate }
            single { reminders }
        })

        val worker = TestListenableWorkerBuilder<SlaReminderWorker>(context).build()
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        coVerify { generate.execute(any()) }
    }

    @Test
    fun slaReminderWorker_retry_on_usecase_failure() = runBlocking {
        val generate: GenerateSlaRemindersUseCase = mockk()
        val reminders: SlaReminderRepository = mockk(relaxed = true)
        coEvery { generate.execute(any()) } throws RuntimeException("boom")

        startKoinWith(module {
            single { generate }
            single { reminders }
        })

        val worker = TestListenableWorkerBuilder<SlaReminderWorker>(context).build()
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.retry())
    }
}
