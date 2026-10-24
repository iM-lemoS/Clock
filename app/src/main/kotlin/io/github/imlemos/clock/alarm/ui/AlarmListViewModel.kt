package io.github.imlemos.clock.alarm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.imlemos.clock.alarm.data.AlarmController
import io.github.imlemos.clock.alarm.data.AlarmRepository
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.model.AlarmGroup
import io.github.imlemos.clock.alarm.scheduling.AlarmScheduler
import io.github.imlemos.clock.core.audio.AlarmVolume
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.core.scheduling.AlarmCalculator
import io.github.imlemos.clock.core.scheduling.Recurrence
import io.github.imlemos.clock.reliability.ReliabilityChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * An alarm with its next ring time, as shown in the list.
 *
 * @property nextRing `null` when it will not ring (off, or its group is off).
 * @property groupOff The alarm is on but its group's master switch is off.
 * @property canSkip Recurring alarms can skip their next occurrence (docs/PLANO.md 3.5).
 * @property zone The zone to name on the card: the alarm's own, or the app's manual zone when its
 *   time differs from the phone's (docs/PLANO.md 3.6).
 * @property hereTime The same moment on the phone's clock, when it differs from the alarm's time.
 */
data class AlarmListItem(
    val alarm: Alarm,
    val nextRing: ZonedDateTime?,
    val groupOff: Boolean = false,
    val canSkip: Boolean = false,
    val skipped: Boolean = false,
    val zone: ZoneId? = null,
    val hereTime: ZonedDateTime? = null,
)

/** One row of the main list: a loose alarm or a group (docs/PLANO.md 3.8). */
sealed interface ListEntry {
    val key: String

    data class Single(val item: AlarmListItem) : ListEntry {
        override val key get() = "alarm-${item.alarm.id}"
    }

    data class Group(
        val group: AlarmGroup,
        val items: List<AlarmListItem>,
        /** The group's next alarm, shown in full on its card. */
        val next: AlarmListItem?,
    ) : ListEntry {
        override val key get() = "group-${group.id}"
    }
}

