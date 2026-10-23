package io.github.imlemos.clock.core.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.imlemos.clock.MainActivity
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.scheduling.AlarmIntents
import io.github.imlemos.clock.alarm.ui.formatTimeOfDay
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Alarm notifications other than the ringing one (which belongs to RingingService). */
@Singleton
class AlarmNotifications @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun showMissed(alarmId: Long, time: TimeOfDay, label: String) {
        val notification = NotificationCompat.Builder(context, NotificationChannels.ALARM_MISSED)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(context.getString(R.string.alarm_missed_title, formatTimeOfDay(context, time)))
            .setContentText(label.ifBlank { null })
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(AlarmIntents.showAlarmsPendingIntent(context))
            .setAutoCancel(true)
            .build()
        manager.notify(MISSED_BASE + alarmId.toInt(), notification)
    }

    /**
     * One notification per alarm, updated in place, counting down to the alarm, with "Dismiss this
     * one" (docs/PLANO.md 3.4).
     */
    fun showUpcoming(alarm: Alarm, occurrence: Instant) {
        val notification = NotificationCompat.Builder(context, NotificationChannels.ALARM_UPCOMING)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(context.getString(R.string.upcoming_title, formatTimeOfDay(context, alarm.time)))
            .setContentText(alarm.label.ifBlank { null })
            .setWhen(occurrence.toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(occurrence.toEpochMilli() - System.currentTimeMillis())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(true)
            .setContentIntent(AlarmIntents.showAlarmsPendingIntent(context))
            .addAction(0, context.getString(R.string.upcoming_dismiss), AlarmIntents.dismissEarlyPendingIntent(context, alarm.id))
            .build()
        manager.notify(UPCOMING_BASE + alarm.id.toInt(), notification)
    }

    fun cancelUpcoming(alarmId: Long) = manager.cancel(UPCOMING_BASE + alarmId.toInt())

    /** "This alarm rang for the last time and was deleted. Tap to undo." */
    fun showDeleted(alarm: Alarm, trashId: Long) {
        val undo = PendingIntent.getActivity(
            context,
            trashId.toInt(),
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_RESTORE_FROM_TRASH)
                .putExtra(MainActivity.EXTRA_TRASH_ID, trashId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = context.getString(R.string.deleted_title, formatTimeOfDay(context, alarm.time))
        val notification = NotificationCompat.Builder(context, NotificationChannels.ALARM_DELETED)
            .setSmallIcon(R.drawable.ic_delete)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.deleted_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.deleted_body)))
            .setContentIntent(undo)
            .setAutoCancel(true)
            .build()
        manager.notify(DELETED_BASE + trashId.toInt(), notification)
    }

    fun cancelDeleted(trashId: Long) = manager.cancel(DELETED_BASE + trashId.toInt())

    private companion object {
        const val MISSED_BASE = 2_000_000
        const val UPCOMING_BASE = 3_000_000
        const val DELETED_BASE = 4_000_000
    }
}
