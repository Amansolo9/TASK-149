package com.fieldtripops.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fieldtripops.R
import com.fieldtripops.domain.repository.SlaReminderRepository
import com.fieldtripops.domain.usecase.GenerateSlaRemindersUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Periodic worker that evaluates open tickets against the persisted SLA
 * configuration and generates pre-breach / breach reminders.
 *
 * After evaluation it reads any newly generated rows from the reminder
 * repository and posts a local (on-device only) notification to surface them
 * to reviewers/admins. No network activity occurs.
 */
class SlaReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val generate: GenerateSlaRemindersUseCase by inject()
    private val reminders: SlaReminderRepository by inject()

    override suspend fun doWork(): Result = try {
        val result = generate.execute()
        if (result.generated > 0) {
            postLocalNotification(result.generated)
        }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    private fun postLocalNotification(count: Int) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "SLA Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "On-device SLA pre-breach/breach reminders for claims."
            }
            mgr.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("SLA attention required")
            .setContentText("$count claim(s) approaching or past SLA")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
        try {
            mgr.notify(NOTIFICATION_ID, notif)
        } catch (_: SecurityException) {
            // Notification permission may be declined on API 33+. Rows are
            // still persisted; in-app UI surface is the source of truth.
        }
    }

    companion object {
        const val WORK_NAME = "sla_reminder_worker"
        const val CHANNEL_ID = "sla_reminders"
        const val NOTIFICATION_ID = 4242
    }
}
