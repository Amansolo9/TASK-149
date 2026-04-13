package com.fieldtripops.data.db.migration

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fieldtripops.data.db.FieldTripDatabase

object Migrations {

    /** v1 → v2: Phase 2 booking, inventory, quota ledger, fee items. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `itinerary_drafts` (
                    `id` TEXT NOT NULL PRIMARY KEY, `travelerId` TEXT NOT NULL,
                    `travelerInitials` TEXT NOT NULL, `partySize` INTEGER NOT NULL,
                    `startDateEpochDay` INTEGER NOT NULL, `endDateEpochDay` INTEGER NOT NULL,
                    `notes` TEXT, `itineraryType` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                    `submitted` INTEGER NOT NULL)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_itinerary_drafts_travelerId_createdAt` ON `itinerary_drafts` (`travelerId`, `createdAt`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `booking_orders` (
                    `id` TEXT NOT NULL PRIMARY KEY, `itineraryId` TEXT NOT NULL,
                    `travelerId` TEXT NOT NULL, `inventorySlotId` TEXT NOT NULL,
                    `partySize` INTEGER NOT NULL, `state` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                    `confirmedAt` INTEGER, `confirmedBy` TEXT,
                    `cancelledAt` INTEGER, `cancelReason` TEXT,
                    `lastActivityAt` INTEGER NOT NULL)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_booking_orders_state_updatedAt` ON `booking_orders` (`state`, `updatedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_booking_orders_travelerId_createdAt` ON `booking_orders` (`travelerId`, `createdAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_booking_orders_state_lastActivityAt` ON `booking_orders` (`state`, `lastActivityAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_booking_orders_itineraryId` ON `booking_orders` (`itineraryId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_booking_orders_inventorySlotId` ON `booking_orders` (`inventorySlotId`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `inventory_slots` (
                    `id` TEXT NOT NULL PRIMARY KEY, `itineraryType` TEXT NOT NULL,
                    `serviceName` TEXT NOT NULL, `startDateEpochDay` INTEGER NOT NULL,
                    `endDateEpochDay` INTEGER NOT NULL, `totalQuota` INTEGER NOT NULL,
                    `reservedCount` INTEGER NOT NULL, `bookedCount` INTEGER NOT NULL,
                    `allowExceptionBooking` INTEGER NOT NULL)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_slots_itineraryType_startDateEpochDay` ON `inventory_slots` (`itineraryType`, `startDateEpochDay`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `quota_ledger` (
                    `id` TEXT NOT NULL PRIMARY KEY, `inventorySlotId` TEXT NOT NULL,
                    `bookingOrderId` TEXT NOT NULL, `operation` TEXT NOT NULL,
                    `units` INTEGER NOT NULL, `actor` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL, `reason` TEXT)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_quota_ledger_inventorySlotId_timestamp` ON `quota_ledger` (`inventorySlotId`, `timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_quota_ledger_bookingOrderId` ON `quota_ledger` (`bookingOrderId`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `fee_items` (
                    `id` TEXT NOT NULL PRIMARY KEY, `bookingOrderId` TEXT NOT NULL,
                    `category` TEXT NOT NULL, `description` TEXT NOT NULL,
                    `amountCents` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_fee_items_bookingOrderId_sortOrder` ON `fee_items` (`bookingOrderId`, `sortOrder`)")
        }
    }

    /** v2 → v3: Phase 3 reschedule, refund, claims. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `reschedule_requests` (
                    `id` TEXT NOT NULL PRIMARY KEY, `bookingOrderId` TEXT NOT NULL,
                    `requestedBy` TEXT NOT NULL, `requestedAt` INTEGER NOT NULL,
                    `originalStartDateEpochDay` INTEGER NOT NULL, `originalEndDateEpochDay` INTEGER NOT NULL,
                    `newStartDateEpochDay` INTEGER NOT NULL, `newEndDateEpochDay` INTEGER NOT NULL,
                    `exceptionReason` TEXT, `approvedBy` TEXT, `approvedAt` INTEGER,
                    `status` TEXT NOT NULL)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reschedule_requests_bookingOrderId` ON `reschedule_requests` (`bookingOrderId`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `refund_decisions` (
                    `id` TEXT NOT NULL PRIMARY KEY, `bookingOrderId` TEXT NOT NULL,
                    `paidTotalCents` INTEGER NOT NULL, `refundAmountCents` INTEGER NOT NULL,
                    `refundPercent` INTEGER NOT NULL, `ruleUsed` TEXT NOT NULL,
                    `approverUserId` TEXT NOT NULL, `approverName` TEXT NOT NULL,
                    `decidedAt` INTEGER NOT NULL, `manualOverrideReason` TEXT)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_refund_decisions_bookingOrderId_decidedAt` ON `refund_decisions` (`bookingOrderId`, `decidedAt`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `claim_tickets` (
                    `id` TEXT NOT NULL PRIMARY KEY, `travelerId` TEXT NOT NULL,
                    `bookingOrderId` TEXT NOT NULL, `claimStyle` TEXT NOT NULL,
                    `classification` TEXT NOT NULL, `responsibility` TEXT NOT NULL,
                    `description` TEXT NOT NULL, `state` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                    `firstResponseAt` INTEGER, `resolvedAt` INTEGER, `closedAt` INTEGER,
                    `lastTravelerActivityAt` INTEGER NOT NULL)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_claim_tickets_travelerId_createdAt` ON `claim_tickets` (`travelerId`, `createdAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_claim_tickets_state_updatedAt` ON `claim_tickets` (`state`, `updatedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_claim_tickets_responsibility_createdAt` ON `claim_tickets` (`responsibility`, `createdAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_claim_tickets_bookingOrderId` ON `claim_tickets` (`bookingOrderId`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `ticket_status_history` (
                    `id` TEXT NOT NULL PRIMARY KEY, `ticketId` TEXT NOT NULL,
                    `fromState` TEXT, `toState` TEXT NOT NULL,
                    `actor` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `reason` TEXT)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ticket_status_history_ticketId_timestamp` ON `ticket_status_history` (`ticketId`, `timestamp`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `investigation_notes` (
                    `id` TEXT NOT NULL PRIMARY KEY, `ticketId` TEXT NOT NULL,
                    `authorUserId` TEXT NOT NULL, `note` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_investigation_notes_ticketId_createdAt` ON `investigation_notes` (`ticketId`, `createdAt`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `appeal_records` (
                    `id` TEXT NOT NULL PRIMARY KEY, `ticketId` TEXT NOT NULL,
                    `filedBy` TEXT NOT NULL, `filedAt` INTEGER NOT NULL,
                    `reason` TEXT NOT NULL, `resolvedAt` INTEGER, `resolution` TEXT)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_appeal_records_ticketId_filedAt` ON `appeal_records` (`ticketId`, `filedAt`)")
        }
    }

    /** v3 → v4: Phase 4 content governance, dedup, rollback. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `content_items` (
                    `id` TEXT NOT NULL PRIMARY KEY, `title` TEXT NOT NULL,
                    `body` TEXT NOT NULL, `contentHash` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                    `state` TEXT NOT NULL, `averageRating` REAL NOT NULL,
                    `ratingCount` INTEGER NOT NULL, `favoriteCount` INTEGER NOT NULL,
                    `downloadCount` INTEGER NOT NULL)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_content_items_state_averageRating` ON `content_items` (`state`, `averageRating`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_content_items_contentHash` ON `content_items` (`contentHash`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_content_items_updatedAt` ON `content_items` (`updatedAt`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `content_metrics_daily` (
                    `contentId` TEXT NOT NULL, `dateEpochDay` INTEGER NOT NULL,
                    `ratingSum` INTEGER NOT NULL, `ratingCount` INTEGER NOT NULL,
                    `commentCount` INTEGER NOT NULL, `favoriteAdds` INTEGER NOT NULL,
                    `downloadCount` INTEGER NOT NULL, PRIMARY KEY(`contentId`, `dateEpochDay`))"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_content_metrics_daily_contentId_dateEpochDay` ON `content_metrics_daily` (`contentId`, `dateEpochDay`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `content_ratings` (
                    `id` TEXT NOT NULL PRIMARY KEY, `contentId` TEXT NOT NULL,
                    `userId` TEXT NOT NULL, `stars` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL)"""
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_content_ratings_contentId_userId` ON `content_ratings` (`contentId`, `userId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_content_ratings_userId` ON `content_ratings` (`userId`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `governance_decisions` (
                    `id` TEXT NOT NULL PRIMARY KEY, `contentId` TEXT NOT NULL,
                    `fromState` TEXT NOT NULL, `toState` TEXT NOT NULL,
                    `actor` TEXT NOT NULL, `reason` TEXT NOT NULL,
                    `threshold` TEXT, `decidedAt` INTEGER NOT NULL)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_governance_decisions_contentId_decidedAt` ON `governance_decisions` (`contentId`, `decidedAt`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `recommendation_suppressions` (
                    `id` TEXT NOT NULL PRIMARY KEY, `contentId` TEXT NOT NULL,
                    `reason` TEXT NOT NULL, `establishedAt` INTEGER NOT NULL,
                    `clearedAt` INTEGER)"""
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recommendation_suppressions_contentId` ON `recommendation_suppressions` (`contentId`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `duplicate_clusters` (
                    `id` TEXT NOT NULL PRIMARY KEY, `primaryContentId` TEXT NOT NULL,
                    `duplicateContentId` TEXT NOT NULL, `similarity` REAL NOT NULL,
                    `establishedAt` INTEGER NOT NULL, `resolved` INTEGER NOT NULL)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_duplicate_clusters_primaryContentId` ON `duplicate_clusters` (`primaryContentId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_duplicate_clusters_duplicateContentId` ON `duplicate_clusters` (`duplicateContentId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_duplicate_clusters_primaryContentId_duplicateContentId` ON `duplicate_clusters` (`primaryContentId`, `duplicateContentId`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `transaction_checkpoints` (
                    `id` TEXT NOT NULL PRIMARY KEY, `label` TEXT NOT NULL,
                    `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL,
                    `snapshotJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
                    `rolledBack` INTEGER NOT NULL)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_checkpoints_entityType_entityId` ON `transaction_checkpoints` (`entityType`, `entityId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_checkpoints_createdAt` ON `transaction_checkpoints` (`createdAt`)")
        }
    }

    /** v4 → v5: Phase 5 offline queue items and export packages. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `offline_queue_items` (
                    `id` TEXT NOT NULL PRIMARY KEY, `jobType` TEXT NOT NULL,
                    `payloadJson` TEXT NOT NULL, `state` TEXT NOT NULL,
                    `attempts` INTEGER NOT NULL, `maxAttempts` INTEGER NOT NULL,
                    `scheduledAt` INTEGER NOT NULL, `lastError` TEXT,
                    `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_offline_queue_items_state_scheduledAt` ON `offline_queue_items` (`state`, `scheduledAt`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `export_packages` (
                    `id` TEXT NOT NULL PRIMARY KEY, `exportType` TEXT NOT NULL,
                    `filePath` TEXT NOT NULL, `rowCount` INTEGER NOT NULL,
                    `checksum` TEXT NOT NULL, `generatedBy` TEXT NOT NULL,
                    `generatedAt` INTEGER NOT NULL, `maskingProfile` TEXT NOT NULL)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_export_packages_generatedBy_generatedAt` ON `export_packages` (`generatedBy`, `generatedAt`)")
        }
    }

    /** v5 → v6: SLA configuration tables (audit finding #6). */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `sla_config` (
                    `key` TEXT NOT NULL PRIMARY KEY,
                    `firstResponseMinutes` INTEGER NOT NULL,
                    `resolutionMinutes` INTEGER NOT NULL,
                    `travelerNoResponseHours` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `updatedBy` TEXT NOT NULL)"""
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `sla_config_history` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `firstResponseMinutes` INTEGER NOT NULL,
                    `resolutionMinutes` INTEGER NOT NULL,
                    `travelerNoResponseHours` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `updatedBy` TEXT NOT NULL)"""
            )
        }
    }

    /**
     * v6 → v7: Per-user deletion workflow, on-device SLA reminders,
     * persisted refund rules, and compensation fields on claim_tickets.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Deletion requests (audit-fix #1)
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `deletion_requests` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `targetUserId` TEXT NOT NULL,
                    `requestedBy` TEXT NOT NULL,
                    `requestedAt` INTEGER NOT NULL,
                    `reason` TEXT,
                    `state` TEXT NOT NULL,
                    `approvedBy` TEXT,
                    `approvedAt` INTEGER,
                    `executedBy` TEXT,
                    `executedAt` INTEGER,
                    `failureReason` TEXT,
                    `scope` TEXT NOT NULL)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_deletion_requests_targetUserId` ON `deletion_requests` (`targetUserId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_deletion_requests_requestedBy` ON `deletion_requests` (`requestedBy`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_deletion_requests_state` ON `deletion_requests` (`state`)")

            // SLA reminders (audit-fix #2)
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `sla_reminders` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `ticketId` TEXT NOT NULL,
                    `kind` TEXT NOT NULL,
                    `dueAt` INTEGER NOT NULL,
                    `generatedAt` INTEGER NOT NULL,
                    `slaConfigVersion` INTEGER NOT NULL,
                    `acknowledged` INTEGER NOT NULL,
                    `message` TEXT NOT NULL)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sla_reminders_ticketId` ON `sla_reminders` (`ticketId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sla_reminders_dueAt` ON `sla_reminders` (`dueAt`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sla_reminders_ticketId_kind` ON `sla_reminders` (`ticketId`, `kind`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sla_reminders_acknowledged` ON `sla_reminders` (`acknowledged`)")

            // Refund rules (audit-fix #4)
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `refund_rules` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `code` TEXT NOT NULL,
                    `minHoursBeforeStartExclusive` INTEGER NOT NULL,
                    `maxHoursBeforeStartInclusive` INTEGER,
                    `refundPercent` INTEGER NOT NULL,
                    `description` TEXT NOT NULL,
                    `active` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `updatedBy` TEXT NOT NULL)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_refund_rules_active` ON `refund_rules` (`active`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_refund_rules_minHoursBeforeStartExclusive` ON `refund_rules` (`minHoursBeforeStartExclusive`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_refund_rules_code` ON `refund_rules` (`code`)")

            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `refund_rule_history` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `ruleId` TEXT NOT NULL,
                    `code` TEXT NOT NULL,
                    `minHoursBeforeStartExclusive` INTEGER NOT NULL,
                    `maxHoursBeforeStartInclusive` INTEGER,
                    `refundPercent` INTEGER NOT NULL,
                    `description` TEXT NOT NULL,
                    `active` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `updatedBy` TEXT NOT NULL)"""
            )

            // Compensation columns on claim_tickets (audit-fix #3)
            db.execSQL("ALTER TABLE `claim_tickets` ADD COLUMN `compensationAmountCents` INTEGER")
            db.execSQL("ALTER TABLE `claim_tickets` ADD COLUMN `compensationCurrency` TEXT")
            db.execSQL("ALTER TABLE `claim_tickets` ADD COLUMN `compensationBasis` TEXT")
            db.execSQL("ALTER TABLE `claim_tickets` ADD COLUMN `compensationApproverId` TEXT")
            db.execSQL("ALTER TABLE `claim_tickets` ADD COLUMN `compensationApproverName` TEXT")
            db.execSQL("ALTER TABLE `claim_tickets` ADD COLUMN `compensationDecidedAt` INTEGER")
            db.execSQL("ALTER TABLE `claim_tickets` ADD COLUMN `compensationNote` TEXT")
        }
    }

    /**
     * v7 → v8: Authoritative trip window + paid total on booking_orders (audit
     * findings #1, #4, #7). `tripStartAt` / `tripEndAt` source claim eligibility
     * and refund bands. `paidTotalCents` is written from confirmed fee items
     * and is the ONLY value consulted by ApproveRefundUseCase.
     *
     * Backfill: pre-existing rows get trip window = createdAt so instant-epoch-0
     * never appears. These are legacy rows with unknown real trip dates.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE booking_orders ADD COLUMN tripStartAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE booking_orders ADD COLUMN tripEndAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE booking_orders ADD COLUMN paidTotalCents INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "UPDATE booking_orders SET tripStartAt = createdAt, tripEndAt = createdAt " +
                "WHERE tripStartAt = 0"
            )
        }
    }

    /**
     * v8 → v9: Remap booking state 'AutoClosed' → 'Closed' to align with
     * the prompt requirement that only 'Cancelled' and 'Closed' are terminal
     * booking states. No schema change — data-only migration.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE booking_orders SET state = 'Closed' WHERE state = 'AutoClosed'")
        }
    }

    /**
     * v9 → v10: Add business-hour SLA columns to sla_config and
     * sla_config_history tables. Defaults: 9–17 work window, weekends excluded.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sla_config ADD COLUMN workDayStartHour INTEGER NOT NULL DEFAULT 9")
            db.execSQL("ALTER TABLE sla_config ADD COLUMN workDayEndHour INTEGER NOT NULL DEFAULT 17")
            db.execSQL("ALTER TABLE sla_config ADD COLUMN excludeWeekends INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE sla_config_history ADD COLUMN workDayStartHour INTEGER NOT NULL DEFAULT 9")
            db.execSQL("ALTER TABLE sla_config_history ADD COLUMN workDayEndHour INTEGER NOT NULL DEFAULT 17")
            db.execSQL("ALTER TABLE sla_config_history ADD COLUMN excludeWeekends INTEGER NOT NULL DEFAULT 1")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        MIGRATION_9_10
    )

    fun buildDatabase(context: Context): FieldTripDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            FieldTripDatabase::class.java,
            "fieldtripops.db"
        )
            .addMigrations(*ALL)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }
}
