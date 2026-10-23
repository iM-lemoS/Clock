package io.github.imlemos.clock.core.scheduling

import java.time.Instant
import java.time.ZoneId

/**
 * Everything needed to decide when an alarm rings next. Pure data, no Android types.
 *
 * @property zone The alarm's own time zone. `null` follows the app zone (manual setting or system).
 * @property skippedOccurrence "Skip next": this exact occurrence does not ring.
 * @property lastTriggeredOccurrence The last occurrence that already rang (or was dismissed early).
 *   Occurrences at or before it are never returned again, which makes rescheduling idempotent.
 * @property snoozedUntil When a snoozed alarm rings again, if snoozed.
 */
public data class AlarmSchedule(
    val time: TimeOfDay,
    val recurrence: Recurrence,
    val enabled: Boolean = true,
    val zone: ZoneId? = null,
    val skippedOccurrence: Instant? = null,
    val lastTriggeredOccurrence: Instant? = null,
    val snoozedUntil: Instant? = null,
)

/**
 * The next moment an alarm must fire.
 *
 * @property at When AlarmManager should fire.
 * @property occurrence The scheduled occurrence this trigger belongs to. For a snooze it is the
 *   occurrence that was snoozed.
 */
public data class Trigger(
    val at: Instant,
    val occurrence: Instant,
    val isSnooze: Boolean,
)
