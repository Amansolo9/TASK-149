package com.fieldtripops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fieldtripops.data.entity.SlaReminderEntity

@Dao
interface SlaReminderDao {
    /** IGNORE so re-evaluation cannot duplicate an existing (ticketId, kind). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(reminder: SlaReminderEntity): Long

    @Query("SELECT * FROM sla_reminders WHERE ticketId = :id ORDER BY dueAt ASC")
    suspend fun getByTicket(id: String): List<SlaReminderEntity>

    @Query("SELECT * FROM sla_reminders WHERE acknowledged = 0 ORDER BY dueAt ASC")
    suspend fun listOpen(): List<SlaReminderEntity>

    @Query("SELECT * FROM sla_reminders WHERE ticketId = :ticketId AND kind = :kind LIMIT 1")
    suspend fun findByTicketAndKind(ticketId: String, kind: String): SlaReminderEntity?

    @Query("UPDATE sla_reminders SET acknowledged = 1 WHERE id = :id")
    suspend fun acknowledge(id: String)

    @Query("DELETE FROM sla_reminders WHERE ticketId = :ticketId")
    suspend fun deleteForTicket(ticketId: String)

    /**
     * Drop pre-breach reminders that were generated against a stale SLA
     * version. Breach rows (post-event) are preserved for audit.
     */
    @Query(
        """DELETE FROM sla_reminders
           WHERE slaConfigVersion < :currentVersion
             AND kind IN ('FIRST_RESPONSE_PRE_BREACH','RESOLUTION_PRE_BREACH')
             AND acknowledged = 0"""
    )
    suspend fun purgeStalePending(currentVersion: Long)
}
