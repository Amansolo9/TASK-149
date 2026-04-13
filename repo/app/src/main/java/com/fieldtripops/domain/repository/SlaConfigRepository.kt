package com.fieldtripops.domain.repository

import com.fieldtripops.domain.sla.SlaConfig

interface SlaConfigRepository {
    /** Returns persisted current SLA, or [SlaConfig.DEFAULT] if not yet seeded. */
    suspend fun get(): SlaConfig
    suspend fun save(config: SlaConfig)
    suspend fun history(): List<SlaConfig>
}
