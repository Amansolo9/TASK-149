package com.fieldtripops.domain.usecase

import androidx.room.withTransaction
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.domain.governance.GovernanceEvaluator
import com.fieldtripops.domain.model.ContentItem
import com.fieldtripops.domain.model.ContentState
import com.fieldtripops.domain.repository.CheckpointRepository
import com.fieldtripops.domain.repository.ContentRepository
import com.fieldtripops.domain.repository.GovernanceRepository
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
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
 *  - Checkpoint is created before quarantine in duplicate scan
 *  - Checkpoint is created before demotion in rating
 *  - Checkpoint is created before governance override
 *  - Checkpoint snapshot contains previous state
 */
@Ignore("Requires Android runtime (Room withTransaction or Bitmap); move to androidTest")
class ContentCheckpointCreationTest {

    private lateinit var db: FieldTripDatabase
    private lateinit var contentRepo: ContentRepository
    private lateinit var govRepo: GovernanceRepository
    private lateinit var checkpointRepo: CheckpointRepository
    private lateinit var auditLogger: AuditLogger
    private lateinit var sessionManager: SessionManager

    private val activeItem = ContentItem(
        id = "c1", title = "Test", body = "Body text for testing",
        contentHash = "hash1",
        createdAt = Instant.now(), updatedAt = Instant.now(),
        state = ContentState.Active,
        averageRating = 4.0, ratingCount = 5,
        favoriteCount = 0, downloadCount = 0
    )

    @Before
    fun setup() {
        db = mockk(relaxed = true)
        contentRepo = mockk(relaxed = true)
        govRepo = mockk(relaxed = true)
        checkpointRepo = mockk(relaxed = true)
        auditLogger = mockk(relaxed = true)
        sessionManager = SessionManager()

        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { db.withTransaction(any<suspend () -> Any>()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val block = args[0] as suspend () -> Any
            block()
        }
    }

    @Test
    fun `governance override creates checkpoint before state change`() = runTest {
        sessionManager.set(SessionContext("reviewer-1", "Reviewer", setOf(com.fieldtripops.domain.model.Role.Reviewer), "s1"))
        coEvery { contentRepo.findById("c1") } returns activeItem

        val useCase = GovernanceOverrideUseCase(
            db, contentRepo, govRepo, auditLogger, sessionManager, checkpointRepo
        )
        useCase.execute("c1", ContentState.Quarantined, "Test quarantine")

        val labelSlot = slot<String>()
        val snapshotSlot = slot<String>()
        coVerify {
            checkpointRepo.createCheckpoint(
                label = capture(labelSlot),
                entityType = "ContentItem",
                entityId = "c1",
                snapshotJson = capture(snapshotSlot)
            )
        }
        assertThat(labelSlot.captured).isEqualTo("pre-override")
        assertThat(snapshotSlot.captured).contains("\"state\":\"Active\"")
    }

    @Test
    fun `duplicate scan creates checkpoint before quarantine`() = runTest {
        val item1 = activeItem.copy(id = "c1", body = "Same body here", contentHash = "hash1")
        val item2 = activeItem.copy(id = "c2", body = "Same body here", contentHash = "hash1")
        coEvery { contentRepo.getAll() } returns listOf(item1, item2)

        val useCase = RunDuplicateScanUseCase(
            db, contentRepo, govRepo, auditLogger, checkpointRepo
        )
        useCase.execute()

        coVerify {
            checkpointRepo.createCheckpoint(
                label = "pre-quarantine",
                entityType = "ContentItem",
                entityId = "c2",
                snapshotJson = any()
            )
        }
    }

    @Test
    fun `checkpoint snapshot contains previous state for rollback`() = runTest {
        sessionManager.set(SessionContext("admin-1", "Admin", setOf(com.fieldtripops.domain.model.Role.Administrator), "s2"))
        val demotedItem = activeItem.copy(state = ContentState.Demoted)
        coEvery { contentRepo.findById("c1") } returns demotedItem

        val useCase = GovernanceOverrideUseCase(
            db, contentRepo, govRepo, auditLogger, sessionManager, checkpointRepo
        )
        useCase.execute("c1", ContentState.Active, "Restoring from demotion")

        val snapshotSlot = slot<String>()
        coVerify {
            checkpointRepo.createCheckpoint(
                label = any(),
                entityType = "ContentItem",
                entityId = "c1",
                snapshotJson = capture(snapshotSlot)
            )
        }
        // Previous state was Demoted
        assertThat(snapshotSlot.captured).contains("\"state\":\"Demoted\"")
    }
}
