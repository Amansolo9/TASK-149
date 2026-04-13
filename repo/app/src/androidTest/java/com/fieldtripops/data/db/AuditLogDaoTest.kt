package com.fieldtripops.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fieldtripops.data.dao.AuditLogDao
import com.fieldtripops.data.entity.AuditLogEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuditLogDaoTest {

    private lateinit var database: FieldTripDatabase
    private lateinit var auditLogDao: AuditLogDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FieldTripDatabase::class.java
        ).allowMainThreadQueries().build()
        auditLogDao = database.auditLogDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun testEntry(
        id: String = "a1",
        actor: String = "user1",
        action: String = "LOGIN_SUCCESS",
        entityType: String = "Session",
        entityId: String = "s1",
        timestamp: Long = 1700000000000L
    ) = AuditLogEntity(
        id = id,
        actor = actor,
        action = action,
        entityType = entityType,
        entityId = entityId,
        timestamp = timestamp,
        details = null,
        checksum = "checksum_$id"
    )

    @Test
    fun insert_and_getByEntity() = runTest {
        auditLogDao.insert(testEntry())
        val results = auditLogDao.getByEntity("Session", "s1")
        assertThat(results).hasSize(1)
        assertThat(results[0].actor).isEqualTo("user1")
    }

    @Test
    fun getByActor() = runTest {
        auditLogDao.insert(testEntry("a1", "user1"))
        auditLogDao.insert(testEntry("a2", "user1", "LOGOUT"))
        auditLogDao.insert(testEntry("a3", "user2"))
        val results = auditLogDao.getByActor("user1", 10)
        assertThat(results).hasSize(2)
    }

    @Test
    fun getLastEntry_returns_most_recent() = runTest {
        auditLogDao.insert(testEntry("a1", timestamp = 1700000000000L))
        auditLogDao.insert(testEntry("a2", timestamp = 1700000001000L))
        val last = auditLogDao.getLastEntry()
        assertThat(last).isNotNull()
        assertThat(last!!.id).isEqualTo("a2")
    }

    @Test
    fun getLastEntry_empty_returns_null() = runTest {
        val last = auditLogDao.getLastEntry()
        assertThat(last).isNull()
    }

    @Test
    fun multiple_entries_for_same_entity() = runTest {
        auditLogDao.insert(testEntry("a1", timestamp = 1700000000000L))
        auditLogDao.insert(testEntry("a2", action = "LOGOUT", timestamp = 1700000001000L))
        val results = auditLogDao.getByEntity("Session", "s1")
        assertThat(results).hasSize(2)
        // Ordered by timestamp ASC
        assertThat(results[0].id).isEqualTo("a1")
        assertThat(results[1].id).isEqualTo("a2")
    }
}
