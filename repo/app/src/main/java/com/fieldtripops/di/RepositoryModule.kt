package com.fieldtripops.di

import com.fieldtripops.attachment.AttachmentStorage
import com.fieldtripops.attachment.LocalFileAttachmentStorage
import com.fieldtripops.data.repository.AttachmentRepositoryImpl
import com.fieldtripops.data.repository.AuditRepositoryImpl
import com.fieldtripops.data.repository.AuthRepositoryImpl
import com.fieldtripops.data.repository.BookingRepositoryImpl
import com.fieldtripops.data.repository.CheckpointRepositoryImpl
import com.fieldtripops.data.repository.ClaimRepositoryImpl
import com.fieldtripops.data.repository.ConsentRepositoryImpl
import com.fieldtripops.data.repository.DeletionRequestRepositoryImpl
import com.fieldtripops.data.repository.RefundRuleRepositoryImpl
import com.fieldtripops.data.repository.SlaReminderRepositoryImpl
import com.fieldtripops.data.repository.ContentRepositoryImpl
import com.fieldtripops.data.repository.ExportPackageRepositoryImpl
import com.fieldtripops.data.repository.FeeItemRepositoryImpl
import com.fieldtripops.data.repository.GovernanceRepositoryImpl
import com.fieldtripops.data.repository.InventoryRepositoryImpl
import com.fieldtripops.data.repository.ItineraryRepositoryImpl
import com.fieldtripops.data.repository.OfflineQueueRepositoryImpl
import com.fieldtripops.data.repository.RefundDecisionRepositoryImpl
import com.fieldtripops.data.repository.RescheduleRepositoryImpl
import com.fieldtripops.data.repository.SessionRepositoryImpl
import com.fieldtripops.data.repository.SlaConfigRepositoryImpl
import com.fieldtripops.data.repository.UserRepositoryImpl
import com.fieldtripops.domain.repository.AttachmentRepository
import com.fieldtripops.domain.repository.AuditRepository
import com.fieldtripops.domain.repository.AuthRepository
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.repository.CheckpointRepository
import com.fieldtripops.domain.repository.ClaimRepository
import com.fieldtripops.domain.repository.ConsentRepository
import com.fieldtripops.domain.repository.DeletionRequestRepository
import com.fieldtripops.domain.repository.RefundRuleRepository
import com.fieldtripops.domain.repository.SlaReminderRepository
import com.fieldtripops.domain.repository.ContentRepository
import com.fieldtripops.domain.repository.ExportPackageRepository
import com.fieldtripops.domain.repository.FeeItemRepository
import com.fieldtripops.domain.repository.GovernanceRepository
import com.fieldtripops.domain.repository.InventoryRepository
import com.fieldtripops.domain.repository.ItineraryRepository
import com.fieldtripops.domain.repository.OfflineQueueRepository
import com.fieldtripops.domain.repository.RefundDecisionRepository
import com.fieldtripops.domain.repository.RescheduleRepository
import com.fieldtripops.domain.repository.SessionRepository
import com.fieldtripops.domain.repository.SlaConfigRepository
import com.fieldtripops.domain.repository.UserRepository
import com.fieldtripops.security.AesSensitiveFieldCodec
import com.fieldtripops.security.AuditChecksum
import com.fieldtripops.security.FieldEncryptor
import com.fieldtripops.security.PasswordHasher
import com.fieldtripops.security.SensitiveFieldCodec
import com.fieldtripops.security.auth.SessionManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val repositoryModule = module {
    single { PasswordHasher() }
    single { FieldEncryptor(androidContext()) }
    single { AuditChecksum() }
    single { SessionManager() }
    single<SensitiveFieldCodec> { AesSensitiveFieldCodec(get()) }
    single<AttachmentStorage> { LocalFileAttachmentStorage(androidContext()) }
    single { com.fieldtripops.attachment.AttachmentImageCache() }

    single<UserRepository> { UserRepositoryImpl(get(), get()) }
    single<AuthRepository> { AuthRepositoryImpl(get()) }
    single<ConsentRepository> { ConsentRepositoryImpl(get()) }
    single<SessionRepository> { SessionRepositoryImpl(get()) }
    single<AuditRepository> { AuditRepositoryImpl(get()) }
    single<AttachmentRepository> { AttachmentRepositoryImpl(get(), get()) }

    single<ItineraryRepository> { ItineraryRepositoryImpl(get(), get()) }
    single<BookingRepository> { BookingRepositoryImpl(get()) }
    single<InventoryRepository> { InventoryRepositoryImpl(get(), get(), get()) }
    single<FeeItemRepository> { FeeItemRepositoryImpl(get(), get()) }

    single<ClaimRepository> { ClaimRepositoryImpl(get(), get(), get(), get(), get(), get()) }
    single<RefundDecisionRepository> { RefundDecisionRepositoryImpl(get()) }
    single<RescheduleRepository> { RescheduleRepositoryImpl(get()) }

    single<ContentRepository> { ContentRepositoryImpl(get(), get(), get()) }
    single<GovernanceRepository> { GovernanceRepositoryImpl(get(), get(), get()) }
    single<CheckpointRepository> { CheckpointRepositoryImpl(get()) }

    single<OfflineQueueRepository> { OfflineQueueRepositoryImpl(get()) }
    single<ExportPackageRepository> { ExportPackageRepositoryImpl(get()) }
    single<SlaConfigRepository> { SlaConfigRepositoryImpl(get(), get()) }

    // Phase 7 audit-fix additions
    single<DeletionRequestRepository> { DeletionRequestRepositoryImpl(get(), get()) }
    single<SlaReminderRepository> { SlaReminderRepositoryImpl(get()) }
    single<RefundRuleRepository> { RefundRuleRepositoryImpl(get(), get()) }
}
