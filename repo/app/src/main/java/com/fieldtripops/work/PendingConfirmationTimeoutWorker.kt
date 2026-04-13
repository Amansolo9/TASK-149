package com.fieldtripops.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fieldtripops.domain.usecase.AutoCloseStalePendingUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Periodically closes PendingConfirmation bookings that have been inactive
 * for more than 30 minutes, releasing held quota per PRD §9.3.
 */
class PendingConfirmationTimeoutWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val autoCloseUseCase: AutoCloseStalePendingUseCase by inject()

    override suspend fun doWork(): Result = try {
        autoCloseUseCase.execute()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val WORK_NAME = "pending_confirmation_timeout_worker"
    }
}
