package io.github.imlemos.clock.timer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.imlemos.clock.timer.data.TimerController
import io.github.imlemos.clock.timer.data.timerBroadcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A timer's end (from AlarmManager) and the actions of its ongoing notification. */
@AndroidEntryPoint
class TimerReceiver : BroadcastReceiver() {

    @Inject lateinit var controller: TimerController

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TIMER_ID, -1)
        if (id < 0) return
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> controller.onFired(id)
                    ACTION_PAUSE -> controller.pause(id)
                    ACTION_RESUME -> controller.start(id)
                    ACTION_RESET -> controller.reset(id)
                    ACTION_ADD_MINUTE -> controller.addMinute(id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TIMER_ID = "timer_id"
        const val ACTION_FIRE = "io.github.imlemos.clock.action.TIMER_FIRE"
        const val ACTION_PAUSE = "io.github.imlemos.clock.action.TIMER_PAUSE"
        const val ACTION_RESUME = "io.github.imlemos.clock.action.TIMER_RESUME"
        const val ACTION_RESET = "io.github.imlemos.clock.action.TIMER_RESET"
        const val ACTION_ADD_MINUTE = "io.github.imlemos.clock.action.TIMER_ADD_MINUTE"

        fun pendingIntent(context: Context, action: String, timerId: Long): PendingIntent = timerBroadcast(context, action, timerId)

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
