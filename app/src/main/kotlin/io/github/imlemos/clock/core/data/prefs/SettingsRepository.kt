package io.github.imlemos.clock.core.data.prefs

import io.github.imlemos.clock.alarm.model.AlarmSound
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

interface SettingsRepository {
    val appearance: Flow<AppearanceSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    /** Sets the accent color as ARGB; `null` restores the app default. */
    suspend fun setAccent(argb: Int?)

    suspend fun setReduceTransparency(enabled: Boolean)

    /** Sound used for new alarms (docs/PLANO.md 6). Changing it does not alter existing alarms. */
    val defaultSound: Flow<AlarmSound>

    suspend fun setDefaultSound(sound: AlarmSound)

    val audioOutput: Flow<AudioOutput>

    suspend fun setAudioOutput(output: AudioOutput)

    /** Warn on the alarm list when the alarm volume is at its minimum (zero is always warned). */
    val warnLowVolume: Flow<Boolean>

    suspend fun setWarnLowVolume(enabled: Boolean)

    val timeFormat: Flow<TimeFormat>

    suspend fun setTimeFormat(format: TimeFormat)

    /**
     * The manual app time zone (docs/PLANO.md 3.6): alarms without their own zone follow it.
     * `null` is automatic, i.e. the phone's zone.
     */
    val manualZone: Flow<ZoneId?>

    suspend fun setManualZone(zone: ZoneId?)

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
