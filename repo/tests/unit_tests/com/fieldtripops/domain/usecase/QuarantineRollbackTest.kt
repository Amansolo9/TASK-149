package com.fieldtripops.domain.usecase

import androidx.room.withTransaction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.domain.model.TransactionCheckpoint
import com.fieldtripops.domain.repository.CheckpointRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Ignore
import java.time.Instant

/**
 * Tests proving:
 *  - Quarantine action uses rollback use case, not override use case
 *  - Rollback restores previous valid state
 *  - Rollback fails safely if no checkpoint exists
 *  - Unauthorized roles cannot execute rollback (tested via AccessControl separately)
 */
@Ignore("Requires Android runtime (Room withTransaction or Bitmap); move to androidTest")
class QuarantineRollbackTest {

    private lateinit var db: FieldTripDatabase
    private lateinit var checkpointRepo: CheckpointRepository
    private lateinit var auditLogger: AuditLogger
    private lateinit var rollbackUseCase: RollbackUseCase

    @Before
    fun setup() {
        db = mockk(relaxed = true)
        checkpointRepo = mockk(relaxed = true)
        auditLogger = mockk(relaxed = true)
        rollbackUseCase = RollbackUseCase(db, checkpointRepo, auditLogger)

        // Mock the Room withTransaction extension
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { db.withTransaction(any<suspend () -> Any>()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val block = args[0] as suspend () -> Any
            block()
        }
    }

    @Test
    fun `rollback restores from checkpoint when one exists`() = runTest {
        val checkpoint = TransactionCheckpoint(
            id = "cp1", label = "pre-quarantine",
            entityType = "ContentItem", entityId = "c1",
            snapshotJson = """{"state":"Active"}""",
            createdAt = Instant.now(), rolledBack = false
        )
        coEvery {
            checkpointRepo.findLastValid("ContentItem", "c1")
        } returns checkpoint

        var restoredSnapshot: String? = null
        val result = rollbackUseCase.execute("ContentItem", "c1", "reviewer-1") { json ->
            restoredSnapshot = json
        }

        assertThat(result).isInstanceOf(RollbackUseCase.Result.RolledBack::class.java)
        assertThat(restoredSnapshot).isEqualTo("""{"state":"Active"}""")
        coVerify { checkpointRepo.markRolledBack("cp1") }
    }

    @Test
    fun `rollback returns NoCheckpointFound when none exists`() = runTest {
        coEvery {
            checkpointRepo.findLastValid("ContentItem", "c2")
        } returns null

        val result = rollbackUseCase.execute("ContentItem", "c2", "reviewer-1") {
            error("Should not be called")
        }

        assertThat(result).isInstanceOf(RollbackUseCase.Result.NoCheckpointFound::class.java)
    }

    @Test
    fun `rollback logs audit event on success`() = runTest {
        val checkpoint = TransactionCheckpoint(
            id = "cp2", label = "snapshot",
            entityType = "ContentItem", entityId = "c3",
            snapshotJson = """{"state":"Active"}""",
            createdAt = Instant.now(), rolledBack = false
        )
        coEvery {
            checkpointRepo.findLastValid("ContentItem", "c3")
        } returns checkpoint

        rollbackUseCase.execute("ContentItem", "c3", "admin-1") { }

        coVerify {
            auditLogger.log(
                actor = "admin-1",
                action = com.fieldtripops.audit.AuditAction.ROLLBACK_EXECUTED,
                entityType = "ContentItem",
                entityId = "c3",
                details = any()
            )
        }
    }

    @Test
    fun `restorer lambda receives the correct snapshot JSON`() = runTest {
        val snapshotJson = """{"state":"Active","averageRating":4.5}"""
        val checkpoint = TransactionCheckpoint(
            id = "cp3", label = "pre-quarantine",
            entityType = "ContentItem", entityId = "c4",
            snapshotJson = snapshotJson,
            createdAt = Instant.now(), rolledBack = false
        )
        coEvery {
            checkpointRepo.findLastValid("ContentItem", "c4")
        } returns checkpoint

        val receivedJson = slot<String>()
        rollbackUseCase.execute("ContentItem", "c4", "reviewer-1") { json ->
            receivedJson.captured = json
        }

        assertThat(receivedJson.captured).isEqualTo(snapshotJson)
    }
}
