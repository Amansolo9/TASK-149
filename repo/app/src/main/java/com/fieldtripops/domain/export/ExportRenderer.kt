package com.fieldtripops.domain.export

import com.fieldtripops.domain.model.Role
import com.fieldtripops.ui.util.MaskedFieldRenderer
import java.security.MessageDigest

/**
 * Renders user/booking/claim data into a CSV export, applying role-based masking
 * per PRD §14 and §16.
 */
object ExportRenderer {

    enum class MaskingProfile { TRAVELER, AGENT, REVIEWER, ADMINISTRATOR;
        companion object {
            fun fromRole(role: Role): MaskingProfile = when (role) {
                Role.Traveler -> TRAVELER
                Role.Agent -> AGENT
                Role.Reviewer -> REVIEWER
                Role.Administrator -> ADMINISTRATOR
            }
        }
    }

    data class Rendered(val csvContent: String, val rowCount: Int, val checksum: String)

    /**
     * Renders rows of [headers + values] as CSV, applies per-cell masking by column name.
     * "phone", "email", "name" are masked for non-admin profiles.
     */
    fun renderCsv(
        headers: List<String>,
        rows: List<List<String>>,
        profile: MaskingProfile
    ): Rendered {
        val sb = StringBuilder()
        sb.append(headers.joinToString(",") { csvEscape(it) }).append('\n')
        for (row in rows) {
            require(row.size == headers.size) {
                "Row size ${row.size} does not match header size ${headers.size}"
            }
            val masked = row.mapIndexed { idx, cell ->
                val header = headers[idx].lowercase()
                csvEscape(maskCell(header, cell, profile))
            }
            sb.append(masked.joinToString(",")).append('\n')
        }
        val csv = sb.toString()
        return Rendered(
            csvContent = csv,
            rowCount = rows.size,
            checksum = sha256(csv)
        )
    }

    private fun maskCell(header: String, value: String, profile: MaskingProfile): String {
        if (profile == MaskingProfile.ADMINISTRATOR) return value
        return when {
            header.contains("phone") -> MaskedFieldRenderer.maskPhone(value)
            header.contains("email") -> MaskedFieldRenderer.maskEmail(value)
            header == "name" || header.contains("display") || header.contains("traveler") ->
                MaskedFieldRenderer.maskName(value)
            else -> value
        }
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
    }

    private fun sha256(data: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
