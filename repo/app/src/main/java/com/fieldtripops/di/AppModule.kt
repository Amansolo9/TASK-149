package com.fieldtripops.di

import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.audit.RoomAuditLogger
import org.koin.dsl.module

val auditModule = module {
    single<AuditLogger> { RoomAuditLogger(get(), get()) }
}

val allModules = listOf(
    databaseModule,
    repositoryModule,
    auditModule,
    useCaseModule,
    viewModelModule
)
