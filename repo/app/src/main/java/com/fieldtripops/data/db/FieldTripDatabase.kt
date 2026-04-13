package com.fieldtripops.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fieldtripops.data.dao.AppealRecordDao
import com.fieldtripops.data.dao.AttachmentRefDao
import com.fieldtripops.data.dao.AuditLogDao
import com.fieldtripops.data.dao.BookingOrderDao
import com.fieldtripops.data.dao.ClaimTicketDao
import com.fieldtripops.data.dao.ConsentRecordDao
import com.fieldtripops.data.dao.DeletionRequestDao
import com.fieldtripops.data.dao.RefundRuleDao
import com.fieldtripops.data.dao.SlaReminderDao
import com.fieldtripops.data.dao.ContentItemDao
import com.fieldtripops.data.dao.ContentMetricDao
import com.fieldtripops.data.dao.CredentialDao
import com.fieldtripops.data.dao.DuplicateClusterDao
import com.fieldtripops.data.dao.ExportPackageDao
import com.fieldtripops.data.dao.FeeItemDao
import com.fieldtripops.data.dao.GovernanceDecisionDao
import com.fieldtripops.data.dao.InventorySlotDao
import com.fieldtripops.data.dao.InvestigationNoteDao
import com.fieldtripops.data.dao.ItineraryDraftDao
import com.fieldtripops.data.dao.OfflineQueueItemDao
import com.fieldtripops.data.dao.QuotaLedgerDao
import com.fieldtripops.data.dao.RecommendationSuppressionDao
import com.fieldtripops.data.dao.RefundDecisionDao
import com.fieldtripops.data.dao.RescheduleRequestDao
import com.fieldtripops.data.dao.RoleAssignmentDao
import com.fieldtripops.data.dao.SessionAuditDao
import com.fieldtripops.data.dao.SlaConfigDao
import com.fieldtripops.data.dao.TicketStatusHistoryDao
import com.fieldtripops.data.dao.TransactionCheckpointDao
import com.fieldtripops.data.dao.UserDao
import com.fieldtripops.data.entity.AppealRecordEntity
import com.fieldtripops.data.entity.AttachmentRefEntity
import com.fieldtripops.data.entity.AuditLogEntity
import com.fieldtripops.data.entity.BookingOrderEntity
import com.fieldtripops.data.entity.ClaimTicketEntity
import com.fieldtripops.data.entity.ConsentRecordEntity
import com.fieldtripops.data.entity.DeletionRequestEntity
import com.fieldtripops.data.entity.RefundRuleEntity
import com.fieldtripops.data.entity.RefundRuleHistoryEntity
import com.fieldtripops.data.entity.SlaReminderEntity
import com.fieldtripops.data.entity.ContentItemEntity
import com.fieldtripops.data.entity.ContentMetricDailyEntity
import com.fieldtripops.data.entity.ContentRatingEntity
import com.fieldtripops.data.entity.CredentialEntity
import com.fieldtripops.data.entity.DuplicateClusterEntity
import com.fieldtripops.data.entity.ExportPackageEntity
import com.fieldtripops.data.entity.FeeItemEntity
import com.fieldtripops.data.entity.GovernanceDecisionEntity
import com.fieldtripops.data.entity.InventorySlotEntity
import com.fieldtripops.data.entity.InvestigationNoteEntity
import com.fieldtripops.data.entity.ItineraryDraftEntity
import com.fieldtripops.data.entity.OfflineQueueItemEntity
import com.fieldtripops.data.entity.QuotaLedgerEntity
import com.fieldtripops.data.entity.RecommendationSuppressionEntity
import com.fieldtripops.data.entity.RefundDecisionEntity
import com.fieldtripops.data.entity.RescheduleRequestEntity
import com.fieldtripops.data.entity.RoleAssignmentEntity
import com.fieldtripops.data.entity.SessionAuditEntity
import com.fieldtripops.data.entity.SlaConfigEntity
import com.fieldtripops.data.entity.SlaConfigHistoryEntity
import com.fieldtripops.data.entity.TicketStatusHistoryEntity
import com.fieldtripops.data.entity.TransactionCheckpointEntity
import com.fieldtripops.data.entity.UserEntity

@Database(
    entities = [
        // Phase 1
        UserEntity::class, RoleAssignmentEntity::class, CredentialEntity::class,
        ConsentRecordEntity::class, SessionAuditEntity::class, AuditLogEntity::class,
        AttachmentRefEntity::class,
        // Phase 2
        ItineraryDraftEntity::class, BookingOrderEntity::class, InventorySlotEntity::class,
        QuotaLedgerEntity::class, FeeItemEntity::class,
        // Phase 3
        RescheduleRequestEntity::class, RefundDecisionEntity::class,
        ClaimTicketEntity::class, TicketStatusHistoryEntity::class,
        InvestigationNoteEntity::class, AppealRecordEntity::class,
        // Phase 4
        ContentItemEntity::class, ContentMetricDailyEntity::class, ContentRatingEntity::class,
        GovernanceDecisionEntity::class, RecommendationSuppressionEntity::class,
        DuplicateClusterEntity::class, TransactionCheckpointEntity::class,
        // Phase 5
        OfflineQueueItemEntity::class, ExportPackageEntity::class,
        // Phase 6 — SLA configuration (audit finding #6)
        SlaConfigEntity::class, SlaConfigHistoryEntity::class,
        // Phase 7 — Deletion workflow, SLA reminders, refund rules (audit-fix #1/#2/#3/#4)
        DeletionRequestEntity::class, SlaReminderEntity::class,
        RefundRuleEntity::class, RefundRuleHistoryEntity::class
    ],
    version = 10,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class FieldTripDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun roleAssignmentDao(): RoleAssignmentDao
    abstract fun credentialDao(): CredentialDao
    abstract fun consentRecordDao(): ConsentRecordDao
    abstract fun sessionAuditDao(): SessionAuditDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun attachmentRefDao(): AttachmentRefDao

    abstract fun itineraryDraftDao(): ItineraryDraftDao
    abstract fun bookingOrderDao(): BookingOrderDao
    abstract fun inventorySlotDao(): InventorySlotDao
    abstract fun quotaLedgerDao(): QuotaLedgerDao
    abstract fun feeItemDao(): FeeItemDao

    abstract fun rescheduleRequestDao(): RescheduleRequestDao
    abstract fun refundDecisionDao(): RefundDecisionDao
    abstract fun claimTicketDao(): ClaimTicketDao
    abstract fun ticketStatusHistoryDao(): TicketStatusHistoryDao
    abstract fun investigationNoteDao(): InvestigationNoteDao
    abstract fun appealRecordDao(): AppealRecordDao

    abstract fun contentItemDao(): ContentItemDao
    abstract fun contentMetricDao(): ContentMetricDao
    abstract fun governanceDecisionDao(): GovernanceDecisionDao
    abstract fun recommendationSuppressionDao(): RecommendationSuppressionDao
    abstract fun duplicateClusterDao(): DuplicateClusterDao
    abstract fun transactionCheckpointDao(): TransactionCheckpointDao

    abstract fun offlineQueueItemDao(): OfflineQueueItemDao
    abstract fun exportPackageDao(): ExportPackageDao
    abstract fun slaConfigDao(): SlaConfigDao

    abstract fun deletionRequestDao(): DeletionRequestDao
    abstract fun slaReminderDao(): SlaReminderDao
    abstract fun refundRuleDao(): RefundRuleDao
}
