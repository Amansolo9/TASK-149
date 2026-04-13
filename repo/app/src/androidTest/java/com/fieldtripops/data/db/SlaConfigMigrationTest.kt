package com.fieldtripops.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fieldtripops.data.db.migration.Migrations
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SlaConfigMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FieldTripDatabase::class.java
    )

    @Test
    fun migrate5to6_creates_sla_tables() {
        var db = helper.createDatabase("test_db_5_6", 5)
        db.close()
        db = helper.runMigrationsAndValidate(
            "test_db_5_6", 6, true, Migrations.MIGRATION_5_6
        )
        // Round-trip: insert and read back from sla_config
        db.execSQL(
            """INSERT INTO sla_config
               (`key`, firstResponseMinutes, resolutionMinutes,
                travelerNoResponseHours, updatedAt, updatedBy)
               VALUES ('current', 60, 1440, 24, 1700000000000, 'admin')"""
        )
        val cursor = db.query("SELECT firstResponseMinutes FROM sla_config WHERE `key` = 'current'")
        assert(cursor.moveToFirst())
        assert(cursor.getInt(0) == 60)
        cursor.close()
        db.close()
    }
}
