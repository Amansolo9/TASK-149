package com.fieldtripops.ui.util

object MaskedFieldRenderer {

    fun maskPhone(value: String): String {
        val digits = value.filter { it.isDigit() }
        if (digits.length < 4) return "****"
        val lastFour = digits.takeLast(4)
        return "(***) ***-$lastFour"
    }

    fun maskEmail(value: String): String {
        val atIndex = value.indexOf('@')
        if (atIndex <= 0) return "***"
        val firstChar = value[0]
        val domain = value.substring(atIndex)
        return "$firstChar***$domain"
    }

    fun maskGeneric(value: String, visibleEnd: Int = 4): String {
        if (value.length <= visibleEnd) return value
        val masked = "*".repeat(value.length - visibleEnd)
        return masked + value.takeLast(visibleEnd)
    }

    fun maskName(value: String): String {
        if (value.isEmpty()) return "***"
        val parts = value.split(" ")
        return parts.joinToString(" ") { part ->
            if (part.length <= 1) part
            else part[0] + "*".repeat(part.length - 1)
        }
    }
}
