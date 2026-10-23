package io.github.imlemos.clock.alarm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.imlemos.clock.alarm.data.AlarmController
import io.github.imlemos.clock.alarm.data.AlarmRepository
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.scheduling.AlarmScheduler
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZonedDateTime

data class AlarmEditorState(
    val isNew: Boolean,
    val loaded: Boolean = false,
    val time: TimeOfDay = TimeOfDay(7, 0),
    val label: String = "",
)

/** Result of saving, used for the "rings in ..." confirmation. */
data class SavedAlarm(val nextRing: ZonedDateTime?, val now: ZonedDateTime)

@HiltViewModel(assistedFactory = AlarmEditorViewModel.Factory::class)
class AlarmEditorViewModel @AssistedInject constructor(
    @Assisted private val alarmId: Long,
    private val repository: AlarmRepository,
    private val controller: AlarmController,
    private val scheduler: AlarmScheduler,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(alarmId: Long): AlarmEditorViewModel
    }

    private var original: Alarm? = null
    private val _state = MutableStateFlow(AlarmEditorState(isNew = alarmId == NEW_ALARM))
    val state: StateFlow<AlarmEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val alarm = if (alarmId == NEW_ALARM) null else repository.get(alarmId)
            original = alarm
            _state.update {
                if (alarm == null) {
                    it.copy(loaded = true, time = defaultTimeForNewAlarm())
                } else {
                    it.copy(loaded = true, time = alarm.time, label = alarm.label)
                }
            }
        }
    }

    fun setTime(time: TimeOfDay) = _state.update { it.copy(time = time) }

    fun setLabel(label: String) = _state.update { it.copy(label = label.take(MAX_LABEL_LENGTH)) }

    fun save(onSaved: (SavedAlarm) -> Unit) {
        val current = _state.value
        viewModelScope.launch {
            val base = original ?: Alarm(time = current.time, createdAt = clock.instant())
            val saved = controller.save(base.copy(time = current.time, label = current.label.trim(), enabled = true))
            val zone = scheduler.appZone()
            onSaved(SavedAlarm(scheduler.nextTrigger(saved)?.at?.atZone(zone), clock.instant().atZone(zone)))
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (alarmId != NEW_ALARM) controller.delete(alarmId)
            onDeleted()
        }
    }

    /** The next full hour, a sensible starting point for a new alarm. */
    private fun defaultTimeForNewAlarm(): TimeOfDay {
        val now = clock.instant().atZone(scheduler.appZone())
        return TimeOfDay((now.hour + 1) % 24, 0)
    }

    companion object {
        const val NEW_ALARM = 0L
        const val MAX_LABEL_LENGTH = 60
    }
}
