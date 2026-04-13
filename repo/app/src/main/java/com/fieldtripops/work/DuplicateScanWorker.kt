package com.fieldtripops.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fieldtripops.domain.usecase.RunDuplicateScanUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Scheduled duplicate scan — should run only when idle per PRD §15.
 * Configure as Constraints.Builder().setRequiresDeviceIdle(true) via WorkManager.
 */
class DuplicateScanWorker(
    context: Context, params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val useCase: RunDuplicateScanUseCase by inject()

    override suspend fun doWork(): Result = try {
        useCase.execute()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val WORK_NAME = "duplicate_scan_worker"
    }
}
