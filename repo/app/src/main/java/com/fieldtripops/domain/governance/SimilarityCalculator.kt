package com.fieldtripops.domain.governance

import java.security.MessageDigest

/**
 * Deterministic local similarity computation for content deduplication.
 * Combines normalized-text hash equality with Jaccard n-gram similarity.
 * No external ML dependencies per PRD §6 out-of-scope constraints.
 */
object SimilarityCalculator {

    /**
     * Normalizes text: lowercase, collapse whitespace, strip punctuation.
     */
    fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun contentHash(text: String): String {
        val normalized = normalize(text)
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Character 3-gram Jaccard similarity [0.0, 1.0].
     * Exact duplicates return 1.0.
     */
    fun similarity(a: String, b: String): Double {
        val na = normalize(a)
        val nb = normalize(b)
        if (na == nb) return 1.0
        if (na.isEmpty() || nb.isEmpty()) return 0.0

        val gramsA = ngrams(na, 3)
        val gramsB = ngrams(nb, 3)
        if (gramsA.isEmpty() || gramsB.isEmpty()) return 0.0

        val intersection = gramsA.intersect(gramsB).size.toDouble()
        val union = gramsA.union(gramsB).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }

    private fun ngrams(text: String, n: Int): Set<String> {
        if (text.length < n) return setOf(text)
        return (0..text.length - n).map { text.substring(it, it + n) }.toSet()
    }
}
