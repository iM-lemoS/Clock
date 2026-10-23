package io.github.imlemos.clock.alarm.ui

import android.content.Context
import android.content.res.Resources
import android.text.format.DateFormat
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Hours and minutes split for dot-matrix rendering, following the system 12/24 h setting. */
data class TimeParts(val hours: String, val minutes: String, val dayPeriod: String?)

fun timeParts(context: Context, time: TimeOfDay, locale: Locale = Locale.getDefault()): TimeParts {
    val is24 = DateFormat.is24HourFormat(context)
    val local = LocalTime.of(time.hour, time.minute)
    return if (is24) {
        TimeParts("%02d".format(locale, time.hour), "%02d".format(locale, time.minute), null)
    } else {
        val hour12 = (time.hour % 12).let { if (it == 0) 12 else it }
        TimeParts(hour12.toString(), "%02d".format(locale, time.minute), DateTimeFormatter.ofPattern("a", locale).format(local))
    }
}

/** "07:30" or "7:30 AM", as the system formats times. */
fun formatTimeOfDay(context: Context, time: TimeOfDay): String =
    DateFormat.getTimeFormat(context).format(
        java.util.Date.from(
            java.time.LocalDate.now().atTime(time.hour, time.minute).atZone(java.time.ZoneId.systemDefault()).toInstant(),
        ),
    )

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
