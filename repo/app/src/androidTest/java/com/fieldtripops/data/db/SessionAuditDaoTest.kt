package com.fieldtripops.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fieldtripops.data.dao.SessionAuditDao
import com.fieldtripops.data.dao.UserDao
import com.fieldtripops.data.entity.SessionAuditEntity
import com.fieldtripops.data.entity.UserEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionAuditDaoTest {

    private lateinit var database: FieldTripDatabase
    private lateinit var sessionDao: SessionAuditDao
    private lateinit var userDao: UserDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FieldTripDatabase::class.java
        ).allowMainThreadQueries().build()
        sessionDao = database.sessionAuditDao()
        userDao = database.userDao()

        // Insert a user for FK constraint
        kotlinx.coroutines.runBlocking {
            userDao.insert(
                UserEntity("u1", "testuser", "Test", true, 1700000000000L, 1700000000000L)
            )
        }
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun testSession(id: String = "s1") = SessionAuditEntity(
        id = id,
        userId = "u1",
        startedAt = 1700000000000L,
        lastActiveAt = 1700000000000L,
        endedAt = null,
        endReason = null
    )

    @Test
    fun insertAndGetActive() = runTest {
        sessionDao.insert(testSession())
        val active = sessionDao.getActiveByUserId("u1")
        assertThat(active).isNotNull()
        assertThat(active!!.id).isEqualTo("s1")
    }

    @Test
    fun touchLastActive() = runTest {
        sessionDao.insert(testSession())
        sessionDao.touchLastActive("s1", 1700000001000L)
        val session = sessionDao.getActiveByUserId("u1")
        assertThat(session!!.lastActiveAt).isEqualTo(1700000001000L)
    }

    @Test
    fun endSession() = runTest {
        sessionDao.insert(testSession())
        sessionDao.endSession("s1", 1700000002000L, "user_logout")
        val active = sessionDao.getActiveByUserId("u1")
        assertThat(active).isNull()
    }

    @Test
    fun getExpiredSessions() = runTest {
        sessionDao.insert(testSession("s1").copy(lastActiveAt = 1700000000000L))
        sessionDao.insert(testSession("s2").copy(lastActiveAt = 1700000002000L))
        // Threshold at 1700000001000 - s1 should be expired
        val expired = sessionDao.getExpiredSessions(1700000001000L)
        assertThat(expired).hasSize(1)
        assertThat(expired[0].id).isEqualTo("s1")
    }
}
