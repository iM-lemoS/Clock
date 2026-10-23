package io.github.imlemos.clock.alarm.layout.render

import android.text.format.DateFormat
import io.github.imlemos.clock.alarm.layout.DateArrangement
import io.github.imlemos.clock.alarm.layout.DateParts
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The date as the layout's [parts] ask, in the locale's own order and wording ("sexta-feira,
 * 23 de outubro de 2026", "outubro de 2026", "Friday"): one line, or two when the weekday sits
 * above or below the rest. Empty when no part is chosen.
 */
fun layoutDateLines(date: LocalDate, parts: DateParts, locale: Locale): List<String> {
    fun format(skeleton: String): String =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(date)

    val rest = buildString {
        if (parts.day) append("d")
        if (parts.month) append("MMMM")
        if (parts.year) append("y")
    }
    return when {
        !parts.weekday && rest.isEmpty() -> emptyList()
        !parts.weekday -> listOf(format(rest))
        rest.isEmpty() -> listOf(format("EEEE"))
        !parts.split -> listOf(format("EEEE$rest"))
        parts.arrangement == DateArrangement.WEEKDAY_ABOVE -> listOf(format("EEEE"), format(rest))
        else -> listOf(format(rest), format("EEEE"))
    }
}
