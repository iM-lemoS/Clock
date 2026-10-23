package io.github.imlemos.clock.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.model.AlarmSound
import io.github.imlemos.clock.alarm.model.GradualVolume
import io.github.imlemos.clock.alarm.model.Vibration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import android.util.Log
import java.time.ZoneId
import javax.inject.Inject

class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val appearance: Flow<AppearanceSettings> = dataStore.data
        .map { prefs ->
            AppearanceSettings(
                themeMode = prefs[Keys.THEME_MODE]
                    ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                    ?: ThemeMode.SYSTEM,
                accentArgb = prefs[Keys.ACCENT_ARGB],
                reduceTransparency = prefs[Keys.REDUCE_TRANSPARENCY] ?: false,
            )
        }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setAccent(argb: Int?) {
        dataStore.edit { prefs ->
            if (argb == null) prefs.remove(Keys.ACCENT_ARGB) else prefs[Keys.ACCENT_ARGB] = argb
        }
    }

    override suspend fun setReduceTransparency(enabled: Boolean) {
        dataStore.edit { it[Keys.REDUCE_TRANSPARENCY] = enabled }
    }

    override val defaultSound: Flow<AlarmSound> = dataStore.data
        .map { prefs ->
            AlarmSound(
                ringtoneUri = prefs[Keys.DEFAULT_RINGTONE_URI],
                ringtoneTitle = prefs[Keys.DEFAULT_RINGTONE_TITLE],
                vibration = prefs[Keys.DEFAULT_VIBRATION]
                    ?.let { name -> Vibration.entries.firstOrNull { it.name == name } }
                    ?: Vibration.SYNC,
                gradual = GradualVolume(
                    enabled = prefs[Keys.DEFAULT_GRADUAL_ENABLED] ?: false,
                    targetPercent = (prefs[Keys.DEFAULT_GRADUAL_TARGET] ?: 100).coerceIn(1, 100),
                    seconds = (prefs[Keys.DEFAULT_GRADUAL_SECONDS] ?: 60).coerceIn(1, 600),
                ),
                ringDurationSeconds = prefs[Keys.DEFAULT_RING_SECONDS] ?: Alarm.DEFAULT_RING_DURATION_SECONDS,
                snoozeMinutes = prefs[Keys.DEFAULT_SNOOZE_MINUTES] ?: Alarm.DEFAULT_SNOOZE_MINUTES,
            )
        }
        .distinctUntilChanged()

    override suspend fun setDefaultSound(sound: AlarmSound) {
        dataStore.edit { prefs ->
            if (sound.ringtoneUri == null) prefs.remove(Keys.DEFAULT_RINGTONE_URI) else prefs[Keys.DEFAULT_RINGTONE_URI] = sound.ringtoneUri
            if (sound.ringtoneTitle == null) prefs.remove(Keys.DEFAULT_RINGTONE_TITLE) else prefs[Keys.DEFAULT_RINGTONE_TITLE] = sound.ringtoneTitle
            prefs[Keys.DEFAULT_VIBRATION] = sound.vibration.name
            prefs[Keys.DEFAULT_GRADUAL_ENABLED] = sound.gradual.enabled
            prefs[Keys.DEFAULT_GRADUAL_TARGET] = sound.gradual.targetPercent
            prefs[Keys.DEFAULT_GRADUAL_SECONDS] = sound.gradual.seconds
            prefs[Keys.DEFAULT_RING_SECONDS] = sound.ringDurationSeconds
            prefs[Keys.DEFAULT_SNOOZE_MINUTES] = sound.snoozeMinutes
        }
    }

    override val audioOutput: Flow<AudioOutput> = dataStore.data
        .map { prefs ->
            prefs[Keys.AUDIO_OUTPUT]?.let { name -> AudioOutput.entries.firstOrNull { it.name == name } }
                ?: AudioOutput.PHONE_AND_BLUETOOTH
        }
        .distinctUntilChanged()

    override suspend fun setAudioOutput(output: AudioOutput) {
        dataStore.edit { it[Keys.AUDIO_OUTPUT] = output.name }
    }

    override val warnLowVolume: Flow<Boolean> = dataStore.data
        .map { it[Keys.WARN_LOW_VOLUME] ?: true }
        .distinctUntilChanged()

    override suspend fun setWarnLowVolume(enabled: Boolean) {
        dataStore.edit { it[Keys.WARN_LOW_VOLUME] = enabled }
    }

    override val timeFormat: Flow<TimeFormat> = dataStore.data
        .map { prefs -> prefs[Keys.TIME_FORMAT]?.let { name -> TimeFormat.entries.firstOrNull { it.name == name } } ?: TimeFormat.SYSTEM }
        .distinctUntilChanged()

    override suspend fun setTimeFormat(format: TimeFormat) {
        dataStore.edit { it[Keys.TIME_FORMAT] = format.name }
    }

    override val manualZone: Flow<ZoneId?> = dataStore.data
        .map { prefs -> prefs[Keys.MANUAL_ZONE]?.let { runCatching { ZoneId.of(it) }.getOrNull() } }
        .distinctUntilChanged()

    override suspend fun setManualZone(zone: ZoneId?) {
        dataStore.edit { prefs -> if (zone == null) prefs.remove(Keys.MANUAL_ZONE) else prefs[Keys.MANUAL_ZONE] = zone.id }
    }

    override val worldClockZones: Flow<List<ZoneId>> = dataStore.data
        .map { prefs ->
            prefs[Keys.WORLD_CLOCK_ZONES].orEmpty().split(',').filter(String::isNotBlank)
                .mapNotNull { runCatching { ZoneId.of(it) }.getOrNull() }
        }
        .distinctUntilChanged()

    override suspend fun setWorldClockZones(zones: List<ZoneId>) {
        dataStore.edit { it[Keys.WORLD_CLOCK_ZONES] = zones.distinct().joinToString(",") { zone -> zone.id } }
    }

    override val defaultLayoutId: Flow<Long?> = dataStore.data
        .map { it[Keys.DEFAULT_LAYOUT_ID] }
        .distinctUntilChanged()

    override suspend fun setDefaultLayoutId(id: Long?) {
        dataStore.edit { prefs -> if (id == null) prefs.remove(Keys.DEFAULT_LAYOUT_ID) else prefs[Keys.DEFAULT_LAYOUT_ID] = id }
    }

    override val volumeButtonAction: Flow<VolumeButtonAction> = dataStore.data
        .map { prefs ->
            prefs[Keys.VOLUME_BUTTON_ACTION]
                ?.let { name -> VolumeButtonAction.entries.firstOrNull { it.name == name } }
                ?: VolumeButtonAction.OFF
        }
        .distinctUntilChanged()

    override suspend fun setVolumeButtonAction(action: VolumeButtonAction) {
        dataStore.edit { it[Keys.VOLUME_BUTTON_ACTION] = action.name }
    }

    override val noticeMinutes: Flow<Set<Int>> = dataStore.data
        .map { prefs -> prefs[Keys.NOTICE_MINUTES]?.toIntSet() ?: DEFAULT_NOTICE_MINUTES }
        .distinctUntilChanged()

    override suspend fun setNoticeMinutes(minutes: Set<Int>) {
        dataStore.edit { it[Keys.NOTICE_MINUTES] = minutes.sorted().joinToString(",") }
    }

    override val pendingUndo: Flow<List<Long>> = dataStore.data
        .map { prefs -> prefs[Keys.PENDING_UNDO].orEmpty().split(',').mapNotNull(String::toLongOrNull) }
        .distinctUntilChanged()

    override suspend fun addPendingUndo(trashId: Long) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PENDING_UNDO].orEmpty().split(',').filter(String::isNotBlank)
            prefs[Keys.PENDING_UNDO] = (current + trashId.toString()).joinToString(",")
        }
        Log.i(TAG, "Pending undo added: $trashId")
    }

    override suspend fun removePendingUndo(trashId: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.PENDING_UNDO] = prefs[Keys.PENDING_UNDO].orEmpty().split(',')
                .filter { it.isNotBlank() && it != trashId.toString() }
                .joinToString(",")
        }
    }

    override suspend fun clearPendingUndo() {
        Log.i(TAG, "Pending undo cleared")
        dataStore.edit { it.remove(Keys.PENDING_UNDO) }
    }

    override val reliabilityIntroShown: Flow<Boolean> = dataStore.data
        .map { it[Keys.RELIABILITY_INTRO_SHOWN] ?: false }
        .distinctUntilChanged()

    override suspend fun setReliabilityIntroShown() {
        dataStore.edit { it[Keys.RELIABILITY_INTRO_SHOWN] = true }
    }

    // Stored names are persisted on users' devices: never rename them.
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_ARGB = intPreferencesKey("accent_argb")
        val REDUCE_TRANSPARENCY = booleanPreferencesKey("reduce_transparency")
        val RELIABILITY_INTRO_SHOWN = booleanPreferencesKey("reliability_intro_shown")
        val VOLUME_BUTTON_ACTION = stringPreferencesKey("volume_button_action")
        val NOTICE_MINUTES = stringPreferencesKey("notice_minutes")
        val PENDING_UNDO = stringPreferencesKey("pending_undo")
        val DEFAULT_RINGTONE_URI = stringPreferencesKey("default_ringtone_uri")
        val DEFAULT_RINGTONE_TITLE = stringPreferencesKey("default_ringtone_title")
        val DEFAULT_VIBRATION = stringPreferencesKey("default_vibration")
        val DEFAULT_GRADUAL_ENABLED = booleanPreferencesKey("default_gradual_enabled")
        val DEFAULT_GRADUAL_TARGET = intPreferencesKey("default_gradual_target")
        val DEFAULT_GRADUAL_SECONDS = intPreferencesKey("default_gradual_seconds")
        val DEFAULT_RING_SECONDS = intPreferencesKey("default_ring_seconds")
        val DEFAULT_SNOOZE_MINUTES = intPreferencesKey("default_snooze_minutes")
        val AUDIO_OUTPUT = stringPreferencesKey("audio_output")
        val WARN_LOW_VOLUME = booleanPreferencesKey("warn_low_volume")
        val TIME_FORMAT = stringPreferencesKey("time_format")
        val MANUAL_ZONE = stringPreferencesKey("manual_zone")
        val DEFAULT_LAYOUT_ID = longPreferencesKey("default_layout_id")
        val WORLD_CLOCK_ZONES = stringPreferencesKey("world_clock_zones")
    }

    private fun String.toIntSet(): Set<Int> = split(',').mapNotNull(String::toIntOrNull).toSet()

    companion object {
        val DEFAULT_NOTICE_MINUTES = setOf(30)
        private const val TAG = "Settings"
    }
}
