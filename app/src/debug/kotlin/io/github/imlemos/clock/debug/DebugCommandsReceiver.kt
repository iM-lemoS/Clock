package io.github.imlemos.clock.debug

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import io.github.imlemos.clock.alarm.data.AlarmController
import io.github.imlemos.clock.alarm.data.AlarmRepository
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.model.AlarmSound
import io.github.imlemos.clock.alarm.model.GradualVolume
import io.github.imlemos.clock.alarm.model.Vibration
import io.github.imlemos.clock.core.data.prefs.AudioOutput
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.core.scheduling.Recurrence
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import io.github.imlemos.clock.timer.data.TimerController
import io.github.imlemos.clock.timer.data.TimerRepository
import androidx.compose.ui.graphics.toArgb
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.layout.render.layoutDateLines
import io.github.imlemos.clock.core.designsystem.theme.AccentPalette
import io.github.imlemos.clock.widget.ClockWidgetProvider
import io.github.imlemos.clock.widget.WidgetConfig
import io.github.imlemos.clock.widget.WidgetContent
import io.github.imlemos.clock.widget.WidgetRenderer
import io.github.imlemos.clock.widget.NextAlarmWidgetProvider
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
 *     | --ez single_use true | --es vibration HEARTBEAT | --ei gradual_seconds 30 --ei gradual_target 80
 *     | --ei ring_seconds 120 | --es zone America/New_York (the alarm's own zone; same moment)
 *     | --el layout -2 (layout id; presets are negative)
 * adb shell am broadcast -a io.github.imlemos.clock.debug.DELETE_ALL_ALARMS -n <same component>
 *     (also deletes groups and timers)
 * adb shell am broadcast -a io.github.imlemos.clock.debug.SET_NOTICES -n <same component> --es minutes 30,5
 *     (empty string disables notices)
 * adb shell am broadcast -a io.github.imlemos.clock.debug.SET_AUDIO_OUTPUT -n <same component> --es output PHONE_ONLY
 * adb shell am broadcast -a io.github.imlemos.clock.debug.START_TIMER -n <same component> --ei seconds 10 --es name Tea
 *     (creates a timer and starts it)
 * adb shell am broadcast -a io.github.imlemos.clock.debug.SET_MANUAL_ZONE -n <same component> --es zone Asia/Tokyo
 *     (empty string = automatic)
 * adb shell am broadcast -a io.github.imlemos.clock.debug.PIN_WIDGET -n <same component> --es kind clock
 *     (the app must be open; asks the launcher to add a widget: clock | next_alarm)
 * adb shell am broadcast -a io.github.imlemos.clock.debug.EXPORT_WIDGET_PREVIEWS -n <same component>
 *     (draws the widget picker previews, in English and Portuguese, into files/previews)
 * ```
 */
@AndroidEntryPoint
class DebugCommandsReceiver : BroadcastReceiver() {

    @Inject lateinit var controller: AlarmController
    @Inject lateinit var repository: AlarmRepository
    @Inject lateinit var clock: Clock
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var timers: TimerController
    @Inject lateinit var timerRepository: TimerRepository

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
                        zone = intent.getStringExtra("zone")?.let(ZoneId::of),
                        layoutId = if (intent.hasExtra("layout")) intent.getLongExtra("layout", 0) else null,
                        sound = soundFrom(intent, settings.defaultSound.first()),
                    )
                    ACTION_DELETE_ALL -> {
                        repository.getGroups().forEach { controller.deleteGroup(it.id, withAlarms = true) }
                        repository.getAll().forEach { controller.delete(it.id) }
                        timerRepository.getAll().forEach { timers.delete(it.id) }
                    }
                    ACTION_START_TIMER -> {
                        val id = timers.create(intent.getStringExtra("name").orEmpty(), intent.getIntExtra("seconds", 10) * 1000L)
                        timers.start(id)
                        Log.i(TAG, "Test timer $id started")
                    }
                    ACTION_SET_MANUAL_ZONE ->
                        settings.setManualZone(intent.getStringExtra("zone")?.takeIf(String::isNotBlank)?.let(ZoneId::of))
                    ACTION_SET_AUDIO_OUTPUT ->
                        settings.setAudioOutput(AudioOutput.valueOf(intent.getStringExtra("output").orEmpty()))
                    ACTION_PIN_WIDGET -> {
                        val provider = when (intent.getStringExtra("kind")) {
                            "next_alarm" -> NextAlarmWidgetProvider::class.java
                            else -> ClockWidgetProvider::class.java
                        }
                        val manager = context.getSystemService(AppWidgetManager::class.java)
                        // The app must be in the foreground: open it first.
                        val asked = manager.isRequestPinAppWidgetSupported &&
                            runCatching { manager.requestPinAppWidget(ComponentName(context, provider), null, null) }
                                .onFailure { Log.w(TAG, "Pin widget failed", it) }
                                .getOrDefault(false)
                        Log.i(TAG, "Pin widget ${provider.simpleName}: requested=$asked")
                    }
                    ACTION_EXPORT_WIDGET_PREVIEWS -> exportWidgetPreviews(context)
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

    /** The widget picker's sample images: each widget's default look with sample content. */
    // Draws in each language through a configuration context; nothing changes the app's locale.
    @SuppressLint("AppBundleLocaleChanges")
    private fun exportWidgetPreviews(context: Context) {
        val dir = java.io.File(context.filesDir, "previews").apply { mkdirs() }
        val density = 3f
        listOf("en", "pt").forEach { language ->
            val localized = context.createConfigurationContext(
                android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(language)) },
            )
            val res = localized.resources
            val locale = java.util.Locale.forLanguageTag(language)
            val renderer = WidgetRenderer(localized) { java.io.File(it) }
            val date = layoutDateLines(java.time.LocalDate.of(2026, 10, 24), WidgetConfig().date, locale)
            val samples = listOf(
                Triple("clock", 330 to 150, WidgetContent("09", "30", lines = date)),
                Triple(
                    "next_alarm",
                    250 to 150,
                    WidgetContent(
                        "07",
                        "00",
                        lines = listOf(
                            res.getString(R.string.widget_tomorrow) + " · " + res.getString(R.string.widget_preview_label),
                            res.getString(R.string.alarm_rings_in, res.getString(R.string.duration_hours_minutes, 9, 30)),
                        ),
                        action = res.getString(R.string.alarm_skip_next),
                    ),
                ),
            )
            samples.forEach { (name, size, content) ->
                val image = renderer.render(WidgetConfig(), content, (size.first * density).toInt(), (size.second * density).toInt(), density, night = true, accent = AccentPalette.Default.color.toArgb())
                java.io.File(dir, "widget_preview_${name}_$language.png").outputStream().use { image.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        Log.i(TAG, "Widget previews written to $dir")
    }

    /** An alarm at the start of the minute [minutes] from now; one-off unless days or dates are given. */
    private fun soundFrom(intent: Intent, defaults: AlarmSound): AlarmSound {
        val gradualSeconds = intent.getIntExtra("gradual_seconds", 0)
        return defaults.copy(
            vibration = intent.getStringExtra("vibration")?.let(Vibration::valueOf) ?: defaults.vibration,
            gradual = if (gradualSeconds > 0) {
                GradualVolume(true, intent.getIntExtra("gradual_target", 100), gradualSeconds)
            } else {
                defaults.gradual
            },
            ringDurationSeconds = intent.getIntExtra("ring_seconds", defaults.ringDurationSeconds),
            snoozeMinutes = intent.getIntExtra("snooze_minutes", defaults.snoozeMinutes),
        )
    }

    private suspend fun createTestAlarm(
        minutes: Int,
        label: String,
        days: String?,
        dates: String?,
        singleUse: Boolean,
        zone: ZoneId?,
        layoutId: Long?,
        sound: AlarmSound,
    ) {
        val at = clock.instant().atZone(zone ?: settings.manualZone.first() ?: ZoneId.systemDefault()).plusMinutes(minutes.toLong()).truncatedTo(ChronoUnit.MINUTES)
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
                sound = sound,
                zone = zone,
                layoutId = layoutId,
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
        const val ACTION_PIN_WIDGET = "io.github.imlemos.clock.debug.PIN_WIDGET"
        const val ACTION_EXPORT_WIDGET_PREVIEWS = "io.github.imlemos.clock.debug.EXPORT_WIDGET_PREVIEWS"
        const val ACTION_START_TIMER = "io.github.imlemos.clock.debug.START_TIMER"
        const val ACTION_SET_MANUAL_ZONE = "io.github.imlemos.clock.debug.SET_MANUAL_ZONE"
        const val ACTION_SET_AUDIO_OUTPUT = "io.github.imlemos.clock.debug.SET_AUDIO_OUTPUT"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
