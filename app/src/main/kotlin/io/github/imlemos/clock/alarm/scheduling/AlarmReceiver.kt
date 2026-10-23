package io.github.imlemos.clock.alarm.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.imlemos.clock.alarm.ringing.RingingService

/**
 * Receives AlarmManager's broadcast and immediately hands over to the foreground service. Alarm
 * clock broadcasts are allowed to start foreground services from the background.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmIntents.ACTION_FIRE) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, RingingService::class.java)
                .setAction(AlarmIntents.ACTION_FIRE)
                .putExtras(intent),
        )
    }
}
