package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fieldtripops.data.entity.CredentialEntity

@Dao
interface CredentialDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(credential: CredentialEntity)

    @Query("SELECT * FROM credentials WHERE userId = :userId")
    suspend fun getByUserId(userId: String): CredentialEntity?

    @Query("UPDATE credentials SET failedAttempts = failedAttempts + 1 WHERE userId = :userId")
    suspend fun incrementFailedAttempts(userId: String)

    @Query("UPDATE credentials SET failedAttempts = 0 WHERE userId = :userId")
    suspend fun resetFailedAttempts(userId: String)

    @Query("UPDATE credentials SET lockedUntil = :until WHERE userId = :userId")
    suspend fun lockAccount(userId: String, until: Long)

    @Query("UPDATE credentials SET lastLoginAt = :at WHERE userId = :userId")
    suspend fun updateLastLogin(userId: String, at: Long)
}
