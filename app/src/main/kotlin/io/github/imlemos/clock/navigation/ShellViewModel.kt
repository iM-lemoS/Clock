package io.github.imlemos.clock.navigation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.imlemos.clock.alarm.data.AlarmController
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.trash.TrashRepository
import io.github.imlemos.clock.trash.TrashedItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State shared by the whole shell: the "Undo" pop-up for recently deleted alarms. */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val trash: TrashRepository,
    private val controller: AlarmController,
) : ViewModel() {

    val pendingUndo: StateFlow<List<TrashedItem>> = settings.pendingUndo
        .map { ids -> ids.mapNotNull { trash.get(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Set once the pop-up was on screen while the app was in the foreground. */
    private var undoSeen = false

    fun onUndoVisibleWhileResumed() {
        undoSeen = true
    }

    /** The app went to the background: forget the offer only if the user actually saw it. */
    fun onAppStopped() {
        if (undoSeen && pendingUndo.value.isNotEmpty()) forgetPendingUndo()
        undoSeen = false
    }

    /** The "x" on the pop-up: drop the offer now (items stay in the trash). */
    fun dismissUndo() {
        viewModelScope.launch { settings.clearPendingUndo() }
    }

    fun undo() {
        viewModelScope.launch { pendingUndo.value.forEach { controller.restore(it.trashId) } }
    }

    /** The pop-up was seen and the app left: it must not come back on the next launch. */
    private fun forgetPendingUndo() {
        Log.i("ShellViewModel", "Undo offer seen and app left; forgetting ${pendingUndo.value.size} item(s)")
        viewModelScope.launch { settings.clearPendingUndo() }
    }
}
