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
            .take(MAX_CANDIDATES)
            .filter { it.toInstant() > lowerBound && it.toInstant() != schedule.skippedOccurrence }
            .firstOrNull()
    }

    /**
     * The occurrence "Skip next" would skip: the next one ignoring any current skip. Used to show
     * and toggle the skip on recurring alarms.
     */
    public fun upcomingOccurrence(schedule: AlarmSchedule, now: Instant, appZone: ZoneId): ZonedDateTime? =
        nextOccurrence(schedule.copy(skippedOccurrence = null), now, appZone)

    /**
     * The most recent occurrence that should have rung but did not, e.g. because the phone was off.
     * Only occurrences after [armedAt] (when the alarm was last saved or switched on) and after the
     * last triggered one count, so editing or re-enabling an alarm never reports old occurrences.
     */
    public fun missedOccurrence(
        schedule: AlarmSchedule,
        armedAt: Instant,
        now: Instant,
        appZone: ZoneId,
    ): ZonedDateTime? {
        if (!schedule.enabled) return null
        val zone = schedule.zone ?: appZone
        val lowerBound = maxOf(armedAt, schedule.lastTriggeredOccurrence ?: Instant.MIN)
        if (lowerBound >= now) return null
        val firstDay = lowerBound.atZone(zone).toLocalDate().minusDays(1)
        val lastDay = now.atZone(zone).toLocalDate()
        return candidateDates(schedule.recurrence, firstDay.plusDays(1))
            .takeWhile { it <= lastDay }
            .map { date -> resolve(date, schedule.time, zone) }
            .filter { it.toInstant() > lowerBound && it.toInstant() <= now && it.toInstant() != schedule.skippedOccurrence }
            .lastOrNull()
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
            Recurrence.Once -> generateSequence(start) { it.plusDays(1) }
            is Recurrence.Weekly -> generateSequence(start) { it.plusDays(1) }
                .filter { it.dayOfWeek in recurrence.days }
            is Recurrence.Dates -> recurrence.dates.sorted().asSequence().filter { it >= start }
        }
    }

    // Enough to step over a skipped occurrence of a single-day weekly alarm (about two weeks).
    private const val MAX_CANDIDATES = 32
}
