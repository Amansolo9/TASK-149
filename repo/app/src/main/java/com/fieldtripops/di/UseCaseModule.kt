package com.fieldtripops.di

import com.fieldtripops.domain.usecase.ApproveRefundUseCase
import com.fieldtripops.domain.usecase.AutoCloseStalePendingUseCase
import com.fieldtripops.domain.usecase.AutoCloseWaitingTicketsUseCase
import com.fieldtripops.domain.usecase.CancelBookingUseCase
import com.fieldtripops.domain.usecase.ConfirmBookingUseCase
import com.fieldtripops.domain.usecase.ExecuteUserDeletionUseCase
import com.fieldtripops.domain.usecase.FileAppealUseCase
import com.fieldtripops.domain.usecase.FileClaimUseCase
import com.fieldtripops.domain.usecase.GenerateExportUseCase
import com.fieldtripops.domain.usecase.GenerateReportUseCase
import com.fieldtripops.domain.usecase.GenerateSlaRemindersUseCase
import com.fieldtripops.domain.usecase.GetBookingTotalUseCase
import com.fieldtripops.domain.usecase.GovernanceOverrideUseCase
import com.fieldtripops.domain.usecase.LoginUseCase
import com.fieldtripops.domain.usecase.LogoutUseCase
import com.fieldtripops.domain.usecase.RateContentUseCase
import com.fieldtripops.domain.usecase.RecordConsentUseCase
import com.fieldtripops.domain.usecase.RequestRescheduleUseCase
import com.fieldtripops.domain.usecase.RequestUserDeletionUseCase
import com.fieldtripops.domain.usecase.RetentionSweepUseCase
import com.fieldtripops.domain.usecase.RollbackUseCase
import com.fieldtripops.domain.usecase.RunDuplicateScanUseCase
import com.fieldtripops.domain.usecase.SaveItineraryDraftUseCase
import com.fieldtripops.domain.usecase.SetClaimCompensationUseCase
import com.fieldtripops.domain.usecase.SubmitBookingUseCase
import com.fieldtripops.domain.usecase.TransitionClaimUseCase
import com.fieldtripops.domain.usecase.UpdateRefundRuleUseCase
import com.fieldtripops.domain.usecase.UpdateSlaConfigUseCase
import com.fieldtripops.domain.usecase.ValidateSessionUseCase
import com.fieldtripops.domain.usecase.WriteAuditLogUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val useCaseModule = module {
    // Phase 1 — auth
    factory { LoginUseCase(get(), get(), get(), get(), get(), get()) }
    factory { LogoutUseCase(get(), get(), get()) }
    factory { RecordConsentUseCase(get(), get(), get()) }
    factory { ValidateSessionUseCase(get(), get()) }
    factory { WriteAuditLogUseCase(get()) }

    // Phase 2 — booking (session-bound)
    factory { SaveItineraryDraftUseCase(get(), get(), get()) }
    factory { SubmitBookingUseCase(get(), get(), get(), get(), get(), get()) }
    factory { ConfirmBookingUseCase(get(), get(), get(), get(), get(), get()) }
    factory { CancelBookingUseCase(get(), get(), get(), get(), get()) }
    factory { AutoCloseStalePendingUseCase(get(), get(), get(), get()) }
    factory { GetBookingTotalUseCase(get()) }

    // Phase 3 — claims (session-bound)
    factory { FileClaimUseCase(get(), get(), get(), get(), get(), get()) }
    factory { TransitionClaimUseCase(get(), get(), get()) }
    factory { ApproveRefundUseCase(get(), get(), get(), get(), get()) }
    factory { RequestRescheduleUseCase(get(), get(), get(), get(), get()) }
    factory { AutoCloseWaitingTicketsUseCase(get(), get(), get()) }

    // Phase 4 — governance
    factory { RateContentUseCase(get(), get(), get(), get(), get()) }
    factory { RunDuplicateScanUseCase(get(), get(), get(), get(), get()) }
    factory { GovernanceOverrideUseCase(get(), get(), get(), get(), get(), get()) }
    factory { RollbackUseCase(get(), get(), get()) }

    // Phase 5 — reports/exports/retention
    factory { GenerateReportUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GenerateExportUseCase(androidContext(), get(), get(), get(), get(), get()) }
    factory { RetentionSweepUseCase(get(), get()) }

    // Phase 6 — admin SLA
    factory { UpdateSlaConfigUseCase(get(), get(), get()) }

    // Phase 7 — Deletion workflow, SLA reminders, compensation, refund rules
    factory { RequestUserDeletionUseCase(get(), get(), get()) }
    factory { ExecuteUserDeletionUseCase(get(), get(), get()) }
    factory { GenerateSlaRemindersUseCase(get(), get(), get(), get()) }
    factory { SetClaimCompensationUseCase(get(), get(), get()) }
    factory { UpdateRefundRuleUseCase(get(), get(), get()) }

    // Phase 8 — Appeals filing with offline-queue handoff
    factory { FileAppealUseCase(get(), get(), get(), get(), get()) }
}
