package io.github.imlemos.clock.alarm.scheduling

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.core.scheduling.AlarmCalculator
import io.github.imlemos.clock.core.scheduling.Trigger
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands alarms to AlarmManager. Every alarm uses [AlarmManager.setAlarmClock]: highest priority,
 * fires in Doze, and shows the alarm icon in the status bar.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** The zone used for alarms without their own zone. The manual app zone arrives in phase 5. */
    fun appZone(): ZoneId = ZoneId.systemDefault()

    fun nextTrigger(alarm: Alarm): Trigger? = AlarmCalculator.nextTrigger(alarm.schedule, clock.instant(), appZone())

    /** Schedules (or cancels) the next trigger of [alarm]. Returns what was scheduled. */
    fun schedule(alarm: Alarm): Trigger? {
        val trigger = nextTrigger(alarm)
        if (trigger == null) {
            cancel(alarm.id)
            return null
        }
        val operation = AlarmIntents.firePendingIntent(
            context,
            alarm.id,
            trigger.occurrence.toEpochMilli(),
            trigger.isSnooze,
        )
        val at = trigger.at.toEpochMilli()
        if (canScheduleExactAlarms()) {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(at, AlarmIntents.showAlarmsPendingIntent(context)),
                operation,
            )
        } else {
            // Android 12 with "Alarms & reminders" revoked: best effort until the user grants it
            // again (the reliability screen flags this).
            Log.w(TAG, "Exact alarms not allowed; scheduling alarm ${alarm.id} inexactly")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
        Log.i(TAG, "Alarm ${alarm.id} scheduled for ${trigger.at} (snooze=${trigger.isSnooze})")
        return trigger
    }

    fun cancel(alarmId: Long) {
        alarmManager.cancel(AlarmIntents.firePendingIntent(context, alarmId, null, false))
        Log.i(TAG, "Alarm $alarmId cancelled")
    }

    fun canScheduleExactAlarms(): Boolean = alarmManager.canScheduleExactAlarms()

    private companion object {
        const val TAG = "AlarmScheduler"
    }
}
