package io.github.imlemos.clock.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.imlemos.clock.alarm.data.AlarmController
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.core.data.prefs.AppearanceSettings
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.core.data.prefs.ThemeMode
import io.github.imlemos.clock.core.data.prefs.VolumeButtonAction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val alarms: AlarmController,
    private val clock: Clock,
) : ViewModel() {

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
