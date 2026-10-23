package io.github.imlemos.clock.core.scheduling

import java.time.Instant
import java.time.ZoneId

/** What happens to an alarm once an occurrence is over (rang, timed out, missed or dismissed early). */
public enum class AfterOccurrence {
    /** Stays on for its next occurrence. */
    KEEP,

    /** A one-off alarm: stays in the list, switched off. */
    DISABLE,

    /** Single-use, or a dates alarm past its last date: goes to the trash (docs/PLANO.md 3.1). */
    MOVE_TO_TRASH,
}

/** Lifecycle rules from docs/PLANO.md sections 3.1 and 3.4. */
public object AlarmLifecycle {

    public fun afterOccurrence(
        schedule: AlarmSchedule,
        singleUse: Boolean,
        occurrence: Instant,
        appZone: ZoneId,
    ): AfterOccurrence = when {
        singleUse -> AfterOccurrence.MOVE_TO_TRASH
        schedule.recurrence == Recurrence.Once -> AfterOccurrence.DISABLE
        schedule.recurrence is Recurrence.Dates &&
            AlarmCalculator.nextOccurrence(
                schedule.copy(lastTriggeredOccurrence = occurrence, skippedOccurrence = null),
                occurrence,
                appZone,
            ) == null -> AfterOccurrence.MOVE_TO_TRASH
        else -> AfterOccurrence.KEEP
    }

    /**
     * Notice times before an occurrence: the next one still ahead of [now], or `null` when none is
     * left. [minutesBefore] comes from the "notices before the alarm" setting (default 30).
     */
    public fun nextNotice(occurrence: Instant, minutesBefore: Set<Int>, now: Instant): Instant? =
        minutesBefore
            .map { occurrence.minusSeconds(it * 60L) }
            .filter { it > now }
            .minOrNull()

    /** Whether the upcoming-alarm notification should be visible at [now]. */
    public fun isInNoticeWindow(occurrence: Instant, minutesBefore: Set<Int>, now: Instant): Boolean {
        val earliest = minutesBefore.maxOrNull() ?: return false
        return now >= occurrence.minusSeconds(earliest * 60L) && now < occurrence
    }
}
