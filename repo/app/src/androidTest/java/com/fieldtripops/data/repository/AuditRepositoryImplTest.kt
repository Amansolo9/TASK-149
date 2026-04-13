package com.fieldtripops.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.domain.model.AuditEntry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class AuditRepositoryImplTest {

    private lateinit var database: FieldTripDatabase
    private lateinit var repository: AuditRepositoryImpl

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FieldTripDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = AuditRepositoryImpl(database.auditLogDao())
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun testEntry(id: String = "e1", actor: String = "user1") = AuditEntry(
        id = id,
        actor = actor,
        action = "LOGIN_SUCCESS",
        entityType = "Session",
        entityId = "s1",
        timestamp = Instant.ofEpochMilli(1700000000000L),
        details = null,
        checksum = "checksum_$id"
    )

    @Test
    fun appendAndRetrieveByEntity() = runTest {
        repository.append(testEntry())
        val results = repository.getByEntity("Session", "s1")
        assertThat(results).hasSize(1)
        assertThat(results[0].actor).isEqualTo("user1")
    }

    @Test
    fun appendAndRetrieveByActor() = runTest {
        repository.append(testEntry("e1", "user1"))
        repository.append(testEntry("e2", "user1"))
        repository.append(testEntry("e3", "user2"))
        val results = repository.getByActor("user1", 10)
        assertThat(results).hasSize(2)
    }

    @Test
    fun getLastEntry() = runTest {
        repository.append(testEntry("e1").copy(timestamp = Instant.ofEpochMilli(1700000000000L)))
        repository.append(testEntry("e2").copy(timestamp = Instant.ofEpochMilli(1700000001000L)))
        val last = repository.getLastEntry()
        assertThat(last).isNotNull()
        assertThat(last!!.id).isEqualTo("e2")
    }
}
