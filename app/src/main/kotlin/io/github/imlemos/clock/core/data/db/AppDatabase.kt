package io.github.imlemos.clock.core.data.db

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * Schema history lives in app/schemas and is committed.
 *
 * Every schema change bumps the version and ships with a migration (automatic when possible) and
 * a test in AppDatabaseMigrationTest.
 */
@Database(
    entities = [AlarmEntity::class, TrashEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        // 2: single use, armed time, trash.
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    abstract fun trashDao(): TrashDao

    companion object {
        const val VERSION = 2
        const val NAME = "clock.db"
    }
}
