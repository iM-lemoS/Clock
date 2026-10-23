package io.github.imlemos.clock.alarm.ui

import android.content.Context
import android.content.res.Resources
import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.data.prefs.TimeFormat
import io.github.imlemos.clock.core.scheduling.Recurrence
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import io.github.imlemos.clock.core.zones.ZoneNames
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The app's 12/24-hour choice. Snapshot state, so composables re-render when the setting changes;
 * kept up to date from settings by ClockApp (also in direct boot).
 */
object ClockTimeFormat {
    var format: TimeFormat by mutableStateOf(TimeFormat.SYSTEM)
}

/** Whether times use 24-hour format: the app setting, or the phone's when set to follow it. */
fun is24Hour(context: Context): Boolean = when (ClockTimeFormat.format) {
    TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
    TimeFormat.H24 -> true
    TimeFormat.H12 -> false
}

/** Hours and minutes split for dot-matrix rendering, following [is24Hour]. */
data class TimeParts(val hours: String, val minutes: String, val dayPeriod: String?)

fun timeParts(context: Context, time: TimeOfDay, locale: Locale = Locale.getDefault()): TimeParts {
    val is24 = is24Hour(context)
    val local = LocalTime.of(time.hour, time.minute)
    return if (is24) {
        TimeParts("%02d".format(locale, time.hour), "%02d".format(locale, time.minute), null)
    } else {
        val hour12 = (time.hour % 12).let { if (it == 0) 12 else it }
        TimeParts(hour12.toString(), "%02d".format(locale, time.minute), DateTimeFormatter.ofPattern("a", locale).format(local))
    }
}

/** "07:30" or "7:30 AM", localized, following [is24Hour]. */
fun formatTimeOfDay(context: Context, time: TimeOfDay, locale: Locale = Locale.getDefault()): String {
    val skeleton = if (is24Hour(context)) "Hm" else "hma"
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        .format(LocalTime.of(time.hour, time.minute))
}

/** "08:00", or "08:00 New York" for an alarm whose time is not the phone's ([zone] not `null`). */
fun formatAlarmTime(context: Context, time: TimeOfDay, zone: ZoneId?, locale: Locale = Locale.getDefault()): String {
    val text = formatTimeOfDay(context, time, locale)
    return if (zone == null) text else context.getString(R.string.time_in_zone, text, ZoneNames.city(zone, locale))
}

/** The wall-clock time of [at], formatted like [formatTimeOfDay]. */
fun formatTimeOfDay(context: Context, at: ZonedDateTime, locale: Locale = Locale.getDefault()): String =
    formatTimeOfDay(context, TimeOfDay(at.hour, at.minute), locale)

/**
 * The phone's time for an alarm in another zone, with the short weekday when the day differs from
 * the alarm's own ([alarmDate]): "22:00" or "Sun 22:00".
 */
fun formatHereTime(context: Context, here: ZonedDateTime, alarmDate: LocalDate, locale: Locale = Locale.getDefault()): String {
    val time = formatTimeOfDay(context, here, locale)
    if (here.toLocalDate() == alarmDate) return time
    return here.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).trimEnd('.') + " " + time
}

/** A snooze length: "15 min" or "1 h". */
fun formatSnooze(resources: Resources, minutes: Int): String = when {
    minutes < 60 -> resources.getString(R.string.duration_minutes, minutes)
    minutes % 60 == 0 -> resources.getString(R.string.duration_hours, minutes / 60)
    else -> resources.getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
}

/** A notice length such as "1 day 2 h 30 min" (days, hours and minutes, each only when present). */
fun formatNotice(resources: Resources, minutes: Int): String {
    val days = minutes / (24 * 60)
    val hours = minutes % (24 * 60) / 60
    val rest = minutes % 60
    return buildList {
        if (days > 0) add(resources.getQuantityString(R.plurals.notice_days, days, days))
        if (hours > 0) add(resources.getString(R.string.duration_hours, hours))
        if (rest > 0 || (days == 0 && hours == 0)) add(resources.getString(R.string.duration_minutes, rest))
    }.joinToString(" ")
}

/** Like [formatSnooze] but "1h30" for mixed lengths, to fit a snooze dial bubble. */
fun formatSnoozeCompact(resources: Resources, minutes: Int): String =
    if (minutes < 60 || minutes % 60 == 0) formatSnooze(resources, minutes) else resources.getString(R.string.duration_hours_minutes_compact, minutes / 60, minutes % 60)

/** "in 7 h 30 min" for the next 24 hours, otherwise the localized weekday and date. */
fun describeNextRing(resources: Resources, now: ZonedDateTime, next: ZonedDateTime, locale: Locale): String {
    val until = Duration.between(now, next)
    if (until < Duration.ofHours(24)) {
        val totalMinutes = ((until.toSeconds() + 59) / 60).coerceAtLeast(1)
        val hours = (totalMinutes / 60).toInt()
        val minutes = (totalMinutes % 60).toInt()
        val amount = when {
            hours == 0 -> resources.getString(R.string.duration_minutes, minutes)
            minutes == 0 -> resources.getString(R.string.duration_hours, hours)
            else -> resources.getString(R.string.duration_hours_minutes, hours, minutes)
        }
        return resources.getString(R.string.alarm_rings_in, amount)
    }
    val pattern = DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
    return DateTimeFormatter.ofPattern(pattern, locale).format(next)
}

/** "Once", "Weekdays", "Mon, Wed, Fri", "3 Nov, 10 Nov, +2"... for alarm cards. */
fun describeRecurrence(resources: Resources, recurrence: Recurrence, locale: Locale, today: LocalDate): String =
    when (recurrence) {
        Recurrence.Once -> resources.getString(R.string.recurrence_once)
        is Recurrence.Weekly -> {
            val days = recurrence.days
            when {
                days.size == 7 -> resources.getString(R.string.recurrence_every_day)
                days == WORKDAYS -> resources.getString(R.string.recurrence_weekdays)
                days == WEEKEND -> resources.getString(R.string.recurrence_weekends)
                else -> {
                    val first = WeekFields.of(locale).firstDayOfWeek
                    (0L until 7L).map { first.plus(it) }
                        .filter { it in days }
                        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT_STANDALONE, locale).trimEnd('.') }
                }
            }
        }
        is Recurrence.Dates -> {
            val upcoming = recurrence.dates.filter { it >= today }.sorted().ifEmpty { recurrence.dates.sorted() }
            val formatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale)
            val shown = upcoming.take(MAX_DATES_SHOWN).joinToString(", ") { formatter.format(it).trimEnd('.') }
            val more = upcoming.size - MAX_DATES_SHOWN
            if (more > 0) "$shown, ${resources.getString(R.string.recurrence_more, more)}" else shown
        }
    }

private const val MAX_DATES_SHOWN = 3
private val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
