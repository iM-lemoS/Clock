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
    entities = [AlarmEntity::class, TrashEntity::class, AlarmGroupEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        // 2: single use, armed time, trash.
        AutoMigration(from = 1, to = 2),
        // 3: vibration pattern, gradual volume, ringtone title.
        AutoMigration(from = 2, to = 3),
        // 4: alarm groups.
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    abstract fun trashDao(): TrashDao

    abstract fun groupDao(): AlarmGroupDao

    companion object {
        const val VERSION = 4
        const val NAME = "clock.db"
    }
}
