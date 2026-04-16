package com.fieldtripops.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fieldtripops.audit.AuditLogger
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.repository.CheckpointRepositoryImpl
import com.fieldtripops.data.repository.ContentRepositoryImpl
import com.fieldtripops.domain.model.ContentItem
import com.fieldtripops.domain.model.ContentState
import com.fieldtripops.domain.repository.GovernanceRepository
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Tests proving:
 *  - Checkpoint is created before governance override
 *  - Checkpoint snapshot contains previous state
 *  - Rollback via stored checkpoint restores prior state
 *
 * Uses Robolectric + real in-memory Room DB (no withTransaction mocks).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ContentCheckpointCreationTest {

    private lateinit var db: FieldTripDatabase
    private lateinit var contentRepo: ContentRepositoryImpl
    private lateinit var checkpointRepo: CheckpointRepositoryImpl
    private lateinit var govRepo: GovernanceRepository
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
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FieldTripDatabase::class.java
        ).allowMainThreadQueries().build()

        contentRepo = ContentRepositoryImpl(db, db.contentItemDao(), db.contentMetricDao())
        checkpointRepo = CheckpointRepositoryImpl(db.transactionCheckpointDao())
        govRepo = mockk(relaxed = true)
        auditLogger = mockk(relaxed = true)
        sessionManager = SessionManager()

        runBlocking { contentRepo.save(activeItem) }
    }

    @After fun teardown() { db.close() }

    @Test
    fun `governance override creates checkpoint before state change`() = runTest {
        sessionManager.set(SessionContext(
            "reviewer-1", "Reviewer",
            setOf(com.fieldtripops.domain.model.Role.Reviewer), "s1"
        ))

        val useCase = GovernanceOverrideUseCase(
            db, contentRepo, govRepo, auditLogger, sessionManager, checkpointRepo
        )
        useCase.execute("c1", ContentState.Quarantined, "Test quarantine")

        val checkpoints = checkpointRepo.getHistory("ContentItem", "c1")
        assertThat(checkpoints).isNotEmpty()
        assertThat(checkpoints.first().label).isEqualTo("pre-override")
        assertThat(checkpoints.first().snapshotJson).contains("\"state\":\"Active\"")
    }

    @Test
    fun `checkpoint snapshot contains previous state for rollback`() = runTest {
        val demotedItem = activeItem.copy(state = ContentState.Demoted)
        runBlocking { contentRepo.updateState(demotedItem.id, ContentState.Demoted, Instant.now()) }

        sessionManager.set(SessionContext(
            "admin-1", "Admin",
            setOf(com.fieldtripops.domain.model.Role.Administrator), "s2"
        ))

        val useCase = GovernanceOverrideUseCase(
            db, contentRepo, govRepo, auditLogger, sessionManager, checkpointRepo
        )
        useCase.execute("c1", ContentState.Active, "Restoring from demotion")

        val checkpoints = checkpointRepo.getHistory("ContentItem", "c1")
        assertThat(checkpoints.any { it.snapshotJson.contains("\"state\":\"Demoted\"") }).isTrue()
    }

    @Test
    fun `rollback via checkpoint restores prior state`() = runTest {
        sessionManager.set(SessionContext(
            "reviewer-1", "Reviewer",
            setOf(com.fieldtripops.domain.model.Role.Reviewer), "s3"
        ))

        // Override Active -> Quarantined (creates checkpoint)
        val override = GovernanceOverrideUseCase(
            db, contentRepo, govRepo, auditLogger, sessionManager, checkpointRepo
        )
        override.execute("c1", ContentState.Quarantined, "Flagged")

        assertThat(contentRepo.findById("c1")!!.state).isEqualTo(ContentState.Quarantined)

        // Roll back
        val rollback = RollbackUseCase(db, checkpointRepo, auditLogger)
        val result = rollback.execute("ContentItem", "c1", "reviewer-1") { snapshotJson ->
            val stateMatch = """"state"\s*:\s*"(\w+)"""".toRegex().find(snapshotJson)
            val prev = ContentState.valueOf(stateMatch!!.groupValues[1])
            contentRepo.updateState("c1", prev, Instant.now())
        }

        assertThat(result).isInstanceOf(RollbackUseCase.Result.RolledBack::class.java)
        assertThat(contentRepo.findById("c1")!!.state).isEqualTo(ContentState.Active)
    }

    @Test
    fun `rollback fails safely when no checkpoint exists`() = runTest {
        val rollback = RollbackUseCase(db, checkpointRepo, auditLogger)
        val result = rollback.execute("ContentItem", "never-touched", "reviewer-1") {
            error("Should not be called")
        }
        assertThat(result).isInstanceOf(RollbackUseCase.Result.NoCheckpointFound::class.java)
    }
}
