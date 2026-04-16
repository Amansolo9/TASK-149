package com.fieldtripops.ui.review

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.fieldtripops.domain.model.ContentItem
import com.fieldtripops.domain.model.ContentState
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.model.TransactionCheckpoint
import com.fieldtripops.domain.repository.ContentRepository
import com.fieldtripops.domain.usecase.RollbackUseCase
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class QuarantineViewModelTest {

    @get:Rule val rule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()

    private lateinit var contentRepo: ContentRepository
    private lateinit var rollbackUseCase: RollbackUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var vm: QuarantineViewModel

    private val quarantinedItem = ContentItem(
        id = "c1", title = "T", body = "B", contentHash = "h",
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        state = ContentState.Quarantined,
        averageRating = 2.0, ratingCount = 10,
        favoriteCount = 0, downloadCount = 0
    )

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        contentRepo = mockk(relaxed = true)
        rollbackUseCase = mockk()
        sessionManager = SessionManager()
        vm = QuarantineViewModel(contentRepo, rollbackUseCase, sessionManager)
    }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `traveler cannot load quarantine`() = runTest {
        sessionManager.set(SessionContext("u1", "U", setOf(Role.Traveler), "s1"))
        vm.load()
        advanceUntilIdle()
        assertThat(vm.items.value).isEmpty()
        assertThat(vm.message.value).contains("Not authorized")
    }

    @Test
    fun `reviewer loads only quarantined items`() = runTest {
        sessionManager.set(SessionContext("rev", "Rev", setOf(Role.Reviewer), "s2"))
        val active = quarantinedItem.copy(id = "c2", state = ContentState.Active)
        coEvery { contentRepo.getAll() } returns listOf(quarantinedItem, active)

        vm.load()
        advanceUntilIdle()
        assertThat(vm.items.value).hasSize(1)
        assertThat(vm.items.value?.first()?.id).isEqualTo("c1")
    }

    @Test
    fun `restore uses rollback use case - not governance override`() = runTest {
        sessionManager.set(SessionContext("rev", "Rev", setOf(Role.Reviewer), "s3"))
        val checkpoint = TransactionCheckpoint(
            id = "cp1", label = "pre-quarantine",
            entityType = "ContentItem", entityId = "c1",
            snapshotJson = """{"state":"Active"}""",
            createdAt = Instant.EPOCH, rolledBack = false
        )
        coEvery { rollbackUseCase.execute(any(), any(), any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val restorer = arg<suspend (String) -> Unit>(3)
            kotlinx.coroutines.runBlocking { restorer(checkpoint.snapshotJson) }
            RollbackUseCase.Result.RolledBack(checkpoint)
        }
        coEvery { contentRepo.getAll() } returns emptyList()

        vm.restore("c1", "reviewer click")
        advanceUntilIdle()

        coVerify { rollbackUseCase.execute("ContentItem", "c1", "rev", any()) }
        assertThat(vm.message.value).contains("Rolled back")
    }

    @Test
    fun `restore when no checkpoint publishes message`() = runTest {
        sessionManager.set(SessionContext("rev", "Rev", setOf(Role.Reviewer), "s4"))
        coEvery { rollbackUseCase.execute(any(), any(), any(), any()) } returns
            RollbackUseCase.Result.NoCheckpointFound
        coEvery { contentRepo.getAll() } returns emptyList()

        vm.restore("c1", "reason")
        advanceUntilIdle()
        assertThat(vm.message.value).contains("No rollback checkpoint")
    }

    @Test
    fun `unauthorized restore publishes authz message`() = runTest {
        sessionManager.set(SessionContext("u1", "U", setOf(Role.Traveler), "s5"))

        vm.restore("c1", "reason")
        advanceUntilIdle()
        assertThat(vm.message.value).contains("Not authorized")
    }

    @Test
    fun `clearMessage nulls the message`() = runTest {
        sessionManager.set(SessionContext("u1", "U", setOf(Role.Traveler), "s6"))
        vm.load()
        advanceUntilIdle()
        vm.clearMessage()
        assertThat(vm.message.value).isNull()
    }
}
