package com.fieldtripops.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fieldtripops.data.dao.UserDao
import com.fieldtripops.data.entity.UserEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserDaoTest {

    private lateinit var database: FieldTripDatabase
    private lateinit var userDao: UserDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FieldTripDatabase::class.java
        ).allowMainThreadQueries().build()
        userDao = database.userDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun testUser(id: String = "u1", username: String = "testuser") = UserEntity(
        id = id,
        username = username,
        displayName = "Test User",
        isActive = true,
        createdAt = 1700000000000L,
        updatedAt = 1700000000000L
    )

    @Test
    fun insertAndFindById() = runTest {
        val user = testUser()
        userDao.insert(user)
        val found = userDao.findById("u1")
        assertThat(found).isNotNull()
        assertThat(found!!.username).isEqualTo("testuser")
    }

    @Test
    fun findByUsername() = runTest {
        userDao.insert(testUser())
        val found = userDao.findByUsername("testuser")
        assertThat(found).isNotNull()
        assertThat(found!!.id).isEqualTo("u1")
    }

    @Test
    fun findByUsername_notFound() = runTest {
        val found = userDao.findByUsername("nonexistent")
        assertThat(found).isNull()
    }

    @Test
    fun getAll() = runTest {
        userDao.insert(testUser("u1", "user1"))
        userDao.insert(testUser("u2", "user2"))
        val all = userDao.getAll()
        assertThat(all).hasSize(2)
    }

    @Test
    fun upsert_replaces_existing() = runTest {
        userDao.insert(testUser())
        userDao.insert(testUser().copy(displayName = "Updated Name"))
        val found = userDao.findById("u1")
        assertThat(found!!.displayName).isEqualTo("Updated Name")
    }
}
