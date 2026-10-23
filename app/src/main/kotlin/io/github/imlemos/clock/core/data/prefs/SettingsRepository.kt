package io.github.imlemos.clock.core.data.prefs

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val appearance: Flow<AppearanceSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    /** Sets the accent color as ARGB; `null` restores the app default. */
    suspend fun setAccent(argb: Int?)

    suspend fun setReduceTransparency(enabled: Boolean)

    val volumeButtonAction: Flow<VolumeButtonAction>

    suspend fun setVolumeButtonAction(action: VolumeButtonAction)

    /** Whether "Set up reliability" was already shown on first launch. */
    val reliabilityIntroShown: Flow<Boolean>

    suspend fun setReliabilityIntroShown()
}
