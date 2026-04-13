package com.fieldtripops

import android.app.Application
import com.fieldtripops.BuildConfig
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.seed.SeedData
import com.fieldtripops.di.allModules
import com.fieldtripops.domain.repository.RefundRuleRepository
import com.fieldtripops.security.SessionConfig
import com.fieldtripops.work.DuplicateScanWorker
import com.fieldtripops.work.PendingConfirmationTimeoutWorker
import com.fieldtripops.work.RetentionSweepWorker
import com.fieldtripops.work.SessionTimeoutWorker
import com.fieldtripops.work.SlaReminderWorker
import com.fieldtripops.work.WaitingTicketTimeoutWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import java.util.concurrent.TimeUnit

class FieldTripApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@FieldTripApp)
            modules(allModules)
        }

        // Demo credentials are seeded ONLY in debug builds. Production releases
        // never auto-provision predictable users (audit finding #7).
        if (BuildConfig.DEBUG) {
            seedDatabase()
        }
        seedRefundRules()
        enqueueAllWorkers()
    }

    private fun seedDatabase() {
        appScope.launch {
            val db: FieldTripDatabase = get()
            SeedData.populate(db)
        }
    }

    /**
     * Seed default refund rules into persistence on first launch (any build
     * type). Rules then become admin-editable via UpdateRefundRuleUseCase.
     */
    private fun seedRefundRules() {
        appScope.launch {
            val refundRules: RefundRuleRepository = get()
            refundRules.seedDefaultsIfEmpty()
        }
    }

    private fun enqueueAllWorkers() {
        val wm = WorkManager.getInstance(this)

        wm.enqueueUniquePeriodicWork(
            SessionTimeoutWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SessionTimeoutWorker>(
                SessionConfig.SESSION_CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES
            ).build()
        )

        wm.enqueueUniquePeriodicWork(
            PendingConfirmationTimeoutWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PendingConfirmationTimeoutWorker>(
                15L, TimeUnit.MINUTES
            ).build()
        )

        wm.enqueueUniquePeriodicWork(
            WaitingTicketTimeoutWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WaitingTicketTimeoutWorker>(
                1L, TimeUnit.HOURS
            ).build()
        )

        // On-device SLA reminder generation. Runs every 30 minutes so pre-breach
        // reminders fire well ahead of short SLAs without spamming the device.
        wm.enqueueUniquePeriodicWork(
            SlaReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SlaReminderWorker>(
                30L, TimeUnit.MINUTES
            ).build()
        )

        // Idle-only constraints per PRD §15
        val idleConstraints = Constraints.Builder()
            .setRequiresDeviceIdle(true)
            .build()

        wm.enqueueUniquePeriodicWork(
            DuplicateScanWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DuplicateScanWorker>(6L, TimeUnit.HOURS)
                .setConstraints(idleConstraints).build()
        )

        // Charging + idle for retention
        val maintenanceConstraints = Constraints.Builder()
            .setRequiresDeviceIdle(true)
            .setRequiresCharging(true)
            .build()

        wm.enqueueUniquePeriodicWork(
            RetentionSweepWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RetentionSweepWorker>(24L, TimeUnit.HOURS)
                .setConstraints(maintenanceConstraints).build()
        )
    }
}
