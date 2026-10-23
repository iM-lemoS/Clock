package io.github.imlemos.clock.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.imlemos.clock.alarm.data.AlarmController
import android.net.Uri
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.model.AlarmSound
import io.github.imlemos.clock.alarm.model.Vibration
import io.github.imlemos.clock.core.audio.AlarmSoundPlayer
import io.github.imlemos.clock.core.audio.RingtoneStore
import io.github.imlemos.clock.core.data.prefs.AudioOutput
import io.github.imlemos.clock.core.data.prefs.AppearanceSettings
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.core.data.prefs.ThemeMode
import io.github.imlemos.clock.core.data.prefs.TimeFormat
import io.github.imlemos.clock.core.data.prefs.VolumeButtonAction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import io.github.imlemos.clock.alarm.layout.AlarmLayout
import io.github.imlemos.clock.alarm.layout.LayoutRepository
import io.github.imlemos.clock.alarm.layout.PresetLayouts
import kotlinx.coroutines.launch
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val alarms: AlarmController,
    private val ringtones: RingtoneStore,
    private val player: AlarmSoundPlayer,
    private val clock: Clock,
    layouts: LayoutRepository,
) : ViewModel() {

    /** The layout alarms use unless they have their own. */
    val defaultLayout: StateFlow<AlarmLayout?> = combine(layouts.observeAll(), settings.defaultLayoutId) { all, id ->
        all.firstOrNull { it.id == id } ?: all.firstOrNull { it.id == PresetLayouts.DEFAULT }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val defaultSound: StateFlow<AlarmSound> = settings.defaultSound
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmSound())

    val audioOutput: StateFlow<AudioOutput> = settings.audioOutput
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioOutput.PHONE_AND_BLUETOOTH)

    val timerTone: StateFlow<AlarmSound> = settings.timerTone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmSound())

    fun setTimerSystemTone(uri: Uri?) {
        viewModelScope.launch { settings.setTimerTone(uri?.toString(), uri?.let(ringtones::systemTitle)) }
    }

    fun importTimerTone(uri: Uri, onFailed: () -> Unit) {
        viewModelScope.launch {
            val tone = ringtones.import(uri) ?: return@launch onFailed()
            settings.setTimerTone(tone.uri, tone.title)
        }
    }

    fun setDefaultSound(sound: AlarmSound) {
        viewModelScope.launch { settings.setDefaultSound(sound) }
    }

    fun setDefaultSystemTone(uri: Uri?) =
        setDefaultSound(defaultSound.value.copy(ringtoneUri = uri?.toString(), ringtoneTitle = uri?.let(ringtones::systemTitle)))

    fun importDefaultTone(uri: Uri, onFailed: () -> Unit) {
        viewModelScope.launch {
            val tone = ringtones.import(uri) ?: return@launch onFailed()
            settings.setDefaultSound(defaultSound.value.copy(ringtoneUri = tone.uri, ringtoneTitle = tone.title))
        }
    }

    fun previewVibration(vibration: Vibration) = player.previewVibration(vibration)

    val warnLowVolume: StateFlow<Boolean> = settings.warnLowVolume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setWarnLowVolume(enabled: Boolean) {
        viewModelScope.launch { settings.setWarnLowVolume(enabled) }
    }

    val timeFormat: StateFlow<TimeFormat> = settings.timeFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimeFormat.SYSTEM)

    val manualZone: StateFlow<ZoneId?> = settings.manualZone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setManualZone(zone: ZoneId?) {
        viewModelScope.launch { settings.setManualZone(zone) }
    }

    fun setTimeFormat(format: TimeFormat) {
        viewModelScope.launch { settings.setTimeFormat(format) }
    }

    fun setAudioOutput(output: AudioOutput) {
        viewModelScope.launch { settings.setAudioOutput(output) }
    }

    val appearance: StateFlow<AppearanceSettings> = settings.appearance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceSettings())

    val volumeButtonAction: StateFlow<VolumeButtonAction> = settings.volumeButtonAction
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VolumeButtonAction.OFF)

    val noticeMinutes: StateFlow<Set<Int>> = settings.noticeMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleNotice(minutes: Int) {
        val current = noticeMinutes.value
        viewModelScope.launch {
            settings.setNoticeMinutes(if (minutes in current) current - minutes else current + minutes)
            // Notices are scheduled together with each alarm.
            alarms.rescheduleAll()
        }
    }

    fun setVolumeButtonAction(action: VolumeButtonAction) {
        viewModelScope.launch { settings.setVolumeButtonAction(action) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setAccent(argb: Int?) {
        viewModelScope.launch { settings.setAccent(argb) }
    }

    fun setReduceTransparency(enabled: Boolean) {
        viewModelScope.launch { settings.setReduceTransparency(enabled) }
    }

    /** Debug builds: a one-off alarm about a minute from now, to test ringing end to end. */
    fun createTestAlarm(label: String) {
        viewModelScope.launch {
            val now = clock.instant().atZone(ZoneId.systemDefault())
            val at = now.plusMinutes(if (now.second < 40) 1 else 2)
            alarms.save(Alarm(time = TimeOfDay(at.hour, at.minute), label = label, createdAt = clock.instant()))
        }
    }
}
