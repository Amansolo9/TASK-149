package com.fieldtripops.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fieldtripops.attachment.AttachmentStorage
import com.fieldtripops.attachment.PendingAttachment
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.entity.BookingOrderEntity
import com.fieldtripops.data.repository.AttachmentRepositoryImpl
import com.fieldtripops.data.repository.BookingRepositoryImpl
import com.fieldtripops.data.repository.ClaimRepositoryImpl
import com.fieldtripops.domain.model.ClaimClassification
import com.fieldtripops.domain.model.ClaimStyle
import com.fieldtripops.domain.model.Responsibility
import com.fieldtripops.domain.model.Role
import com.fieldtripops.security.NoopSensitiveFieldCodec
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class FileClaimAtomicityTest {

    private lateinit var db: FieldTripDatabase
    private lateinit var fileClaimUseCase: FileClaimUseCase
    private lateinit var bookingRepo: BookingRepositoryImpl
    private lateinit var attachmentRepo: AttachmentRepositoryImpl
    private lateinit var claimRepo: ClaimRepositoryImpl
    private lateinit var session: SessionManager
    private lateinit var failingStorage: FailingStorage

    private class FailingStorage(private val realStore: Boolean) : AttachmentStorage {
        var fail: Boolean = false
        var pathBase = "/tmp/attach"
        override suspend fun store(id: String, data: ByteArray): String {
            if (fail) error("Simulated disk failure for $id")
            return "$pathBase/$id"
        }
        override suspend fun retrieve(path: String): ByteArray? = null
        override suspend fun delete(path: String): Boolean = true
        override suspend fun exists(path: String): Boolean = true
        override fun pathFor(id: String): String = "$pathBase/$id"
    }

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FieldTripDatabase::class.java
        ).allowMainThreadQueries().build()
        failingStorage = FailingStorage(realStore = false)
        attachmentRepo = AttachmentRepositoryImpl(db.attachmentRefDao(), failingStorage)
        bookingRepo = BookingRepositoryImpl(db.bookingOrderDao())
        claimRepo = ClaimRepositoryImpl(
            db, db.claimTicketDao(), db.ticketStatusHistoryDao(),
            db.investigationNoteDao(), db.appealRecordDao(),
            NoopSensitiveFieldCodec()
        )
        session = SessionManager().also {
            it.set(SessionContext("alice", "Alice", setOf(Role.Traveler), "s1"))
        }
        val logger: AuditLogger = mockk(relaxed = true)
        fileClaimUseCase = FileClaimUseCase(
            db, claimRepo, bookingRepo, attachmentRepo, logger, session
        )

        // Seed an owned booking with trip that ended 1 day ago (within 7-day claim window)
        val now = Instant.now()
        val tripEnd = now.minus(Duration.ofDays(1))
        val tripStart = tripEnd.minus(Duration.ofDays(3))
        val nowMs = now.toEpochMilli()
        kotlinx.coroutines.runBlocking {
            db.userDao().insert(
                com.fieldtripops.data.entity.UserEntity(
                    "alice", "alice", "Alice", true, nowMs, nowMs
                )
            )
            db.bookingOrderDao().upsert(
                BookingOrderEntity(
                    id = "b1", itineraryId = "i1", travelerId = "alice",
                    inventorySlotId = "slot-1", partySize = 2, state = "Booked",
                    createdAt = nowMs, updatedAt = nowMs,
                    confirmedAt = null, confirmedBy = null,
                    cancelledAt = null, cancelReason = null,
                    lastActivityAt = nowMs,
                    tripStartAt = tripStart.toEpochMilli(),
                    tripEndAt = tripEnd.toEpochMilli(),
                    paidTotalCents = 10000
                )
            )
        }
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun `claim with at least one valid evidence persists with linkage`() = runTest {
        val pending = PendingAttachment(
            fileName = "proof.jpg", mimeType = "image/jpeg",
            data = ByteArray(2048) { 1 }
        )
        val r = fileClaimUseCase.execute(
            "b1", ClaimStyle.REFUND_ONLY, ClaimClassification.PROVIDER_NO_SHOW,
            Responsibility.PROVIDER, "Did not show up", listOf(pending)
        ) as FileClaimUseCase.Result.Filed

        // Ticket persisted
        assertThat(claimRepo.findById(r.ticket.id)).isNotNull()
        // Attachment ref persisted and linked
        val refs = attachmentRepo.getRefsForEntity("ClaimTicket", r.ticket.id)
        assertThat(refs).hasSize(1)
        assertThat(refs.first().fileName).isEqualTo("proof.jpg")
    }

    @Test
    fun `claim without evidence is rejected (no rows persist)`() = runTest {
        val r = fileClaimUseCase.execute(
            "b1", ClaimStyle.REFUND_ONLY, ClaimClassification.PROVIDER_NO_SHOW,
            Responsibility.PROVIDER, "no proof", emptyList()
        )
        assertThat(r).isInstanceOf(FileClaimUseCase.Result.ValidationFailed::class.java)
        // No claim, no refs
        assertThat(claimRepo.findByTraveler("alice")).isEmpty()
    }

    @Test
    fun `oversized evidence is rejected and nothing persists`() = runTest {
        val tooBig = PendingAttachment(
            fileName = "big.jpg", mimeType = "image/jpeg",
            data = ByteArray((11L * 1024 * 1024).toInt()) { 0 }
        )
        val r = fileClaimUseCase.execute(
            "b1", ClaimStyle.REFUND_ONLY, ClaimClassification.PROVIDER_NO_SHOW,
            Responsibility.PROVIDER, "x", listOf(tooBig)
        )
        assertThat(r).isInstanceOf(FileClaimUseCase.Result.ValidationFailed::class.java)
        assertThat(claimRepo.findByTraveler("alice")).isEmpty()
    }

    @Test
    fun `disk failure after commit triggers compensating delete of refs`() = runTest {
        failingStorage.fail = true
        val pending = PendingAttachment(
            fileName = "x.jpg", mimeType = "image/jpeg",
            data = ByteArray(64) { 9 }
        )
        try {
            fileClaimUseCase.execute(
                "b1", ClaimStyle.REFUND_ONLY, ClaimClassification.PROVIDER_NO_SHOW,
                Responsibility.PROVIDER, "x", listOf(pending)
            )
        } catch (_: Throwable) {}
        // Compensating delete must remove the orphan ref so the invariant
        // "ref exists ⟹ bytes exist" holds.
        val tickets = claimRepo.findByTraveler("alice")
        for (t in tickets) {
            assertThat(attachmentRepo.getRefsForEntity("ClaimTicket", t.id)).isEmpty()
        }
    }

    @Test
    fun `traveler cannot file claim against another travelers booking`() = runTest {
        // Seed Bob's booking
        val now = Instant.now()
        val tripEnd = now.minus(Duration.ofDays(1))
        val tripStart = tripEnd.minus(Duration.ofDays(3))
        val nowMs = now.toEpochMilli()
        db.userDao().insert(
            com.fieldtripops.data.entity.UserEntity(
                "bob", "bob", "Bob", true, nowMs, nowMs
            )
        )
        db.bookingOrderDao().upsert(
            BookingOrderEntity(
                id = "b2", itineraryId = "i2", travelerId = "bob",
                inventorySlotId = "slot-2", partySize = 1, state = "Booked",
                createdAt = nowMs, updatedAt = nowMs,
                confirmedAt = null, confirmedBy = null,
                cancelledAt = null, cancelReason = null,
                lastActivityAt = nowMs,
                tripStartAt = tripStart.toEpochMilli(),
                tripEndAt = tripEnd.toEpochMilli(),
                paidTotalCents = 5000
            )
        )
        val pending = PendingAttachment("proof.jpg", "image/jpeg", ByteArray(64))
        try {
            fileClaimUseCase.execute(
                "b2", ClaimStyle.REFUND_ONLY, ClaimClassification.PROVIDER_NO_SHOW,
                Responsibility.PROVIDER, "x", listOf(pending)
            )
            assert(false) { "Should have thrown OwnershipViolationException" }
        } catch (e: SecurityException) {
            // expected
        }
        // Nothing persisted under bob
        assertThat(claimRepo.findByTraveler("bob")).isEmpty()
    }
}
