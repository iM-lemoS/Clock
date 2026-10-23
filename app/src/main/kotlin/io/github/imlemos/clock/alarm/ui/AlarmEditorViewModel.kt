package io.github.imlemos.clock.alarm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.imlemos.clock.alarm.data.AlarmController
import io.github.imlemos.clock.alarm.data.AlarmRepository
import android.net.Uri
import io.github.imlemos.clock.alarm.layout.AlarmLayout
import io.github.imlemos.clock.alarm.layout.LayoutRepository
import io.github.imlemos.clock.alarm.layout.PresetLayouts
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.model.AlarmGroup
import io.github.imlemos.clock.alarm.model.AlarmSound
import io.github.imlemos.clock.alarm.model.Vibration
import io.github.imlemos.clock.core.audio.AlarmSoundPlayer
import io.github.imlemos.clock.core.audio.RingtoneStore
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first
import io.github.imlemos.clock.alarm.scheduling.AlarmScheduler
import io.github.imlemos.clock.core.scheduling.Recurrence
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime

enum class RecurrenceKind { ONCE, WEEKLY, DATES }

data class AlarmEditorState(
    val isNew: Boolean,
    val loaded: Boolean = false,
    val time: TimeOfDay = TimeOfDay(7, 0),
    val label: String = "",
    val kind: RecurrenceKind = RecurrenceKind.ONCE,
    val weekDays: Set<DayOfWeek> = emptySet(),
    val dates: Set<LocalDate> = emptySet(),
    val singleUse: Boolean = false,
    val sound: AlarmSound = AlarmSound(),
    val groupId: Long? = null,
    val groups: List<AlarmGroup> = emptyList(),
    /** The alarm's own zone; `null` follows the app (docs/PLANO.md 3.6). */
    val zone: ZoneId? = null,
    /** The app's manual zone, for the "Local time" summary. */
    val manualZone: ZoneId? = null,
    /** Alarm screen layout; `null` uses the default (docs/PLANO.md 3.7). */
    val layoutId: Long? = null,
    val layouts: List<AlarmLayout> = emptyList(),
    val defaultLayoutId: Long = PresetLayouts.DEFAULT,
    /** Set when saving would ring together with another alarm (docs/PLANO.md 3.11). */
    val collision: Collision? = null,
) {
    val recurrence: Recurrence?
        get() = when (kind) {
            RecurrenceKind.ONCE -> Recurrence.Once
            RecurrenceKind.WEEKLY -> weekDays.takeIf { it.isNotEmpty() }?.let(Recurrence::Weekly)
            RecurrenceKind.DATES -> dates.takeIf { it.isNotEmpty() }?.let(Recurrence::Dates)
        }

    /** A weekly alarm needs a day and a dates alarm needs a date before it can be saved. */
    val canSave: Boolean get() = loaded && recurrence != null
}

/** Another alarm that would ring at the same moment as the one being saved. */
data class Collision(val other: Alarm, val at: ZonedDateTime)

/** Result of saving, used for the "rings in ..." confirmation. */
data class SavedAlarm(val nextRing: ZonedDateTime?, val now: ZonedDateTime)

