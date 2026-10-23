package io.github.imlemos.clock.core.oem

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object OemModule {
    // Vendor detection arrives in phase 1; until then every device gets the generic profile.
    @Provides
    fun provideOemProfile(): OemProfile = GenericOemProfile
}
