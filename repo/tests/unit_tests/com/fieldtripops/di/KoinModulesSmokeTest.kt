package com.fieldtripops.di

import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.repository.ClaimRepository
import com.fieldtripops.domain.repository.ContentRepository
import com.fieldtripops.domain.repository.SlaConfigRepository
import com.fieldtripops.domain.usecase.ConfirmBookingUseCase
import com.fieldtripops.domain.usecase.FileClaimUseCase
import com.fieldtripops.domain.usecase.GenerateSlaRemindersUseCase
import com.fieldtripops.domain.usecase.RollbackUseCase
import com.fieldtripops.security.auth.SessionManager
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Verifies Koin bindings resolve — catches missing dependencies the
 * moment a constructor parameter is added without a corresponding
 * `get()` or binding. This is cheaper and more reliable than waiting
 * for a runtime crash at app startup.
 */
@RunWith(RobolectricTestRunner::class)
class KoinModulesSmokeTest : KoinTest {

    @After fun teardown() { stopKoin() }

    @Test
    fun `key repository types resolve from Koin graph`() {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(allModules)
        }.koin

        assertThat(koin.get<SessionManager>()).isNotNull()
        assertThat(koin.get<BookingRepository>()).isNotNull()
        assertThat(koin.get<ClaimRepository>()).isNotNull()
        assertThat(koin.get<ContentRepository>()).isNotNull()
        assertThat(koin.get<SlaConfigRepository>()).isNotNull()
    }

    @Test
    fun `key use case factories resolve from Koin graph`() {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(allModules)
        }.koin

        assertThat(koin.get<ConfirmBookingUseCase>()).isNotNull()
        assertThat(koin.get<FileClaimUseCase>()).isNotNull()
        assertThat(koin.get<RollbackUseCase>()).isNotNull()
        assertThat(koin.get<GenerateSlaRemindersUseCase>()).isNotNull()
    }
}
