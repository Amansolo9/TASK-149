package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.fieldtripops.data.entity.ConsentRecordEntity

@Dao
interface ConsentRecordDao {
    @Insert
    suspend fun insert(consent: ConsentRecordEntity)

    @Query("SELECT * FROM consent_records WHERE userId = :userId AND revokedAt IS NULL")
    suspend fun getActiveByUserId(userId: String): List<ConsentRecordEntity>

    @Query("UPDATE consent_records SET revokedAt = :at WHERE id = :id")
    suspend fun revoke(id: String, at: Long)

    @Query("SELECT * FROM consent_records WHERE userId = :userId")
    suspend fun getAllByUserId(userId: String): List<ConsentRecordEntity>
}
