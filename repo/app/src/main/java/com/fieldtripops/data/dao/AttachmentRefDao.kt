package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.fieldtripops.data.entity.AttachmentRefEntity

@Dao
interface AttachmentRefDao {
    @Insert
    suspend fun insert(ref: AttachmentRefEntity)

    @Query("SELECT * FROM attachment_refs WHERE id = :id")
    suspend fun getById(id: String): AttachmentRefEntity?

    @Query("SELECT * FROM attachment_refs WHERE ownerEntityType = :entityType AND ownerEntityId = :entityId")
    suspend fun getByOwner(entityType: String, entityId: String): List<AttachmentRefEntity>

    @Query("DELETE FROM attachment_refs WHERE id = :id")
    suspend fun deleteById(id: String)
}
