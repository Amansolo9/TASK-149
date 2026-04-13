package com.fieldtripops.domain.governance

/**
 * Per PRD §9.8:
 * - Average rating < 2.5 after at least 10 reviews -> demote and exclude from recs
 * - Duplicate similarity > 80% -> quarantine and exclude from recs
 */
object GovernanceThresholds {
    const val MIN_RATING_COUNT_FOR_DEMOTION = 10
    const val MIN_AVERAGE_RATING = 2.5
    const val DUPLICATE_SIMILARITY_THRESHOLD = 0.80
}
