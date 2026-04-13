package com.fieldtripops.domain.governance

import com.fieldtripops.domain.model.ContentItem
import com.fieldtripops.domain.model.ContentState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class GovernanceEvaluatorTest {

    private fun item(avg: Double, count: Int, state: ContentState = ContentState.Active) =
        ContentItem(
            id = "c1", title = "t", body = "b", contentHash = "h",
            createdAt = Instant.now(), updatedAt = Instant.now(),
            state = state, averageRating = avg, ratingCount = count,
            favoriteCount = 0, downloadCount = 0
        )

    @Test
    fun `avg 2_4 with 10 ratings triggers demotion`() {
        val decision = GovernanceEvaluator.evaluate(item(2.4, 10))
        assertThat(decision).isInstanceOf(GovernanceEvaluator.Decision.Demote::class.java)
    }

    @Test
    fun `avg 2_5 exactly does not trigger demotion`() {
        val decision = GovernanceEvaluator.evaluate(item(2.5, 20))
        assertThat(decision).isEqualTo(GovernanceEvaluator.Decision.NoChange)
    }

    @Test
    fun `low avg with fewer than 10 ratings no demotion`() {
        val decision = GovernanceEvaluator.evaluate(item(1.0, 9))
        assertThat(decision).isEqualTo(GovernanceEvaluator.Decision.NoChange)
    }

    @Test
    fun `already demoted item returns no change`() {
        val decision = GovernanceEvaluator.evaluate(item(1.0, 20, ContentState.Demoted))
        assertThat(decision).isEqualTo(GovernanceEvaluator.Decision.NoChange)
    }

    @Test
    fun `quarantined item returns no change`() {
        val decision = GovernanceEvaluator.evaluate(item(1.0, 20, ContentState.Quarantined))
        assertThat(decision).isEqualTo(GovernanceEvaluator.Decision.NoChange)
    }

    @Test
    fun `high similarity triggers duplicate`() {
        assertThat(GovernanceEvaluator.evaluateDuplicate(0.85)).isTrue()
    }

    @Test
    fun `similarity exactly 80 percent does not trigger`() {
        assertThat(GovernanceEvaluator.evaluateDuplicate(0.80)).isFalse()
    }
}
