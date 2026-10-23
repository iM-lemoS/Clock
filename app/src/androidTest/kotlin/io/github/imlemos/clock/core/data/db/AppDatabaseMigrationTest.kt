package io.github.imlemos.clock.core.data.db

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates every exported schema. Each new database version adds a test here that creates the
 * previous version, inserts data, migrates and checks the data survived.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath(TEST_DB),
        driver = AndroidSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    @Test
    fun createsVersion1() = runTest {
        helper.createDatabase(1).use { connection ->
            connection.execSQL(
                "INSERT INTO alarm (hour, minute, recurrenceType, weekDays, dates, label, enabled, " +
                    "snoozeMinutes, ringDurationSeconds, vibrate, createdAt) " +
                    "VALUES (7, 30, 'ONCE', 0, '', '', 1, 5, 60, 1, 0)",
            )
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
