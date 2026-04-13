package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fieldtripops.data.entity.RoleAssignmentEntity

@Dao
interface RoleAssignmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assignment: RoleAssignmentEntity)

    @Query("SELECT * FROM role_assignments WHERE userId = :userId")
    suspend fun getByUserId(userId: String): List<RoleAssignmentEntity>

    @Query("DELETE FROM role_assignments WHERE id = :id")
    suspend fun deleteById(id: String)
}
