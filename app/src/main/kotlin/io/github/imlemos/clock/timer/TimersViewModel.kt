package io.github.imlemos.clock.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.imlemos.clock.timer.ringing.TimerRingingService
import io.github.imlemos.clock.timer.data.Timer
import io.github.imlemos.clock.timer.data.TimerController
import io.github.imlemos.clock.timer.data.TimerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TimersViewModel @Inject constructor(
    repository: TimerRepository,
    private val controller: TimerController,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val timers: StateFlow<List<Timer>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun start(id: Long) = launch { controller.start(id) }

    fun pause(id: Long) = launch { controller.pause(id) }

    fun reset(id: Long) = launch { controller.reset(id) }

    fun addMinute(id: Long) = launch { controller.addMinute(id) }

    fun delete(id: Long) = launch { controller.delete(id) }

    /** Stops a ringing timer (through the ringing service, which owns the sound). */
    fun stop(id: Long) = TimerRingingService.stop(context, id)

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