@HiltViewModel(assistedFactory = AlarmEditorViewModel.Factory::class)
class AlarmEditorViewModel @AssistedInject constructor(
    @Assisted private val alarmId: Long,
    private val repository: AlarmRepository,
    private val controller: AlarmController,
    private val scheduler: AlarmScheduler,
    private val settings: SettingsRepository,
    private val ringtones: RingtoneStore,
    private val player: AlarmSoundPlayer,
    private val clock: Clock,
    private val layoutRepository: LayoutRepository,
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
            repository.observeGroups().collect { groups -> _state.update { it.copy(groups = groups) } }
        }
        viewModelScope.launch {
            settings.manualZone.collect { zone -> _state.update { it.copy(manualZone = zone) } }
        }
        viewModelScope.launch {
            layoutRepository.observeAll().collect { all ->
                val defaultId = layoutRepository.defaultId()
                _state.update { it.copy(layouts = all, defaultLayoutId = defaultId) }
            }
        }
        viewModelScope.launch {
            val alarm = if (alarmId == NEW_ALARM) null else repository.get(alarmId)
            original = alarm
            _state.update {
                if (alarm == null) {
                    it.copy(loaded = true, time = defaultTimeForNewAlarm(), sound = settings.defaultSound.first())
                } else {
                    val recurrence = alarm.recurrence
                    it.copy(
                        loaded = true,
                        time = alarm.time,
                        label = alarm.label,
                        kind = when (recurrence) {
                            Recurrence.Once -> RecurrenceKind.ONCE
                            is Recurrence.Weekly -> RecurrenceKind.WEEKLY
                            is Recurrence.Dates -> RecurrenceKind.DATES
                        },
                        weekDays = (recurrence as? Recurrence.Weekly)?.days.orEmpty(),
                        dates = (recurrence as? Recurrence.Dates)?.dates.orEmpty(),
                        singleUse = alarm.singleUse,
                        sound = alarm.sound,
                        groupId = alarm.groupId,
                        zone = alarm.zone,
                        layoutId = alarm.layoutId,
                    )
                }
            }
        }
    }

    fun setTime(time: TimeOfDay) = _state.update { it.copy(time = time) }

    fun setLabel(label: String) = _state.update { it.copy(label = label.take(MAX_LABEL_LENGTH)) }

    fun setKind(kind: RecurrenceKind) = _state.update { it.copy(kind = kind) }

    fun toggleDay(day: DayOfWeek) = _state.update {
        it.copy(weekDays = if (day in it.weekDays) it.weekDays - day else it.weekDays + day)
    }

    fun toggleDate(date: LocalDate) = _state.update {
        it.copy(dates = if (date in it.dates) it.dates - date else it.dates + date)
    }

    fun setSingleUse(singleUse: Boolean) = _state.update { it.copy(singleUse = singleUse) }

    fun setSound(sound: AlarmSound) = _state.update { it.copy(sound = sound) }

    fun setSystemTone(uri: Uri?) = _state.update {
        it.copy(sound = it.sound.copy(ringtoneUri = uri?.toString(), ringtoneTitle = uri?.let(ringtones::systemTitle)))
    }

    /** Copies a picked file into the app; [onFailed] when the file cannot be used. */
    fun importTone(uri: Uri, onFailed: () -> Unit) {
        viewModelScope.launch {
            val tone = ringtones.import(uri) ?: return@launch onFailed()
            _state.update { it.copy(sound = it.sound.copy(ringtoneUri = tone.uri, ringtoneTitle = tone.title)) }
        }
    }

    fun previewVibration(vibration: Vibration) = player.previewVibration(vibration)

    fun setGroup(groupId: Long?) = _state.update { it.copy(groupId = groupId) }

    fun createGroup(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val group = controller.createGroup(name)
            _state.update { it.copy(groupId = group.id) }
        }
    }

    fun setZone(zone: ZoneId?) = _state.update { it.copy(zone = zone) }

    fun setLayout(layoutId: Long?) = _state.update { it.copy(layoutId = layoutId) }

    /** A new layout copied from the default, chosen for this alarm; [onCreated] opens its editor. */
    fun createLayout(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = layoutRepository.duplicate(layoutRepository.defaultId(), name)
            _state.update { it.copy(layoutId = id) }
            onCreated(id)
        }
    }

    /**
     * The next ring on the phone's clock when the alarm's zone shows a different time, e.g.
     * "13:00 here" under 08:00 New York. `null` otherwise.
     */
    fun hereTime(state: AlarmEditorState): ZonedDateTime? {
        val recurrence = state.recurrence ?: return null
        val draft = Alarm(time = state.time, recurrence = recurrence, zone = state.zone, createdAt = clock.instant())
        val at = scheduler.nextTrigger(draft)?.at ?: return null
        return at.takeIf { scheduler.displayZone(draft, it) != null }?.atZone(scheduler.systemZone())
    }

    fun dismissCollision() = _state.update { it.copy(collision = null) }

    /** First selectable date in the calendar: today, in the alarm's zone. */
    fun today(): LocalDate = clock.instant().atZone(_state.value.zone ?: scheduler.appZone()).toLocalDate()

    /**
     * Saves the alarm. Unless [force], first checks whether it would ring together with another
     * alarm and, if so, stops and exposes [AlarmEditorState.collision] so the user can decide.
     */
    fun save(force: Boolean = false, onSaved: (SavedAlarm) -> Unit) {
        val current = _state.value
        val recurrence = current.recurrence ?: return
        viewModelScope.launch {
            val base = original ?: Alarm(time = current.time, createdAt = clock.instant())
            val edited = base.copy(
                time = current.time,
                label = current.label.trim(),
                recurrence = recurrence,
                singleUse = current.singleUse,
                sound = current.sound,
                groupId = current.groupId,
                zone = current.zone,
                layoutId = current.layoutId,
                enabled = true,
            )
            val zone = scheduler.zoneOf(edited)
            if (!force) {
                controller.findCollision(edited)?.let { (other, at) ->
                    _state.update { it.copy(collision = Collision(other, at.atZone(zone))) }
                    return@launch
                }
            }
            _state.update { it.copy(collision = null) }
            val saved = controller.save(edited)
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
