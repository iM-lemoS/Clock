package io.github.imlemos.clock.alarm.ringing

import io.github.imlemos.clock.core.scheduling.TimeOfDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class RingingAlarm(val id: Long, val time: TimeOfDay, val label: String, val snoozeMinutes: Int)

/** Alarms currently ringing together (collisions share one screen, see docs/PLANO.md 3.11). */
data class RingingSession(val alarms: List<RingingAlarm>) {
    val primary: RingingAlarm get() = alarms.first()
}

/** Shared between the ringing service (writer) and the alarm screen (reader). */
@Singleton
class RingingState @Inject constructor() {
    private val _session = MutableStateFlow<RingingSession?>(null)
    val session: StateFlow<RingingSession?> = _session.asStateFlow()

    fun add(alarm: RingingAlarm) {
        val current = _session.value?.alarms.orEmpty().filterNot { it.id == alarm.id }
        _session.value = RingingSession(current + alarm)
    }

    fun clear() {
        _session.value = null
    }
}
