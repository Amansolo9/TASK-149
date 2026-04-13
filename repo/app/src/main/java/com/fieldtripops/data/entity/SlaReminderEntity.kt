package com.fieldtripops.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted on-device SLA reminder rows, one per (ticket, kind). Dedup via a
 * unique index on (ticketId, kind) so that re-evaluation by the worker never
 * duplicates a reminder that was already issued against the same SLA version.
 *
 * kind: FIRST_RESPONSE_PRE_BREACH | RESOLUTION_PRE_BREACH |
 *       FIRST_RESPONSE_BREACHED | RESOLUTION_BREACHED
 */
@Entity(
    tableName = "sla_reminders",
    indices = [
        Index(value = ["ticketId"]),
        Index(value = ["dueAt"]),
        Index(value = ["ticketId", "kind"], unique = true),
        Index(value = ["acknowledged"])
    ]
)
data class SlaReminderEntity(
    @PrimaryKey val id: String,
    val ticketId: String,
    val kind: String,
    val dueAt: Long,
    val generatedAt: Long,
    val slaConfigVersion: Long, // updatedAt millis from the sla_config used
    val acknowledged: Boolean,
    val message: String
)
