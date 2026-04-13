package com.fieldtripops.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fieldtripops.data.dao.UserDao
import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.entity.UserEntity
import com.fieldtripops.domain.model.Credential
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class AuthRepositoryImplTest {

    private lateinit var database: FieldTripDatabase
    private lateinit var repository: AuthRepositoryImpl
    private lateinit var userDao: UserDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FieldTripDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = AuthRepositoryImpl(database.credentialDao())
        userDao = database.userDao()

        // Insert user for FK constraint
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

    private val testCredential = Credential(
        userId = "u1",
        passwordHash = "hash123",
        salt = "salt123",
        failedAttempts = 0,
        lockedUntil = null,
        lastLoginAt = null
    )

    @Test
    fun saveAndGet() = runTest {
        repository.saveCredential(testCredential)
        val result = repository.getCredential("u1")
        assertThat(result).isNotNull()
        assertThat(result!!.passwordHash).isEqualTo("hash123")
        assertThat(result.failedAttempts).isEqualTo(0)
    }

    @Test
    fun incrementFailedAttempts() = runTest {
        repository.saveCredential(testCredential)
        repository.incrementFailedAttempts("u1")
        val result = repository.getCredential("u1")
        assertThat(result!!.failedAttempts).isEqualTo(1)
    }

    @Test
    fun resetFailedAttempts() = runTest {
        repository.saveCredential(testCredential.copy(failedAttempts = 3))
        repository.resetFailedAttempts("u1")
        val result = repository.getCredential("u1")
        assertThat(result!!.failedAttempts).isEqualTo(0)
    }

    @Test
    fun lockAndUnlock() = runTest {
        repository.saveCredential(testCredential)
        val lockTime = Instant.now().plusSeconds(900)
        repository.lockAccount("u1", lockTime)
        val result = repository.getCredential("u1")
        assertThat(result!!.lockedUntil).isNotNull()
    }

    @Test
    fun updateLastLogin() = runTest {
        repository.saveCredential(testCredential)
        val now = Instant.now()
        repository.updateLastLogin("u1", now)
        val result = repository.getCredential("u1")
        assertThat(result!!.lastLoginAt).isNotNull()
    }

    @Test
    fun getNonExistent_returnsNull() = runTest {
        val result = repository.getCredential("nonexistent")
        assertThat(result).isNull()
    }
}
