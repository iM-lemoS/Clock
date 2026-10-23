package io.github.imlemos.clock.core.data.db

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Before
    fun deleteLeftoverDatabase() {
        instrumentation.targetContext.deleteDatabase(TEST_DB)
    }

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

    @Test
    fun migrates1To2KeepingAlarms() = runTest {
        helper.createDatabase(1).use { connection ->
            connection.execSQL(
                "INSERT INTO alarm (id, hour, minute, recurrenceType, weekDays, dates, label, enabled, " +
                    "snoozeMinutes, ringDurationSeconds, vibrate, createdAt) " +
                    "VALUES (1, 6, 45, 'WEEKLY', 31, '', 'Work', 1, 5, 60, 1, 1000)",
            )
        }
        helper.runMigrationsAndValidate(2, emptyList()).use { connection ->
            connection.prepare("SELECT hour, minute, label, singleUse, armedAt FROM alarm WHERE id = 1").use { stmt ->
                assertTrue(stmt.step())
                assertEquals(6, stmt.getInt(0))
                assertEquals(45, stmt.getInt(1))
                assertEquals("Work", stmt.getText(2))
                assertEquals(0, stmt.getInt(3))
                assertEquals(0L, stmt.getLong(4))
            }
            connection.prepare("SELECT COUNT(*) FROM trash").use { stmt ->
                assertTrue(stmt.step())
                assertEquals(0, stmt.getInt(0))
            }
        }
    }

    @Test
    fun migrates2To3WithSoundDefaults() = runTest {
        helper.createDatabase(2).use { connection ->
            connection.execSQL(
                "INSERT INTO alarm (id, hour, minute, recurrenceType, weekDays, dates, label, enabled, " +
                    "snoozeMinutes, ringDurationSeconds, vibrate, createdAt, singleUse, armedAt) " +
                    "VALUES (1, 7, 0, 'ONCE', 0, '', '', 1, 5, 120, 0, 1000, 0, 1000)",
            )
        }
        helper.runMigrationsAndValidate(3, emptyList()).use { connection ->
            connection.prepare(
                "SELECT ringDurationSeconds, vibrate, vibrationPattern, gradualEnabled, gradualTargetPercent, " +
                    "gradualSeconds, ringtoneTitle FROM alarm WHERE id = 1",
            ).use { stmt ->
                assertTrue(stmt.step())
                assertEquals(120, stmt.getInt(0))
                assertEquals(0, stmt.getInt(1))
                assertEquals("SYNC", stmt.getText(2))
                assertEquals(0, stmt.getInt(3))
                assertEquals(100, stmt.getInt(4))
                assertEquals(60, stmt.getInt(5))
                assertTrue(stmt.isNull(6))
            }
        }
    }

    @Test
    fun migrates3To4AddingGroups() = runTest {
        helper.createDatabase(3).use { connection ->
            connection.execSQL(
                "INSERT INTO alarm (id, hour, minute, recurrenceType, weekDays, dates, label, enabled, " +
                    "snoozeMinutes, ringDurationSeconds, vibrate, createdAt, singleUse, armedAt, vibrationPattern, " +
                    "gradualEnabled, gradualTargetPercent, gradualSeconds) " +
                    "VALUES (1, 7, 0, 'ONCE', 0, '', '', 1, 5, 60, 1, 1000, 0, 1000, 'SYNC', 0, 100, 60)",
            )
        }
        helper.runMigrationsAndValidate(4, emptyList()).use { connection ->
            connection.prepare("SELECT groupId FROM alarm WHERE id = 1").use { stmt ->
                assertTrue(stmt.step())
                assertTrue(stmt.isNull(0))
            }
            connection.prepare("SELECT COUNT(*) FROM alarm_group").use { stmt ->
                assertTrue(stmt.step())
                assertEquals(0, stmt.getInt(0))
            }
        }
    }

    @Test
    fun migrates4To5AddingLayouts() = runTest {
        helper.createDatabase(4).use { connection ->
            connection.execSQL(
                "INSERT INTO alarm (id, hour, minute, recurrenceType, weekDays, dates, label, enabled, " +
                    "snoozeMinutes, ringDurationSeconds, vibrate, createdAt, singleUse, armedAt, vibrationPattern, " +
                    "gradualEnabled, gradualTargetPercent, gradualSeconds) " +
                    "VALUES (1, 7, 0, 'ONCE', 0, '', '', 1, 5, 60, 1, 1000, 0, 1000, 'SYNC', 0, 100, 60)",
            )
        }
        helper.runMigrationsAndValidate(5, emptyList()).use { connection ->
            connection.prepare("SELECT layoutId FROM alarm WHERE id = 1").use { stmt ->
                assertTrue(stmt.step())
                assertTrue(stmt.isNull(0))
            }
            connection.prepare("SELECT COUNT(*) FROM alarm_layout").use { stmt ->
                assertTrue(stmt.step())
                assertEquals(0, stmt.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
