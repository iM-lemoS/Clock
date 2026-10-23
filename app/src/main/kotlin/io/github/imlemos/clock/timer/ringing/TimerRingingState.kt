package io.github.imlemos.clock.timer.ringing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** A timer that has reached zero and is ringing. */
data class RingingTimer(val id: Long, val name: String, val since: Instant)

/** Timers ringing now, shared by the ringing service and the full-screen activity. */
@Singleton
class TimerRingingState @Inject constructor() {
    private val _timers = MutableStateFlow<List<RingingTimer>>(emptyList())
    val timers: StateFlow<List<RingingTimer>> = _timers.asStateFlow()

    fun add(timer: RingingTimer) = _timers.update { list -> list.filterNot { it.id == timer.id } + timer }

    fun remove(id: Long) = _timers.update { list -> list.filterNot { it.id == id } }

    fun clear() {
        _timers.value = emptyList()
    }
}
