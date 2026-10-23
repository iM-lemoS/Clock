package io.github.imlemos.clock.alarm.scheduling

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import io.github.imlemos.clock.MainActivity
import io.github.imlemos.clock.alarm.ringing.AlarmActivity
import io.github.imlemos.clock.alarm.ringing.RingingService

/** Intents and PendingIntents shared by the scheduler, the receiver, the service and notifications. */
object AlarmIntents {
    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_OCCURRENCE = "occurrence"
    const val EXTRA_SNOOZE = "snooze"

    const val ACTION_FIRE = "io.github.imlemos.clock.action.ALARM_FIRE"
    const val ACTION_DISMISS = "io.github.imlemos.clock.action.ALARM_DISMISS"
    const val ACTION_SNOOZE = "io.github.imlemos.clock.action.ALARM_SNOOZE"
    const val ACTION_SILENCE = "io.github.imlemos.clock.action.ALARM_SILENCE"
    const val ACTION_NOTICE = "io.github.imlemos.clock.action.ALARM_NOTICE"
    const val ACTION_DISMISS_EARLY = "io.github.imlemos.clock.action.ALARM_DISMISS_EARLY"
    const val EXTRA_SILENCE_VIBRATION = "silence_vibration"
    const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"

    /** Unique per alarm, so one alarm's PendingIntent never replaces another's. */
    private fun alarmUri(alarmId: Long) = "clock://alarm/$alarmId".toUri()

    fun firePendingIntent(context: Context, alarmId: Long, occurrence: Long?, snooze: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_FIRE)
                .setData(alarmUri(alarmId))
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_OCCURRENCE, occurrence ?: 0L)
                .putExtra(EXTRA_SNOOZE, snooze),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun noticePendingIntent(context: Context, alarmId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, NoticeReceiver::class.java)
            .setAction(ACTION_NOTICE)
            .setData("clock://notice/$alarmId".toUri())
            .putExtra(EXTRA_ALARM_ID, alarmId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun dismissEarlyPendingIntent(context: Context, alarmId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, NoticeReceiver::class.java)
            .setAction(ACTION_DISMISS_EARLY)
            .setData("clock://dismiss-early/$alarmId".toUri())
            .putExtra(EXTRA_ALARM_ID, alarmId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Opened when the user taps the alarm icon in the status bar / quick settings. */
    fun showAlarmsPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun ringingActivityPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun serviceActionPendingIntent(context: Context, action: String): PendingIntent = PendingIntent.getService(
        context,
        action.hashCode(),
        Intent(context, RingingService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
