package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fieldtripops.data.entity.RefundRuleEntity
import com.fieldtripops.data.entity.RefundRuleHistoryEntity

@Dao
interface RefundRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RefundRuleEntity)

    @Insert
    suspend fun insertHistory(entry: RefundRuleHistoryEntity)

    @Query(
        """SELECT * FROM refund_rules WHERE active = 1
           ORDER BY minHoursBeforeStartExclusive DESC"""
    )
    suspend fun listActive(): List<RefundRuleEntity>

    @Query("SELECT * FROM refund_rules ORDER BY minHoursBeforeStartExclusive DESC")
    suspend fun listAll(): List<RefundRuleEntity>

    @Query("SELECT * FROM refund_rules WHERE id = :id")
    suspend fun findById(id: String): RefundRuleEntity?

    @Query("SELECT * FROM refund_rules WHERE code = :code")
    suspend fun findByCode(code: String): RefundRuleEntity?

    @Query("SELECT COUNT(*) FROM refund_rules")
    suspend fun count(): Int
}