data class AlarmListState(
    val entries: List<ListEntry> = emptyList(),
    val groups: List<AlarmGroup> = emptyList(),
    val now: ZonedDateTime? = null,
    val selection: Set<Long> = emptySet(),
) {
    val isEmpty: Boolean get() = entries.isEmpty()
    val selecting: Boolean get() = selection.isNotEmpty()
    val anyActive: Boolean
        get() = entries.any { entry ->
            when (entry) {
                is ListEntry.Single -> entry.item.nextRing != null
                is ListEntry.Group -> entry.items.any { it.nextRing != null }
            }
        }
}

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    repository: AlarmRepository,
    private val controller: AlarmController,
    private val scheduler: AlarmScheduler,
    private val clock: Clock,
    private val reliability: ReliabilityChecker,
    private val alarmVolume: AlarmVolume,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _reliabilityOk = MutableStateFlow(true)

    /** False when a system setting may stop alarms from ringing; re-checked on every resume. */
    val reliabilityOk: StateFlow<Boolean> = _reliabilityOk.asStateFlow()

    private val _volumeLevel = MutableStateFlow(AlarmVolume.Level.OK)

    /** Zero always warns; the minimum only when the user keeps the low-volume warning on. */
    val volumeWarning: StateFlow<AlarmVolume.Level> = combine(_volumeLevel, settings.warnLowVolume) { level, warnLow ->
        if (level == AlarmVolume.Level.LOW && !warnLow) AlarmVolume.Level.OK else level
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmVolume.Level.OK)

    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    /** Ticks at each minute boundary so "rings in ..." stays current. */
    private val minutes = flow {
        while (true) {
            emit(clock.instant())
            delay(60_000L - clock.millis() % 60_000L)
        }
    }

    val state: StateFlow<AlarmListState> =
        combine(
            repository.observeAll(),
            repository.observeGroups(),
            minutes,
            selection,
            settings.manualZone,
        ) { alarms, groups, now, selected, manualZone ->
            val appZone = AlarmCalculator.effectiveZone(null, manualZone, scheduler.systemZone())
            buildState(alarms, groups, now, selected.filterTo(mutableSetOf()) { id -> alarms.any { it.id == id } }, appZone)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmListState())

    fun refreshReliability() {
        _reliabilityOk.value = reliability.allOk()
    }

    fun refreshVolume() {
        _volumeLevel.value = alarmVolume.level()
    }

    fun raiseVolume() {
        alarmVolume.raise()
        refreshVolume()
    }

    fun setEnabled(id: Long, enabled: Boolean) = launch { controller.setEnabled(id, enabled) }

    fun setSkipNext(id: Long, skip: Boolean) = launch { controller.setSkipNext(id, skip) }

    // --- Groups ---

    fun setGroupEnabled(id: Long, enabled: Boolean) = launch { controller.setGroupEnabled(id, enabled) }

    fun toggleExpanded(group: AlarmGroup) = launch { controller.setGroupExpanded(group.id, !group.expanded) }

    fun renameGroup(id: Long, name: String) = launch { if (name.isNotBlank()) controller.renameGroup(id, name) }

    fun deleteGroup(id: Long, withAlarms: Boolean) = launch { controller.deleteGroup(id, withAlarms) }

    // --- Selection (docs/PLANO.md 3.9) ---

    fun toggleSelection(id: Long) = selection.update { if (id in it) it - id else it + id }

    fun clearSelection() = selection.update { emptySet() }

    fun enableSelected(enabled: Boolean) = withSelection { controller.setEnabled(it, enabled) }

    fun deleteSelected() = withSelection { controller.delete(it) }

    /** Copies the selection (switched off); [onDone] gets how many copies were made. */
    fun duplicateSelected(onDone: (Int) -> Unit) = withSelection { onDone(controller.duplicate(it)) }

    /** Moves the selection to [groupId] (`null` = no group). */
    fun moveSelected(groupId: Long?) = withSelection { controller.moveToGroup(it, groupId) }

    fun moveSelectedToNewGroup(name: String) = withSelection { ids ->
        if (name.isNotBlank()) controller.moveToGroup(ids, controller.createGroup(name).id)
    }

    /** Ids for the batch editor; the selection ends there. */
    fun takeSelection(): List<Long> = selection.value.toList().also { clearSelection() }

    private fun withSelection(action: suspend (Set<Long>) -> Unit) {
        val ids = selection.value
        clearSelection()
        launch { action(ids) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun buildState(
        alarms: List<Alarm>,
        groups: List<AlarmGroup>,
        now: Instant,
        selected: Set<Long>,
        appZone: ZoneId,
    ): AlarmListState {
        val here = scheduler.systemZone()
        val groupsById = groups.associateBy { it.id }
        val items = alarms.map { alarm ->
            val groupEnabled = alarm.groupId?.let { groupsById[it]?.enabled } ?: true
            val effective = alarm.copy(enabled = alarm.enabled && groupEnabled)
            val zone = alarm.zone ?: appZone
            val next = scheduler.nextTrigger(effective, appZone)?.at
            // Off alarms still show their zone, compared at the time they would ring.
            val reference = next ?: wouldRing(alarm, appZone).takeIf { it != Instant.MAX }
            val showHere = reference != null && AlarmCalculator.showsLocalTime(reference, zone, here)
            AlarmListItem(
                alarm = alarm,
                nextRing = next?.atZone(zone),
                groupOff = alarm.enabled && !groupEnabled,
                canSkip = effective.enabled && alarm.recurrence !is Recurrence.Once,
                skipped = alarm.skippedOccurrence?.let { it > now } == true,
                zone = zone.takeIf { alarm.zone != null || showHere },
                hereTime = reference?.takeIf { showHere }?.atZone(here),
            )
        }
        val byGroup = items.groupBy { it.alarm.groupId?.takeIf(groupsById::containsKey) }
        val singles = byGroup[null].orEmpty().map { ListEntry.Single(it) }
        val groupEntries = groups.map { group ->
            val members = sortItems(byGroup[group.id].orEmpty(), appZone)
            ListEntry.Group(group, members, members.firstOrNull { it.nextRing != null })
        }
        return AlarmListState(
            entries = sortEntries(singles + groupEntries, appZone),
            groups = groups,
            now = now.atZone(appZone),
            selection = selected,
        )
    }

    /**
     * Next to ring first; groups by their next alarm; entries that will not ring at the bottom,
     * ordered by when they would ring if switched on (docs/PLANO.md 3.8).
     */
    private fun sortEntries(entries: List<ListEntry>, appZone: ZoneId): List<ListEntry> =
        entries.sortedWith(compareBy<ListEntry>({ nextOf(it) == null }, { nextOf(it) ?: wouldRingOf(it, appZone) }))

    private fun sortItems(items: List<AlarmListItem>, appZone: ZoneId): List<AlarmListItem> =
        items.sortedWith(compareBy<AlarmListItem>({ it.nextRing == null }, { it.nextRing?.toInstant() ?: wouldRing(it.alarm, appZone) }))

    private fun nextOf(entry: ListEntry): Instant? = when (entry) {
        is ListEntry.Single -> entry.item.nextRing?.toInstant()
        is ListEntry.Group -> entry.next?.nextRing?.toInstant()
    }

    private fun wouldRingOf(entry: ListEntry, appZone: ZoneId): Instant = when (entry) {
        is ListEntry.Single -> wouldRing(entry.item.alarm, appZone)
        is ListEntry.Group -> entry.items.minOfOrNull { wouldRing(it.alarm, appZone) } ?: Instant.MAX
    }

    private fun wouldRing(alarm: Alarm, appZone: ZoneId): Instant =
        scheduler.nextTrigger(alarm.copy(enabled = true), appZone)?.at ?: Instant.MAX
}
