package io.github.imlemos.clock.core.oem

import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object OemModule {
    @Provides
    fun provideOemProfile(): OemProfile = OemProfiles.forDevice(Build.MANUFACTURER, Build.BRAND)
}
