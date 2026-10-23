package io.github.imlemos.clock.core.scheduling

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** World clock arithmetic (docs/PLANO.md 5.1); Android-free and unit tested. */
public object WorldClockMath {

    /** Minutes [zone] is ahead of [here] at [at] (negative when behind); follows each DST. */
    public fun differenceMinutes(zone: ZoneId, here: ZoneId, at: Instant): Int =
        (zone.rules.getOffset(at).totalSeconds - here.rules.getOffset(at).totalSeconds) / 60

    /** Whether the date in [zone] is yesterday (-1), the same (0) or tomorrow (1) compared with [here]. */
    public fun dayOffset(zone: ZoneId, here: ZoneId, at: Instant): Int =
        ChronoUnit.DAYS.between(at.atZone(here).toLocalDate(), at.atZone(zone).toLocalDate()).toInt()

    /** Daytime between [DAY_START] and [NIGHT_START] local hours; a simple, location-free rule. */
    public fun isDaytime(zone: ZoneId, at: Instant): Boolean = at.atZone(zone).hour in DAY_START until NIGHT_START

    public const val DAY_START: Int = 7
    public const val NIGHT_START: Int = 19
}
