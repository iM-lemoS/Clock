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

    /** Minutes before an alarm at which the upcoming notice appears (docs/PLANO.md 3.4). */
    val noticeMinutes: Flow<Set<Int>>

    suspend fun setNoticeMinutes(minutes: Set<Int>)

    /**
     * Trash ids offered for "Undo" in the app. Cleared when the app goes to the background after
     * showing them, so the pop-up disappears if the app is closed and reopened untouched.
     */
    val pendingUndo: Flow<List<Long>>

    suspend fun addPendingUndo(trashId: Long)

    suspend fun removePendingUndo(trashId: Long)

    suspend fun clearPendingUndo()

    /** Whether "Set up reliability" was already shown on first launch. */
    val reliabilityIntroShown: Flow<Boolean>

    suspend fun setReliabilityIntroShown()
}
