package io.github.imlemos.clock.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room3.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.imlemos.clock.core.data.db.AlarmDao
import io.github.imlemos.clock.core.data.db.AppDatabase
import io.github.imlemos.clock.core.data.prefs.DataStoreSettingsRepository
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
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
    @Singleton
    fun providePreferencesDataStore(@DeviceProtected context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    fun provideClock(): Clock = Clock.systemUTC()
}

@Module
@InstallIn(SingletonComponent::class)
interface DataBindingsModule {
    @Binds
    fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
