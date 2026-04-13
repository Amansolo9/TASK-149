package com.fieldtripops.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.domain.model.ItineraryDraft
import com.fieldtripops.security.AesSensitiveFieldCodec
import com.fieldtripops.security.NoopSensitiveFieldCodec
import com.fieldtripops.security.SensitiveFieldCodec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * Verifies that ItineraryRepositoryImpl encrypts the `notes` column at rest
 * using a stub codec that simulates encryption (prefix wrapping). We don't
 * test against the real AndroidKeyStore-backed AesSensitiveFieldCodec here
 * because instrumented tests on emulators have flaky keystore access; the
 * real codec is exercised in unit + integration around its envelope contract.
 */
@RunWith(AndroidJUnit4::class)
class ItineraryEncryptionTest {

    private class TestPrefixCodec : SensitiveFieldCodec {
        override fun encrypt(plaintext: String?): String? =
            if (plaintext.isNullOrEmpty()) plaintext else "TEST:$plaintext"
        override fun decrypt(stored: String?): String? =
            if (stored.isNullOrEmpty() || !stored.startsWith("TEST:")) stored
            else stored.removePrefix("TEST:")
    }

    private lateinit var db: FieldTripDatabase
    private lateinit var repo: ItineraryRepositoryImpl
    private val codec = TestPrefixCodec()

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FieldTripDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = ItineraryRepositoryImpl(db.itineraryDraftDao(), codec)
    }

    @After fun teardown() { db.close() }

    @Test
    fun `notes are encrypted in raw row but decrypted via repository`() = runTest {
        val draft = ItineraryDraft(
            id = "d1", travelerId = "u1", travelerInitials = "JD",
            partySize = 2,
            startDate = LocalDate.now(), endDate = LocalDate.now().plusDays(3),
            notes = "Allergic to peanuts", itineraryType = "standard",
            createdAt = Instant.now(), updatedAt = Instant.now(), submitted = false
        )
        repo.save(draft)

        val raw = db.itineraryDraftDao().findById("d1")!!
        assertThat(raw.notes).isEqualTo("TEST:Allergic to peanuts")
        assertThat(raw.notes).doesNotContain("Allergic to peanuts".take(8))
        // start of plaintext should NOT appear without the prefix
        assertThat(raw.notes!!.startsWith("Allergic")).isFalse()

        val loaded = repo.findById("d1")!!
        assertThat(loaded.notes).isEqualTo("Allergic to peanuts")
    }

    @Test
    fun `null notes round-trip safely`() = runTest {
        val draft = ItineraryDraft(
            id = "d2", travelerId = "u1", travelerInitials = "JD",
            partySize = 1,
            startDate = LocalDate.now(), endDate = LocalDate.now().plusDays(1),
            notes = null, itineraryType = "standard",
            createdAt = Instant.now(), updatedAt = Instant.now(), submitted = false
        )
        repo.save(draft)
        assertThat(db.itineraryDraftDao().findById("d2")!!.notes).isNull()
        assertThat(repo.findById("d2")!!.notes).isNull()
    }

    @Test
    fun `noop codec leaves notes plaintext for backward compat`() = runTest {
        val plainRepo = ItineraryRepositoryImpl(db.itineraryDraftDao(), NoopSensitiveFieldCodec())
        val draft = ItineraryDraft(
            id = "d3", travelerId = "u1", travelerInitials = "JD", partySize = 1,
            startDate = LocalDate.now(), endDate = LocalDate.now().plusDays(1),
            notes = "open text", itineraryType = "standard",
            createdAt = Instant.now(), updatedAt = Instant.now(), submitted = false
        )
        plainRepo.save(draft)
        assertThat(db.itineraryDraftDao().findById("d3")!!.notes).isEqualTo("open text")
    }
}
