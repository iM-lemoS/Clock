package io.github.imlemos.clock.alarm.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.imlemos.clock.alarm.data.AlarmController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Upcoming-alarm notices: time to show or update one, or "Dismiss this one" was tapped. */
@AndroidEntryPoint
class NoticeReceiver : BroadcastReceiver() {

    @Inject lateinit var controller: AlarmController

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1)
        if (id < 0) return
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    AlarmIntents.ACTION_NOTICE -> controller.onNotice(id)
                    AlarmIntents.ACTION_DISMISS_EARLY -> controller.dismissEarly(id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
