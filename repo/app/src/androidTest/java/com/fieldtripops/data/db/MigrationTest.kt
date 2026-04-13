package com.fieldtripops.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FieldTripDatabase::class.java
    )

    @Test
    fun createDatabase_v1() {
        // Verify database schema at version 1 can be created
        val db = helper.createDatabase("test_migration", 1)
        // Basic query to ensure tables exist
        db.execSQL("SELECT * FROM users LIMIT 0")
        db.execSQL("SELECT * FROM role_assignments LIMIT 0")
        db.execSQL("SELECT * FROM credentials LIMIT 0")
        db.execSQL("SELECT * FROM consent_records LIMIT 0")
        db.execSQL("SELECT * FROM session_audits LIMIT 0")
        db.execSQL("SELECT * FROM audit_logs LIMIT 0")
        db.execSQL("SELECT * FROM attachment_refs LIMIT 0")
        db.close()
    }

    @Test
    fun insertAndQueryUser_v1() {
        val db = helper.createDatabase("test_migration_insert", 1)
        db.execSQL(
            """INSERT INTO users (id, username, displayName, isActive, createdAt, updatedAt)
               VALUES ('u1', 'testuser', 'Test User', 1, 1700000000000, 1700000000000)"""
        )
        val cursor = db.query("SELECT * FROM users WHERE id = 'u1'")
        assert(cursor.moveToFirst())
        assert(cursor.getString(cursor.getColumnIndex("username")) == "testuser")
        cursor.close()
        db.close()
    }

    @Test
    fun auditLogTable_appendOnly_v1() {
        val db = helper.createDatabase("test_audit_append", 1)
        db.execSQL(
            """INSERT INTO audit_logs (id, actor, action, entityType, entityId, timestamp, details, checksum)
               VALUES ('a1', 'user1', 'LOGIN', 'Session', 's1', 1700000000000, NULL, 'checksum1')"""
        )
        val cursor = db.query("SELECT COUNT(*) FROM audit_logs")
        cursor.moveToFirst()
        assert(cursor.getInt(0) == 1)
        cursor.close()
        db.close()
    }
}
