package io.github.imlemos.clock.alarm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.imlemos.clock.alarm.data.AlarmController
import io.github.imlemos.clock.alarm.data.AlarmRepository
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.scheduling.AlarmScheduler
import io.github.imlemos.clock.core.scheduling.Recurrence
import kotlinx.coroutines.delay
import io.github.imlemos.clock.reliability.ReliabilityChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * An alarm with its next ring time, as shown in the list.
 *
 * @property canSkip Recurring alarms can skip their next occurrence (docs/PLANO.md 3.5).
 * @property skipped The next occurrence is currently skipped.
 */
data class AlarmListItem(
    val alarm: Alarm,
    val nextRing: ZonedDateTime?,
    val canSkip: Boolean = false,
    val skipped: Boolean = false,
)

data class AlarmListState(val items: List<AlarmListItem> = emptyList(), val now: ZonedDateTime? = null)

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    repository: AlarmRepository,
    private val controller: AlarmController,
    private val scheduler: AlarmScheduler,
    private val clock: Clock,
    private val reliability: ReliabilityChecker,
) : ViewModel() {

    private val _reliabilityOk = MutableStateFlow(true)

    /** False when a system setting may stop alarms from ringing; re-checked on every resume. */
    val reliabilityOk: StateFlow<Boolean> = _reliabilityOk.asStateFlow()

    fun refreshReliability() {
        _reliabilityOk.value = reliability.allOk()
    }

    /** Ticks at each minute boundary so "rings in ..." stays current. */
    private val minutes = flow {
        while (true) {
            emit(clock.instant())
            delay(60_000L - clock.millis() % 60_000L)
        }
    }

    val state: StateFlow<AlarmListState> = combine(repository.observeAll(), minutes) { alarms, now ->
        val zone = scheduler.appZone()
        val items = alarms.map { alarm ->
            AlarmListItem(
                alarm = alarm,
                nextRing = scheduler.nextTrigger(alarm)?.at?.atZone(zone),
                canSkip = alarm.enabled && alarm.recurrence !is Recurrence.Once,
                skipped = alarm.skippedOccurrence?.let { it > now } == true,
            )
        }
        AlarmListState(sort(items), now.atZone(zone))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmListState())

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { controller.setEnabled(id, enabled) }
    }

    fun setSkipNext(id: Long, skip: Boolean) {
        viewModelScope.launch { controller.setSkipNext(id, skip) }
    }

    /**
     * Next to ring first; disabled alarms at the bottom, ordered by when they would ring if enabled
     * (docs/PLANO.md 3.8).
     */
    private fun sort(items: List<AlarmListItem>): List<AlarmListItem> {
        val (active, inactive) = items.partition { it.nextRing != null }
        val inactiveOrder = inactive.sortedBy { scheduler.nextTrigger(it.alarm.copy(enabled = true))?.at }
        return active.sortedBy { it.nextRing } + inactiveOrder
    }
}
