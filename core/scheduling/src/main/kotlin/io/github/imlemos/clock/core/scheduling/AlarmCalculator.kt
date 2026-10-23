package io.github.imlemos.clock.core.scheduling

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Scheduling maths. A mistake here means someone does not wake up, so every rule is covered by
 * unit tests (AlarmCalculatorTest).
 *
 * Wall-clock rules:
 * - A time that does not exist on a given day (spring-forward gap) rings at the transition, i.e.
 *   the first valid moment after the requested time.
 * - A time that happens twice (fall-back overlap) rings once, at the first of the two.
 */
public object AlarmCalculator {

    /** The zone an alarm follows: its own zone, else the app's manual zone, else the system zone. */
    public fun effectiveZone(alarmZone: ZoneId?, manualZone: ZoneId?, systemZone: ZoneId): ZoneId =
        alarmZone ?: manualZone ?: systemZone

    /**
     * The first scheduled occurrence strictly after [after] (and after the last triggered one),
     * ignoring snooze and skipping [AlarmSchedule.skippedOccurrence]. `null` when nothing is left
     * (a `Dates` alarm past its last date).
     */
    public fun nextOccurrence(schedule: AlarmSchedule, after: Instant, appZone: ZoneId): ZonedDateTime? {
        val zone = schedule.zone ?: appZone
        val lowerBound = maxOf(after, schedule.lastTriggeredOccurrence ?: Instant.MIN)
        return candidateDates(schedule.recurrence, lowerBound.atZone(zone).toLocalDate())
            .map { date -> resolve(date, schedule.time, zone) }
            .filter { it.toInstant() > lowerBound && it.toInstant() != schedule.skippedOccurrence }
            .firstOrNull()
    }

    /**
     * When AlarmManager must fire next, combining the schedule with an active snooze. `null` when
     * the alarm is disabled or has nothing left to ring.
     *
     * A snooze that already expired (e.g. the phone was off) is ignored here; reporting it as a
     * missed alarm is handled by the caller.
     */
    public fun nextTrigger(schedule: AlarmSchedule, now: Instant, appZone: ZoneId): Trigger? {
        if (!schedule.enabled) return null
        val next = nextOccurrence(schedule, now, appZone)?.let { Trigger(it.toInstant(), it.toInstant(), isSnooze = false) }
        val snooze = schedule.snoozedUntil
            ?.takeIf { it > now }
            ?.let { Trigger(it, schedule.lastTriggeredOccurrence ?: it, isSnooze = true) }
        return listOfNotNull(next, snooze).minByOrNull { it.at }
    }

    /** Resolves a wall-clock date and time in [zone], applying the gap and overlap rules above. */
    public fun resolve(date: LocalDate, time: TimeOfDay, zone: ZoneId): ZonedDateTime {
        val local = LocalDateTime.of(date, LocalTime.of(time.hour, time.minute))
        val rules = zone.rules
        val offsets = rules.getValidOffsets(local)
        return when {
            offsets.isEmpty() -> ZonedDateTime.ofInstant(rules.getTransition(local).instant, zone)
            // In an overlap the list is [offset before, offset after]; the first is the earlier instant.
            else -> ZonedDateTime.ofLocal(local, zone, offsets.first())
        }
    }

    /**
     * Candidate dates in order. Starts one day before [from] so a zone far behind the device's
     * cannot miss an occurrence; later filtering removes anything not strictly in the future.
     */
    private fun candidateDates(recurrence: Recurrence, from: LocalDate): Sequence<LocalDate> {
        val start = from.minusDays(1)
        return when (recurrence) {
            Recurrence.Once -> generateSequence(start) { it.plusDays(1) }.take(4)
            is Recurrence.Weekly -> generateSequence(start) { it.plusDays(1) }
                .take(DAYS_TO_SCAN_WEEKLY)
                .filter { it.dayOfWeek in recurrence.days }
            is Recurrence.Dates -> recurrence.dates.sorted().asSequence().filter { it >= start }
        }
    }

    // Two full weeks: enough to step over a skipped occurrence of a single-day weekly alarm.
    private const val DAYS_TO_SCAN_WEEKLY = 16
}
