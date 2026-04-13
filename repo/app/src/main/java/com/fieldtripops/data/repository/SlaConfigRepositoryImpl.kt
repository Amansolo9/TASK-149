package com.fieldtripops.data.repository

import androidx.room.withTransaction
import com.fieldtripops.data.dao.SlaConfigDao
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.entity.SlaConfigEntity
import com.fieldtripops.data.entity.SlaConfigHistoryEntity
import com.fieldtripops.domain.repository.SlaConfigRepository
import com.fieldtripops.domain.sla.SlaConfig
import java.time.Instant
import java.util.UUID

class SlaConfigRepositoryImpl(
    private val database: FieldTripDatabase,
    private val dao: SlaConfigDao
) : SlaConfigRepository {

    override suspend fun get(): SlaConfig =
        dao.current()?.toDomain() ?: SlaConfig.DEFAULT

    override suspend fun save(config: SlaConfig) {
        database.withTransaction {
            dao.upsert(config.toEntity())
            dao.insertHistory(
                SlaConfigHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    firstResponseMinutes = config.firstResponseMinutes,
                    resolutionMinutes = config.resolutionMinutes,
                    travelerNoResponseHours = config.travelerNoResponseHours,
                    updatedAt = config.updatedAt.toEpochMilli(),
                    updatedBy = config.updatedBy,
                    workDayStartHour = config.workDayStartHour,
                    workDayEndHour = config.workDayEndHour,
                    excludeWeekends = if (config.excludeWeekends) 1 else 0
                )
            )
        }
    }

    override suspend fun history(): List<SlaConfig> = dao.history().map {
        SlaConfig(
            firstResponseMinutes = it.firstResponseMinutes,
            resolutionMinutes = it.resolutionMinutes,
            travelerNoResponseHours = it.travelerNoResponseHours,
            updatedAt = Instant.ofEpochMilli(it.updatedAt),
            updatedBy = it.updatedBy,
            workDayStartHour = it.workDayStartHour,
            workDayEndHour = it.workDayEndHour,
            excludeWeekends = it.excludeWeekends != 0
        )
    }

    private fun SlaConfigEntity.toDomain() = SlaConfig(
        firstResponseMinutes = firstResponseMinutes,
        resolutionMinutes = resolutionMinutes,
        travelerNoResponseHours = travelerNoResponseHours,
        updatedAt = Instant.ofEpochMilli(updatedAt),
        updatedBy = updatedBy,
        workDayStartHour = workDayStartHour,
        workDayEndHour = workDayEndHour,
        excludeWeekends = excludeWeekends != 0
    )

    private fun SlaConfig.toEntity() = SlaConfigEntity(
        key = "current",
        firstResponseMinutes = firstResponseMinutes,
        resolutionMinutes = resolutionMinutes,
        travelerNoResponseHours = travelerNoResponseHours,
        updatedAt = updatedAt.toEpochMilli(),
        updatedBy = updatedBy,
        workDayStartHour = workDayStartHour,
        workDayEndHour = workDayEndHour,
        excludeWeekends = if (excludeWeekends) 1 else 0
    )
}
