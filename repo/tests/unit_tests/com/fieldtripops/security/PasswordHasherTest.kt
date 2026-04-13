package com.fieldtripops.security

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class PasswordHasherTest {

    private lateinit var hasher: PasswordHasher

    @Before
    fun setup() {
        hasher = PasswordHasher()
    }

    @Test
    fun `generateSalt returns non-empty base64 string`() {
        val salt = hasher.generateSalt()
        assertThat(salt).isNotEmpty()
        assertThat(salt.length).isGreaterThan(10)
    }

    @Test
    fun `generateSalt returns different values each time`() {
        val salt1 = hasher.generateSalt()
        val salt2 = hasher.generateSalt()
        assertThat(salt1).isNotEqualTo(salt2)
    }

    @Test
    fun `hash returns non-empty result different from plaintext`() {
        val salt = hasher.generateSalt()
        val hash = hasher.hash("password123", salt)
        assertThat(hash).isNotEmpty()
        assertThat(hash).isNotEqualTo("password123")
    }

    @Test
    fun `same password and salt produce same hash`() {
        val salt = hasher.generateSalt()
        val hash1 = hasher.hash("password123", salt)
        val hash2 = hasher.hash("password123", salt)
        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `different salts produce different hashes`() {
        val salt1 = hasher.generateSalt()
        val salt2 = hasher.generateSalt()
        val hash1 = hasher.hash("password123", salt1)
        val hash2 = hasher.hash("password123", salt2)
        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `different passwords produce different hashes`() {
        val salt = hasher.generateSalt()
        val hash1 = hasher.hash("password123", salt)
        val hash2 = hasher.hash("password456", salt)
        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `verify returns true for correct password`() {
        val salt = hasher.generateSalt()
        val hash = hasher.hash("mySecret!", salt)
        assertThat(hasher.verify("mySecret!", salt, hash)).isTrue()
    }

    @Test
    fun `verify returns false for wrong password`() {
        val salt = hasher.generateSalt()
        val hash = hasher.hash("mySecret!", salt)
        assertThat(hasher.verify("wrongPassword", salt, hash)).isFalse()
    }

    @Test
    fun `verify returns false for wrong salt`() {
        val salt1 = hasher.generateSalt()
        val salt2 = hasher.generateSalt()
        val hash = hasher.hash("mySecret!", salt1)
        assertThat(hasher.verify("mySecret!", salt2, hash)).isFalse()
    }
}
