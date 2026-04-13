package com.fieldtripops.di

import com.fieldtripops.ui.admin.DeletionRequestsViewModel
import com.fieldtripops.ui.admin.SlaConfigViewModel
import com.fieldtripops.ui.booking.BookingConfirmViewModel
import com.fieldtripops.ui.claims.FileClaimViewModel
import com.fieldtripops.ui.claims.MyClaimsViewModel
import com.fieldtripops.ui.consent.ConsentViewModel
import com.fieldtripops.ui.itinerary.ItineraryWizardViewModel
import com.fieldtripops.ui.login.LoginViewModel
import com.fieldtripops.ui.reports.ReportsViewModel
import com.fieldtripops.ui.review.QuarantineViewModel
import com.fieldtripops.ui.review.ReviewQueueViewModel
import com.fieldtripops.ui.shell.ShellViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { LoginViewModel(get()) }
    viewModel { ShellViewModel(get(), get(), get(), get()) }
    viewModel { ItineraryWizardViewModel(get(), get(), get()) }
    viewModel { BookingConfirmViewModel(get(), get(), get(), get()) }
    viewModel { MyClaimsViewModel(get(), get(), get()) }
    viewModel { FileClaimViewModel(get()) }
    viewModel { ReviewQueueViewModel(get(), get(), get()) }
    viewModel { QuarantineViewModel(get(), get(), get()) }
    viewModel { SlaConfigViewModel(get(), get(), get()) }
    viewModel { DeletionRequestsViewModel(get(), get(), get(), get()) }
    viewModel { ReportsViewModel(get(), get(), get()) }
    viewModel { ConsentViewModel(get()) }
}
