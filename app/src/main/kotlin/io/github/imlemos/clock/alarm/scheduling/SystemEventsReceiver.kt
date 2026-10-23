package io.github.imlemos.clock.alarm.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import io.github.imlemos.clock.alarm.data.AlarmController
import io.github.imlemos.clock.stopwatch.StopwatchController
import io.github.imlemos.clock.timer.data.TimerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-registers all alarms whenever the system may have dropped or shifted them: boot (including
 * direct boot, before the first unlock), app update, manual time or time zone change, and the
 * exact-alarm permission being granted again (Android 12).
 */
@AndroidEntryPoint
class SystemEventsReceiver : BroadcastReceiver() {

    @Inject lateinit var controller: AlarmController
    @Inject lateinit var timers: TimerController
    @Inject lateinit var stopwatch: StopwatchController

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Rescheduling alarms after ${intent.action}")
        val pending = goAsync()
        scope.launch {
            try {
                // After boot or a manual clock change, report what should have rung meanwhile.
                if (intent.action in MISSED_CHECK_ACTIONS) controller.checkMissed()
                controller.rescheduleAll()
                // Timers and the stopwatch run on elapsed time, which restarts at boot.
                val boot = intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED || intent.action == Intent.ACTION_BOOT_COMPLETED
                timers.resync(afterBoot = boot)
                stopwatch.resync(afterBoot = boot)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SystemEventsReceiver"
        val MISSED_CHECK_ACTIONS = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
