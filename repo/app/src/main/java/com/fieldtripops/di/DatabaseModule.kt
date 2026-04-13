package com.fieldtripops.di

import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.db.migration.Migrations
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single { Migrations.buildDatabase(androidContext()) }

    // Phase 1
    single { get<FieldTripDatabase>().userDao() }
    single { get<FieldTripDatabase>().roleAssignmentDao() }
    single { get<FieldTripDatabase>().credentialDao() }
    single { get<FieldTripDatabase>().consentRecordDao() }
    single { get<FieldTripDatabase>().sessionAuditDao() }
    single { get<FieldTripDatabase>().auditLogDao() }
    single { get<FieldTripDatabase>().attachmentRefDao() }

    // Phase 2
    single { get<FieldTripDatabase>().itineraryDraftDao() }
    single { get<FieldTripDatabase>().bookingOrderDao() }
    single { get<FieldTripDatabase>().inventorySlotDao() }
    single { get<FieldTripDatabase>().quotaLedgerDao() }
    single { get<FieldTripDatabase>().feeItemDao() }

    // Phase 3
    single { get<FieldTripDatabase>().rescheduleRequestDao() }
    single { get<FieldTripDatabase>().refundDecisionDao() }
    single { get<FieldTripDatabase>().claimTicketDao() }
    single { get<FieldTripDatabase>().ticketStatusHistoryDao() }
    single { get<FieldTripDatabase>().investigationNoteDao() }
    single { get<FieldTripDatabase>().appealRecordDao() }

    // Phase 4
    single { get<FieldTripDatabase>().contentItemDao() }
    single { get<FieldTripDatabase>().contentMetricDao() }
    single { get<FieldTripDatabase>().governanceDecisionDao() }
    single { get<FieldTripDatabase>().recommendationSuppressionDao() }
    single { get<FieldTripDatabase>().duplicateClusterDao() }
    single { get<FieldTripDatabase>().transactionCheckpointDao() }

    // Phase 5
    single { get<FieldTripDatabase>().offlineQueueItemDao() }
    single { get<FieldTripDatabase>().exportPackageDao() }

    // Phase 6 — SLA config
    single { get<FieldTripDatabase>().slaConfigDao() }

    // Phase 7 — Deletion workflow, SLA reminders, refund rules
    single { get<FieldTripDatabase>().deletionRequestDao() }
    single { get<FieldTripDatabase>().slaReminderDao() }
    single { get<FieldTripDatabase>().refundRuleDao() }
}
