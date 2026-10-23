package io.github.imlemos.clock.core.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * Schema history lives in app/schemas and is committed.
 *
 * Until the first public release, version 1 may be regenerated. From then on, every schema change
 * bumps the version and ships with a migration and a migration test.
 */
@Database(
    entities = [AlarmEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        const val VERSION = 1
        const val NAME = "clock.db"
    }
}
