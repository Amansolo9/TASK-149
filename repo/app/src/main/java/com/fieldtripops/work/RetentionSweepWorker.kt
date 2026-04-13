package com.fieldtripops.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fieldtripops.domain.usecase.RetentionSweepUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Periodic retention sweep — runs only when idle/charging per PRD §15.
 * Configure with Constraints.Builder().setRequiresDeviceIdle(true).setRequiresCharging(true).
 */
class RetentionSweepWorker(
    context: Context, params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val useCase: RetentionSweepUseCase by inject()

    override suspend fun doWork(): Result = try {
        useCase.execute()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val WORK_NAME = "retention_sweep_worker"
    }
}
