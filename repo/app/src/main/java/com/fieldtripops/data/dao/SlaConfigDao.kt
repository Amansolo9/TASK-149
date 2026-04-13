package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fieldtripops.data.entity.SlaConfigEntity
import com.fieldtripops.data.entity.SlaConfigHistoryEntity

@Dao
interface SlaConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: SlaConfigEntity)

    @Query("SELECT * FROM sla_config WHERE `key` = 'current' LIMIT 1")
    suspend fun current(): SlaConfigEntity?

    @Insert
    suspend fun insertHistory(entry: SlaConfigHistoryEntity)

    @Query("SELECT * FROM sla_config_history ORDER BY updatedAt DESC")
    suspend fun history(): List<SlaConfigHistoryEntity>
}
