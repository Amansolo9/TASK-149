package com.fieldtripops.domain.usecase

import android.content.Context
import com.fieldtripops.audit.AuditAction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.domain.export.ExportRenderer
import com.fieldtripops.domain.model.ExportPackage
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.repository.ExportPackageRepository
import com.fieldtripops.domain.repository.UserRepository
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Generates user-scoped exports per PRD §16.
 *  - Travelers may export only their OWN data.
 *  - Agents/Reviewers/Admins may export any user's data with role-appropriate masking.
 *  - The masking profile is derived from the session, not from the caller.
 */
class GenerateExportUseCase(
    private val context: Context,
    private val userRepository: UserRepository,
    private val bookingRepository: BookingRepository,
    private val exportRepository: ExportPackageRepository,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) {

    enum class ExportType { USER_BOOKINGS }

    sealed class Result {
        data class Generated(val pkg: ExportPackage) : Result()
        object UserNotFound : Result()
    }

    suspend fun execute(
        targetUserId: String,
        type: ExportType = ExportType.USER_BOOKINGS
    ): Result {
        val session = sessionManager.requireSession()

        // Travelers may only export self; staff roles may export anyone.
        AccessControl.requireOwnerOrRole(
            session, targetUserId, "User", targetUserId,
            Role.Agent, Role.Reviewer, Role.Administrator
        )

        val user = userRepository.findById(targetUserId) ?: return Result.UserNotFound
        val profile = highestRoleProfile(session.roles.toList())

        val rendered = when (type) {
            ExportType.USER_BOOKINGS -> renderBookingsExport(targetUserId, profile)
        }

        val pkg = withContext(Dispatchers.IO) {
            val exportsDir = File(context.filesDir, "exports").also { it.mkdirs() }
            val outFile = File(
                exportsDir,
                "${type.name.lowercase()}_${targetUserId}_${System.currentTimeMillis()}.csv"
            )
            outFile.writeText(rendered.csvContent)
            ExportPackage(
                id = UUID.randomUUID().toString(),
                exportType = type.name,
                filePath = outFile.absolutePath,
                rowCount = rendered.rowCount,
                checksum = rendered.checksum,
                generatedBy = session.userId,    // from session
                generatedAt = Instant.now(),
                maskingProfile = profile.name
            )
        }
        exportRepository.save(pkg)

        auditLogger.log(session.userId, AuditAction.EXPORT_CREATED, "ExportPackage", pkg.id,
            "type=${type.name}, target=$targetUserId, rows=${pkg.rowCount}, " +
                "profile=${profile.name}, by=${session.displayName}")

        return Result.Generated(pkg)
    }

    private suspend fun renderBookingsExport(
        userId: String, profile: ExportRenderer.MaskingProfile
    ): ExportRenderer.Rendered {
        val orders = bookingRepository.findByTraveler(userId)
        val headers = listOf("BookingId", "TravelerId", "Party", "State", "CreatedAt", "ConfirmedAt")
        val rows = orders.map { o ->
            listOf(o.id, o.travelerId, o.partySize.toString(), o.state.name,
                o.createdAt.toString(), o.confirmedAt?.toString().orEmpty())
        }
        return ExportRenderer.renderCsv(headers, rows, profile)
    }

    private fun highestRoleProfile(roles: List<Role>): ExportRenderer.MaskingProfile = when {
        roles.contains(Role.Administrator) -> ExportRenderer.MaskingProfile.ADMINISTRATOR
        roles.contains(Role.Reviewer) -> ExportRenderer.MaskingProfile.REVIEWER
        roles.contains(Role.Agent) -> ExportRenderer.MaskingProfile.AGENT
        else -> ExportRenderer.MaskingProfile.TRAVELER
    }
}
