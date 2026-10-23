package io.github.imlemos.clock.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import io.github.imlemos.clock.alarm.data.AlarmController
import io.github.imlemos.clock.alarm.data.AlarmRepository
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * adb hooks for on-device testing (see CLAUDE.md):
 *
 * ```
 * adb shell am broadcast -a io.github.imlemos.clock.debug.TEST_ALARM \
 *     -n io.github.imlemos.clock.debug/io.github.imlemos.clock.debug.DebugCommandsReceiver \
 *     --ei in_minutes 2 --es label "Doze test"
 * adb shell am broadcast -a io.github.imlemos.clock.debug.DELETE_ALL_ALARMS -n <same component>
 * ```
 */
@AndroidEntryPoint
class DebugCommandsReceiver : BroadcastReceiver() {

    @Inject lateinit var controller: AlarmController
    @Inject lateinit var repository: AlarmRepository
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_TEST_ALARM -> createTestAlarm(
                        minutes = intent.getIntExtra("in_minutes", 1).coerceAtLeast(1),
                        label = intent.getStringExtra("label").orEmpty(),
                    )
                    ACTION_DELETE_ALL -> repository.getAll().forEach { controller.delete(it.id) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** A one-off alarm at the start of the minute [minutes] from now. */
    private suspend fun createTestAlarm(minutes: Int, label: String) {
        val at = clock.instant().atZone(ZoneId.systemDefault()).plusMinutes(minutes.toLong()).truncatedTo(ChronoUnit.MINUTES)
        val saved = controller.save(Alarm(time = TimeOfDay(at.hour, at.minute), label = label, createdAt = clock.instant()))
        Log.i(TAG, "Test alarm ${saved.id} '$label' at $at")
    }

    private companion object {
        const val TAG = "DebugCommands"
        const val ACTION_TEST_ALARM = "io.github.imlemos.clock.debug.TEST_ALARM"
        const val ACTION_DELETE_ALL = "io.github.imlemos.clock.debug.DELETE_ALL_ALARMS"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
