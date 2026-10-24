package io.github.imlemos.clock.widget

import android.content.Context
import android.text.format.DateFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.data.AlarmRepository
import io.github.imlemos.clock.alarm.layout.render.layoutDateLines
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.scheduling.AlarmScheduler
import io.github.imlemos.clock.alarm.ui.describeNextRing
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.core.data.prefs.TimeFormat
import io.github.imlemos.clock.core.scheduling.Recurrence
import io.github.imlemos.clock.core.zones.ZoneNames
import io.github.imlemos.clock.reliability.ReliabilityChecker
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** The next-alarm widget's content and the alarm it is about (for the skip button). */
data class NextAlarmContent(val content: WidgetContent, val alarmId: Long?, val skipped: Boolean)

/** Turns the clock and the alarms into what widgets show, in the app's language and time format. */
@Singleton
class WidgetContentFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val settings: SettingsRepository,
    private val reliability: ReliabilityChecker,
    private val clock: Clock,
) {
    private val resources get() = context.resources
    private val locale: Locale get() = resources.configuration.locales[0]

    /** The app's 12/24-hour choice, read directly: widgets may render before ClockApp loads it. */
    suspend fun is24Hour(): Boolean = when (settings.timeFormat.first()) {
        TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
        TimeFormat.H24 -> true
        TimeFormat.H12 -> false
    }

    fun now(): ZonedDateTime = clock.instant().atZone(scheduler.systemZone())

    /** The current time and, if shown, the date. */
    fun clock(config: WidgetConfig, now: ZonedDateTime, is24Hour: Boolean): WidgetContent {
        val time = splitTime(now.toLocalTime(), is24Hour, locale)
        val lines = if (config.showDate) layoutDateLines(now.toLocalDate(), config.date, locale) else emptyList()
        return WidgetContent(time.hours, time.minutes, time.dayPeriod, lines)
    }

    /** The next alarm that will ring, or "no alarms"; a warning when alarms may not ring. */
    suspend fun nextAlarm(config: WidgetConfig, is24Hour: Boolean): NextAlarmContent {
        val warning = if (reliability.allOk()) null else resources.getString(R.string.widget_warning)
        val next = findNext()
        if (next == null) {
            return NextAlarmContent(
                WidgetContent(EMPTY, EMPTY, lines = listOf(resources.getString(R.string.widget_no_alarms)), warning = warning, timeMuted = true),
                alarmId = null,
                skipped = false,
            )
        }
        val (alarm, at) = next
        val zone = scheduler.zoneOf(alarm)
        val ringsAt = at.atZone(zone)
        val now = clock.instant().atZone(zone)
        val time = splitTime(ringsAt.toLocalTime(), is24Hour, locale)
        val firstLine = buildList {
            if (config.showDay) add(relativeDay(now, ringsAt))
            if (config.showLabel && alarm.label.isNotBlank()) add(alarm.label)
            scheduler.displayZone(alarm, at)?.let { add(ZoneNames.city(it, locale)) }
            if (alarm.snoozedUntil?.let { it == at } == true) add(resources.getString(R.string.widget_snoozed))
        }.joinToString(SEPARATOR)
        val countdown = if (config.showCountdown && Duration.between(now, ringsAt) < Duration.ofHours(24)) {
            describeNextRing(resources, now, ringsAt, locale)
        } else {
            null
        }
        val skipped = alarm.skippedOccurrence?.let { it > clock.instant() } == true
        val canSkip = alarm.recurrence !is Recurrence.Once
        val action = when {
            !config.showSkip || !canSkip -> null
            skipped -> resources.getString(R.string.alarm_unskip)
            else -> resources.getString(R.string.alarm_skip_next)
        }
        return NextAlarmContent(
            WidgetContent(time.hours, time.minutes, time.dayPeriod, listOfNotNull(firstLine.ifBlank { null }, countdown), warning, action),
            alarmId = alarm.id,
            skipped = skipped,
        )
    }

    /** The active alarm (its own and its group's switch on) that rings first, with when. */
    private suspend fun findNext(): Pair<Alarm, Instant>? {
        val groups = repository.getGroups().associateBy { it.id }
        val appZone = scheduler.appZone()
        return repository.getAll()
            .filter { alarm -> alarm.enabled && (alarm.groupId?.let { groups[it]?.enabled } ?: true) }
            .mapNotNull { alarm -> scheduler.nextTrigger(alarm, appZone)?.let { alarm to it.at } }
            .minByOrNull { it.second }
    }

    private fun relativeDay(now: ZonedDateTime, at: ZonedDateTime): String {
        val today = now.toLocalDate()
        return when (at.toLocalDate()) {
            today -> resources.getString(R.string.widget_today)
            today.plusDays(1) -> resources.getString(R.string.widget_tomorrow)
            else -> DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale).format(at)
        }
    }

    private companion object {
        const val EMPTY = "--"
        const val SEPARATOR = " · "
    }
}

/** Hours, minutes and (12-hour clocks) the day period, as the big time shows them. */
data class SplitTime(val hours: String, val minutes: String, val dayPeriod: String?)

fun splitTime(time: LocalTime, is24Hour: Boolean, locale: Locale): SplitTime =
    if (is24Hour) {
        SplitTime("%02d".format(locale, time.hour), "%02d".format(locale, time.minute), null)
    } else {
        val hour12 = (time.hour % 12).let { if (it == 0) 12 else it }
        SplitTime(hour12.toString(), "%02d".format(locale, time.minute), DateTimeFormatter.ofPattern("a", locale).format(time))
    }
