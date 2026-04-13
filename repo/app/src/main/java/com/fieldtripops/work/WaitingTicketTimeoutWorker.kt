package com.fieldtripops.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fieldtripops.domain.usecase.AutoCloseWaitingTicketsUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WaitingTicketTimeoutWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val autoClose: AutoCloseWaitingTicketsUseCase by inject()

    override suspend fun doWork(): Result = try {
        autoClose.execute()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val WORK_NAME = "waiting_ticket_timeout_worker"
    }
}
