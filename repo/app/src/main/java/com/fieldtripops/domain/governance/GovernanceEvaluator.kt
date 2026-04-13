package com.fieldtripops.domain.governance

import com.fieldtripops.domain.model.ContentItem
import com.fieldtripops.domain.model.ContentState

/**
 * Stateless evaluator for automatic content demotion and quarantine decisions.
 * Per PRD §9.8.
 */
object GovernanceEvaluator {

    sealed class Decision {
        object NoChange : Decision()
        data class Demote(val reason: String) : Decision()
        data class Quarantine(val reason: String) : Decision()
    }

    fun evaluate(item: ContentItem): Decision {
        if (item.state == ContentState.Quarantined || item.state == ContentState.Excluded) {
            return Decision.NoChange
        }

        // Rating-based demotion
        if (item.ratingCount >= GovernanceThresholds.MIN_RATING_COUNT_FOR_DEMOTION &&
            item.averageRating < GovernanceThresholds.MIN_AVERAGE_RATING
        ) {
            if (item.state != ContentState.Demoted) {
                return Decision.Demote(
                    "Average rating ${"%.2f".format(item.averageRating)} " +
                            "< ${GovernanceThresholds.MIN_AVERAGE_RATING} after ${item.ratingCount} reviews"
                )
            }
        }

        return Decision.NoChange
    }

    fun evaluateDuplicate(similarity: Double): Boolean {
        return similarity > GovernanceThresholds.DUPLICATE_SIMILARITY_THRESHOLD
    }
}
