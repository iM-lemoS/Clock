package io.github.imlemos.clock.core.data.di

import android.content.Context
import android.os.UserManager
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room3.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.imlemos.clock.core.data.db.AlarmDao
import io.github.imlemos.clock.core.data.db.AppDatabase
import io.github.imlemos.clock.core.data.db.TrashDao
import io.github.imlemos.clock.core.data.prefs.DataStoreSettingsRepository
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import java.io.File
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/** A context whose files and databases are in device-protected (direct boot) storage. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeviceProtected

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /**
     * Everything lives in device-protected storage: alarms must be readable after a reboot before
     * the user unlocks the phone (direct boot). Nothing stored here is sensitive.
     */
    @Provides
    @Singleton
    @DeviceProtected
    fun provideDeviceProtectedContext(@ApplicationContext context: Context): Context =
        context.createDeviceProtectedStorageContext()

    @Provides
    @Singleton
    fun provideDatabase(@DeviceProtected context: Context): AppDatabase =
        Room.databaseBuilder<AppDatabase>(context, AppDatabase.NAME).build()

    @Provides
    fun provideAlarmDao(database: AppDatabase): AlarmDao = database.alarmDao()

    @Provides
    fun provideTrashDao(database: AppDatabase): TrashDao = database.trashDao()

    /**
     * Built on an explicit device-protected path: `preferencesDataStoreFile()` resolves against the
     * application context, which is always credential-protected and unreadable in direct boot.
     */
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext appContext: Context,
        @DeviceProtected context: Context,
    ): DataStore<Preferences> {
        val file = File(context.filesDir, "datastore/$SETTINGS_FILE")
        moveFromCredentialStorage(appContext, file)
        return PreferenceDataStoreFactory.create { file }
    }

    /** Versions up to 0.1 kept settings in credential storage; move them once the user has unlocked. */
    private fun moveFromCredentialStorage(appContext: Context, target: File) {
        if (target.exists() || !appContext.getSystemService(UserManager::class.java).isUserUnlocked) return
        runCatching {
            val legacy = File(appContext.filesDir, "datastore/$SETTINGS_FILE")
            if (legacy.exists()) {
                target.parentFile?.mkdirs()
                legacy.copyTo(target)
                legacy.delete()
            }
        }.onFailure { Log.w("DataModule", "Could not move settings to device-protected storage", it) }
    }

    private const val SETTINGS_FILE = "settings.preferences_pb"

    @Provides
    fun provideClock(): Clock = Clock.systemUTC()
}

@Module
@InstallIn(SingletonComponent::class)
interface DataBindingsModule {
    @Binds
    fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
