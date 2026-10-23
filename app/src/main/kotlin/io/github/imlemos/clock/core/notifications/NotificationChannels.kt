package io.github.imlemos.clock.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import io.github.imlemos.clock.R

/** Notification channels. Ids are persisted by the system: never rename them. */
object NotificationChannels {
    const val ALARM_RINGING = "alarm_ringing"
    const val ALARM_MISSED = "alarm_missed"
    const val ALARM_UPCOMING = "alarm_upcoming"
    const val ALARM_DELETED = "alarm_deleted"
    const val TIMERS = "timers"
    const val TIMER_RINGING = "timer_ringing"
    const val STOPWATCH = "stopwatch"

    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    ALARM_RINGING,
                    context.getString(R.string.channel_alarm_ringing),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_alarm_ringing_description)
                    // Sound and vibration are played by the ringing service on the alarm stream.
                    setSound(null, null)
                    enableVibration(false)
                    setBypassDnd(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(
                    ALARM_MISSED,
                    context.getString(R.string.channel_alarm_missed),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.channel_alarm_missed_description)
                },
                NotificationChannel(
                    ALARM_UPCOMING,
                    context.getString(R.string.channel_alarm_upcoming),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.channel_alarm_upcoming_description)
                },
                NotificationChannel(
                    TIMERS,
                    context.getString(R.string.channel_timers),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.channel_timers_description)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(
                    TIMER_RINGING,
                    context.getString(R.string.channel_timer_ringing),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_timer_ringing_description)
                    // Sound and vibration come from the ringing service, like alarms.
                    setSound(null, null)
                    enableVibration(false)
                    setBypassDnd(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(
                    STOPWATCH,
                    context.getString(R.string.channel_stopwatch),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.channel_stopwatch_description)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(
                    ALARM_DELETED,
                    context.getString(R.string.channel_alarm_deleted),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.channel_alarm_deleted_description)
                },
            ),
        )
    }
}
