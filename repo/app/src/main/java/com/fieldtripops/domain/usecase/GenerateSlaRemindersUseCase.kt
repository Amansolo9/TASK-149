package com.fieldtripops.domain.usecase

import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.dao.ClaimTicketDao
import com.fieldtripops.domain.model.SlaReminder
import com.fieldtripops.domain.model.SlaReminderKind
import com.fieldtripops.domain.repository.SlaConfigRepository
import com.fieldtripops.domain.repository.SlaReminderRepository
import com.fieldtripops.domain.sla.BusinessHourCalculator
import com.fieldtripops.domain.sla.SlaConfig
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Evaluates every open ticket against the persisted SLA config and emits
 * reminder rows BEFORE breach. Designed to be deterministic and replay-safe:
 *  - dedup is enforced by a UNIQUE(ticketId, kind) index on sla_reminders
 *  - stale pre-breach reminders (those tagged with an older sla_config version)
 *    are purged before evaluation, so a config change re-times future reminders
 *  - post-breach rows are preserved for audit
 *
 * Pre-breach threshold: the reminder fires when time-remaining drops to 25%
 * of the SLA window (clamped at a minimum 15-minute lead).
 *
 * **Timing model: configurable business hours.** SLA breach deadlines are
 * computed via [BusinessHourCalculator] using the working-hours window and
 * weekend exclusion configured in [SlaConfig]. When weekends are not
 * excluded and the work window is 0–24, this degrades to elapsed time.
 */
class GenerateSlaRemindersUseCase(
    private val slaConfigRepository: SlaConfigRepository,
    private val slaReminderRepository: SlaReminderRepository,
    private val claimTicketDao: ClaimTicketDao,
    private val auditLogger: AuditLogger
) {

    data class Result(
        val generated: Int,
        val slaConfigVersion: Long
    )

    suspend fun execute(now: Instant = Instant.now()): Result {
        val sla = slaConfigRepository.get()
        val version = sla.updatedAt.toEpochMilli().coerceAtLeast(0L)
        slaReminderRepository.purgeStalePending(version)

        var generated = 0

        // FIRST RESPONSE reminders
        val awaitingFr = claimTicketDao.findAwaitingFirstResponse()
        for (t in awaitingFr) {
            val created = Instant.ofEpochMilli(t.createdAt)
            val breachAt = BusinessHourCalculator.breachInstant(
                created, sla.firstResponseMinutes.toLong(), sla
            )
            val lead = preBreachLead(Duration.ofMinutes(sla.firstResponseMinutes.toLong()))
            val remindAt = breachAt.minus(lead)

            if (now.isBefore(breachAt) && !now.isBefore(remindAt)) {
                val reminder = SlaReminder(
                    id = UUID.randomUUID().toString(),
                    ticketId = t.id,
                    kind = SlaReminderKind.FIRST_RESPONSE_PRE_BREACH,
                    dueAt = breachAt,
                    generatedAt = now,
                    slaConfigVersion = version,
                    acknowledged = false,
                    message = "First-response SLA approaching (breach at $breachAt)"
                )
                if (slaReminderRepository.upsertIfAbsent(reminder)) {
                    generated++
                    auditLogger.log(
                        "system", AuditAction.SLA_REMINDER_GENERATED,
                        "ClaimTicket", t.id,
                        "kind=${reminder.kind.name}, dueAt=$breachAt"
                    )
                }
            } else if (!now.isBefore(breachAt)) {
                val reminder = SlaReminder(
                    id = UUID.randomUUID().toString(),
                    ticketId = t.id,
                    kind = SlaReminderKind.FIRST_RESPONSE_BREACHED,
                    dueAt = breachAt,
                    generatedAt = now,
                    slaConfigVersion = version,
                    acknowledged = false,
                    message = "First-response SLA breached at $breachAt"
                )
                if (slaReminderRepository.upsertIfAbsent(reminder)) {
                    generated++
                    auditLogger.log(
                        "system", AuditAction.SLA_REMINDER_GENERATED,
                        "ClaimTicket", t.id,
                        "kind=${reminder.kind.name}, breachAt=$breachAt"
                    )
                }
            }
        }

        // RESOLUTION reminders
        val awaitingRes = claimTicketDao.findAwaitingResolution()
        for (t in awaitingRes) {
            val created = Instant.ofEpochMilli(t.createdAt)
            val breachAt = BusinessHourCalculator.breachInstant(
                created, sla.resolutionMinutes.toLong(), sla
            )
            val lead = preBreachLead(Duration.ofMinutes(sla.resolutionMinutes.toLong()))
            val remindAt = breachAt.minus(lead)

            if (now.isBefore(breachAt) && !now.isBefore(remindAt)) {
                val reminder = SlaReminder(
                    id = UUID.randomUUID().toString(),
                    ticketId = t.id,
                    kind = SlaReminderKind.RESOLUTION_PRE_BREACH,
                    dueAt = breachAt,
                    generatedAt = now,
                    slaConfigVersion = version,
                    acknowledged = false,
                    message = "Resolution SLA approaching (breach at $breachAt)"
                )
                if (slaReminderRepository.upsertIfAbsent(reminder)) {
                    generated++
                    auditLogger.log(
                        "system", AuditAction.SLA_REMINDER_GENERATED,
                        "ClaimTicket", t.id,
                        "kind=${reminder.kind.name}, dueAt=$breachAt"
                    )
                }
            } else if (!now.isBefore(breachAt)) {
                val reminder = SlaReminder(
                    id = UUID.randomUUID().toString(),
                    ticketId = t.id,
                    kind = SlaReminderKind.RESOLUTION_BREACHED,
                    dueAt = breachAt,
                    generatedAt = now,
                    slaConfigVersion = version,
                    acknowledged = false,
                    message = "Resolution SLA breached at $breachAt"
                )
                if (slaReminderRepository.upsertIfAbsent(reminder)) {
                    generated++
                    auditLogger.log(
                        "system", AuditAction.SLA_REMINDER_GENERATED,
                        "ClaimTicket", t.id,
                        "kind=${reminder.kind.name}, breachAt=$breachAt"
                    )
                }
            }
        }
        return Result(generated, version)
    }

    private fun preBreachLead(window: Duration): Duration {
        val quarter = window.dividedBy(4)
        val floor = Duration.ofMinutes(15)
        return if (quarter < floor) floor else quarter
    }
}
