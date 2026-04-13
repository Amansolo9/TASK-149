package com.fieldtripops.security

object SessionConfig {
    const val MAX_FAILED_ATTEMPTS = 5
    const val LOCKOUT_DURATION_MINUTES = 15L
    const val SESSION_TIMEOUT_MINUTES = 30L
    const val SESSION_CHECK_INTERVAL_MINUTES = 15L
}
