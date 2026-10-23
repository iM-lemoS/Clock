package io.github.imlemos.clock.alarm.ringing

import io.github.imlemos.clock.core.scheduling.TimeOfDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import io.github.imlemos.clock.alarm.layout.LayoutSpec
import javax.inject.Inject
import javax.inject.Singleton

data class RingingAlarm(
    val id: Long,
    val time: TimeOfDay,
    val label: String,
    val snoozeMinutes: Int,
    val createdAt: Instant,
    /** The alarm's zone when its time is not the phone's, shown next to it (docs/PLANO.md 3.3). */
    val zone: ZoneId? = null,
    /** The alarm's screen layout, resolved when it fires (docs/PLANO.md 3.7). */
    val layout: LayoutSpec = LayoutSpec(),
)

/**
 * Alarms currently ringing together. Collisions share one screen, and the alarm created first
 * leads: its sound and layout are used (docs/PLANO.md 3.11). [alarms] is kept in that order.
 */
data class RingingSession(val alarms: List<RingingAlarm>, val silenced: Boolean = false) {
    val primary: RingingAlarm get() = alarms.first()
}

/** Shared between the ringing service (writer) and the alarm screen (reader). */
@Singleton
class RingingState @Inject constructor() {
    private val _session = MutableStateFlow<RingingSession?>(null)
    val session: StateFlow<RingingSession?> = _session.asStateFlow()

    fun add(alarm: RingingAlarm) {
        val current = _session.value?.alarms.orEmpty().filterNot { it.id == alarm.id }
        _session.value = RingingSession(
            alarms = (current + alarm).sortedWith(compareBy({ it.createdAt }, { it.id })),
            silenced = _session.value?.silenced ?: false,
        )
    }

    fun markSilenced() {
        _session.value = _session.value?.copy(silenced = true)
    }

    fun clear() {
        _session.value = null
    }
}
