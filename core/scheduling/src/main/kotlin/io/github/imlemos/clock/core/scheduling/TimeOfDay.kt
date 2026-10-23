package io.github.imlemos.clock.core.scheduling

/**
 * A wall-clock time without a date or time zone, as the user sets it on an alarm.
 *
 * Resolving it to an instant (next occurrence, recurrences, time zones, DST gaps and overlaps)
 * belongs to this module and is introduced in phase 1.
 */
public data class TimeOfDay(val hour: Int, val minute: Int) : Comparable<TimeOfDay> {

    init {
        require(hour in 0..23) { "hour must be in 0..23, was $hour" }
        require(minute in 0..59) { "minute must be in 0..59, was $minute" }
    }

    /** Minutes elapsed since midnight, in `0 until 1440`. */
    public val minuteOfDay: Int get() = hour * 60 + minute

    override fun compareTo(other: TimeOfDay): Int = minuteOfDay.compareTo(other.minuteOfDay)

    public companion object {
        public fun ofMinuteOfDay(minuteOfDay: Int): TimeOfDay {
            require(minuteOfDay in 0 until MINUTES_PER_DAY) {
                "minuteOfDay must be in 0 until $MINUTES_PER_DAY, was $minuteOfDay"
            }
            return TimeOfDay(minuteOfDay / 60, minuteOfDay % 60)
        }

        private const val MINUTES_PER_DAY = 24 * 60
    }
}
