package com.fieldtripops.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fieldtripops.data.db.migration.Migrations
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V6V7MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FieldTripDatabase::class.java
    )

    @Test
    fun migrate6to7_creates_new_tables_and_columns() {
        var db = helper.createDatabase("test_db_6_7", 6)
        db.close()
        db = helper.runMigrationsAndValidate(
            "test_db_6_7", 7, true, Migrations.MIGRATION_6_7
        )
        // New tables
        db.execSQL("SELECT * FROM deletion_requests LIMIT 0")
        db.execSQL("SELECT * FROM sla_reminders LIMIT 0")
        db.execSQL("SELECT * FROM refund_rules LIMIT 0")
        db.execSQL("SELECT * FROM refund_rule_history LIMIT 0")
        // New columns on claim_tickets
        db.execSQL("SELECT compensationAmountCents, compensationCurrency, compensationBasis, " +
            "compensationApproverId, compensationApproverName, compensationDecidedAt, " +
            "compensationNote FROM claim_tickets LIMIT 0")
        db.close()
    }
}
