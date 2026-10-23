package io.github.imlemos.clock.timer.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.imlemos.clock.timer.TimerReceiver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wakes the app when a timer ends. Uses `elapsedRealtime`, so changing the clock does not move
 * it, and an exact alarm allowed in Doze.
 */
@Singleton
class TimerScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(timerId: Long, endElapsed: Long) {
        val operation = TimerReceiver.pendingIntent(context, TimerReceiver.ACTION_FIRE, timerId)
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, operation)
        }
        Log.i(TAG, "Timer $timerId ends at elapsed $endElapsed")
    }

    fun cancel(timerId: Long) {
        alarmManager.cancel(TimerReceiver.pendingIntent(context, TimerReceiver.ACTION_FIRE, timerId))
    }

    private companion object {
        const val TAG = "TimerScheduler"
    }
}

/** A broadcast to [TimerReceiver] with an action and timer id, unique per pair. */
internal fun timerBroadcast(context: Context, action: String, timerId: Long): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        (action.hashCode() * 31 + timerId.toInt()),
        Intent(context, TimerReceiver::class.java).setAction(action).putExtra(TimerReceiver.EXTRA_TIMER_ID, timerId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
