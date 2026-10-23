package io.github.imlemos.clock.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import io.github.imlemos.clock.alarm.data.AlarmController
import io.github.imlemos.clock.alarm.data.AlarmRepository
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.core.scheduling.Recurrence
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * adb hooks for on-device testing (see CLAUDE.md):
 *
 * ```
 * adb shell am broadcast -a io.github.imlemos.clock.debug.TEST_ALARM \
 *     -n io.github.imlemos.clock.debug/io.github.imlemos.clock.debug.DebugCommandsReceiver \
 *     --ei in_minutes 2 --es label "Doze test"
 * Optional extras: --es days 1,3,5 (ISO days, weekly) | --es dates today,2026-11-03 (dates)
 *     | --ez single_use true
 * adb shell am broadcast -a io.github.imlemos.clock.debug.DELETE_ALL_ALARMS -n <same component>
 * adb shell am broadcast -a io.github.imlemos.clock.debug.SET_NOTICES -n <same component> --es minutes 30,5
 *     (empty string disables notices)
 * ```
 */
@AndroidEntryPoint
class DebugCommandsReceiver : BroadcastReceiver() {

    @Inject lateinit var controller: AlarmController
    @Inject lateinit var repository: AlarmRepository
    @Inject lateinit var clock: Clock
    @Inject lateinit var settings: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_TEST_ALARM -> createTestAlarm(
                        minutes = intent.getIntExtra("in_minutes", 1).coerceAtLeast(1),
                        label = intent.getStringExtra("label").orEmpty(),
                        days = intent.getStringExtra("days"),
                        dates = intent.getStringExtra("dates"),
                        singleUse = intent.getBooleanExtra("single_use", false),
                    )
                    ACTION_DELETE_ALL -> repository.getAll().forEach { controller.delete(it.id) }
                    ACTION_SET_NOTICES -> {
                        val minutes = intent.getStringExtra("minutes").orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }
                        settings.setNoticeMinutes(minutes.toSet())
                        controller.rescheduleAll()
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** An alarm at the start of the minute [minutes] from now; one-off unless days or dates are given. */
    private suspend fun createTestAlarm(minutes: Int, label: String, days: String?, dates: String?, singleUse: Boolean) {
        val at = clock.instant().atZone(ZoneId.systemDefault()).plusMinutes(minutes.toLong()).truncatedTo(ChronoUnit.MINUTES)
        val recurrence = when {
            days != null -> Recurrence.Weekly(days.split(',').map { DayOfWeek.of(it.trim().toInt()) }.toSet())
            dates != null -> Recurrence.Dates(
                dates.split(',').map { if (it.trim() == "today") at.toLocalDate() else LocalDate.parse(it.trim()) }.toSet(),
            )
            else -> Recurrence.Once
        }
        val saved = controller.save(
            Alarm(
                time = TimeOfDay(at.hour, at.minute),
                recurrence = recurrence,
                label = label,
                singleUse = singleUse,
                createdAt = clock.instant(),
            ),
        )
        Log.i(TAG, "Test alarm ${saved.id} '$label' at $at")
    }

    private companion object {
        const val TAG = "DebugCommands"
        const val ACTION_TEST_ALARM = "io.github.imlemos.clock.debug.TEST_ALARM"
        const val ACTION_DELETE_ALL = "io.github.imlemos.clock.debug.DELETE_ALL_ALARMS"
        const val ACTION_SET_NOTICES = "io.github.imlemos.clock.debug.SET_NOTICES"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
