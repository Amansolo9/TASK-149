package com.fieldtripops.security

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.time.Instant

class AuditChecksumTest {

    private lateinit var checksummer: AuditChecksum

    @Before
    fun setup() {
        checksummer = AuditChecksum()
    }

    @Test
    fun `compute returns non-empty checksum`() {
        val checksum = checksummer.compute(
            actor = "user1",
            action = "LOGIN_SUCCESS",
            entityType = "Session",
            entityId = "sess-1",
            timestamp = Instant.now(),
            previousChecksum = null
        )
        assertThat(checksum).isNotEmpty()
    }

    @Test
    fun `same inputs produce same checksum`() {
        val ts = Instant.ofEpochMilli(1700000000000L)
        val c1 = checksummer.compute("user1", "LOGIN", "Session", "s1", ts, null)
        val c2 = checksummer.compute("user1", "LOGIN", "Session", "s1", ts, null)
        assertThat(c1).isEqualTo(c2)
    }

    @Test
    fun `different actor produces different checksum`() {
        val ts = Instant.ofEpochMilli(1700000000000L)
        val c1 = checksummer.compute("user1", "LOGIN", "Session", "s1", ts, null)
        val c2 = checksummer.compute("user2", "LOGIN", "Session", "s1", ts, null)
        assertThat(c1).isNotEqualTo(c2)
    }

    @Test
    fun `different action produces different checksum`() {
        val ts = Instant.ofEpochMilli(1700000000000L)
        val c1 = checksummer.compute("user1", "LOGIN", "Session", "s1", ts, null)
        val c2 = checksummer.compute("user1", "LOGOUT", "Session", "s1", ts, null)
        assertThat(c1).isNotEqualTo(c2)
    }

    @Test
    fun `chained checksum differs from genesis`() {
        val ts = Instant.ofEpochMilli(1700000000000L)
        val c1 = checksummer.compute("user1", "LOGIN", "Session", "s1", ts, null)
        val c2 = checksummer.compute("user1", "LOGIN", "Session", "s1", ts, "prevChecksum")
        assertThat(c1).isNotEqualTo(c2)
    }

    @Test
    fun `different previous checksums produce different results`() {
        val ts = Instant.ofEpochMilli(1700000000000L)
        val c1 = checksummer.compute("user1", "LOGIN", "Session", "s1", ts, "checksum_A")
        val c2 = checksummer.compute("user1", "LOGIN", "Session", "s1", ts, "checksum_B")
        assertThat(c1).isNotEqualTo(c2)
    }
}
