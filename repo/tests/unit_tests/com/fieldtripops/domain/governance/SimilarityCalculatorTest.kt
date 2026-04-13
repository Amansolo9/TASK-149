package com.fieldtripops.domain.governance

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SimilarityCalculatorTest {

    @Test
    fun `identical text is 100 percent`() {
        assertThat(SimilarityCalculator.similarity("hello world", "hello world")).isEqualTo(1.0)
    }

    @Test
    fun `whitespace and case ignored`() {
        assertThat(SimilarityCalculator.similarity("Hello  World", "hello world")).isEqualTo(1.0)
    }

    @Test
    fun `totally different text is low similarity`() {
        val sim = SimilarityCalculator.similarity("apple banana cherry", "computer keyboard mouse")
        assertThat(sim).isLessThan(0.3)
    }

    @Test
    fun `near-duplicate text is high similarity`() {
        val a = "The quick brown fox jumps over the lazy dog."
        val b = "The quick brown fox jumps over the lazy cat."
        val sim = SimilarityCalculator.similarity(a, b)
        assertThat(sim).isGreaterThan(0.80)
    }

    @Test
    fun `normalize strips punctuation and collapses whitespace`() {
        val n = SimilarityCalculator.normalize("  Hello,   World!!  ")
        assertThat(n).isEqualTo("hello world")
    }

    @Test
    fun `hash is deterministic`() {
        val h1 = SimilarityCalculator.contentHash("Same content.")
        val h2 = SimilarityCalculator.contentHash("same content")
        // Same after normalization
        assertThat(h1).isEqualTo(h2)
    }

    @Test
    fun `empty strings return zero`() {
        assertThat(SimilarityCalculator.similarity("", "anything")).isEqualTo(0.0)
    }
}
