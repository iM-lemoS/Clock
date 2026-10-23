package io.github.imlemos.clock.alarm.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import io.github.imlemos.clock.alarm.data.AlarmController
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

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Rescheduling alarms after ${intent.action}")
        val pending = goAsync()
        scope.launch {
            try {
                controller.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SystemEventsReceiver"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
