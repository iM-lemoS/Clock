package io.github.imlemos.clock.core.scheduling

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val LISBON = ZoneId.of("Europe/Lisbon")
private val NEW_YORK = ZoneId.of("America/New_York")
private val TOKYO = ZoneId.of("Asia/Tokyo")

/** "2026-11-02T07:30" in [zone] as an Instant. */
private fun at(local: String, zone: ZoneId = LISBON): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()

private fun next(schedule: AlarmSchedule, now: String, zone: ZoneId = LISBON): ZonedDateTime? =
    AlarmCalculator.nextOccurrence(schedule, at(now, zone), zone)

private fun once(hour: Int, minute: Int) = AlarmSchedule(TimeOfDay(hour, minute), Recurrence.Once)

class AlarmCalculatorTest {

    @Nested
    inner class OnceRecurrence {
        @Test
        fun `rings later today when the time is still ahead`() {
            assertEquals(at("2026-11-02T07:30"), next(once(7, 30), "2026-11-02T06:00")?.toInstant())
        }

        @Test
        fun `rings tomorrow when the time already passed today`() {
            assertEquals(at("2026-11-03T07:30"), next(once(7, 30), "2026-11-02T08:00")?.toInstant())
        }

        @Test
        fun `exactly at the alarm time the next occurrence is tomorrow`() {
            assertEquals(at("2026-11-03T07:30"), next(once(7, 30), "2026-11-02T07:30")?.toInstant())
        }

        @Test
        fun `midnight alarm set late in the evening rings at midnight`() {
            assertEquals(at("2026-11-03T00:00"), next(once(0, 0), "2026-11-02T23:59")?.toInstant())
        }

        @Test
        fun `crosses the new year`() {
            assertEquals(at("2027-01-01T00:05"), next(once(0, 5), "2026-12-31T23:50")?.toInstant())
        }
    }

    @Nested
    inner class WeeklyRecurrence {
        // 2026-11-02 is a Monday.
        private fun weekly(vararg days: java.time.DayOfWeek) =
            AlarmSchedule(TimeOfDay(7, 0), Recurrence.Weekly(days.toSet()))

        @Test
        fun `rings today when today is selected and the time is ahead`() {
            assertEquals(at("2026-11-02T07:00"), next(weekly(MONDAY), "2026-11-02T06:00")?.toInstant())
        }

        @Test
        fun `jumps to the next selected day`() {
            assertEquals(at("2026-11-04T07:00"), next(weekly(MONDAY, WEDNESDAY, FRIDAY), "2026-11-02T07:01")?.toInstant())
        }

        @Test
        fun `wraps around the week`() {
            assertEquals(at("2026-11-09T07:00"), next(weekly(MONDAY), "2026-11-02T08:00")?.toInstant())
        }

        @Test
        fun `weekend only from a weekday`() {
            assertEquals(at("2026-11-07T07:00"), next(weekly(SATURDAY, SUNDAY), "2026-11-03T12:00")?.toInstant())
        }

        @Test
        fun `every day behaves like daily`() {
            val all = java.time.DayOfWeek.entries.toTypedArray()
            assertEquals(at("2026-11-03T07:00"), next(weekly(*all), "2026-11-02T07:00")?.toInstant())
        }
    }

    @Nested
    inner class DatesRecurrence {
        private fun dates(vararg iso: String) =
            AlarmSchedule(TimeOfDay(9, 0), Recurrence.Dates(iso.map(LocalDate::parse).toSet()))

        @Test
        fun `picks the earliest future date regardless of insertion order`() {
            val schedule = dates("2026-11-17", "2026-11-03", "2026-11-10")
            assertEquals(at("2026-11-03T09:00"), next(schedule, "2026-11-02T12:00")?.toInstant())
        }

        @Test
        fun `skips dates already past and today's passed time`() {
            val schedule = dates("2026-11-03", "2026-11-10", "2026-11-17")
            assertEquals(at("2026-11-10T09:00"), next(schedule, "2026-11-03T09:00")?.toInstant())
        }

        @Test
        fun `returns null after the last date`() {
            assertNull(next(dates("2026-11-03"), "2026-11-03T09:00"))
        }

        @Test
        fun `handles leap day and year change`() {
            val schedule = dates("2028-02-29", "2027-12-31")
            assertEquals(at("2027-12-31T09:00"), next(schedule, "2027-06-01T00:00")?.toInstant())
            assertEquals(at("2028-02-29T09:00"), next(schedule, "2027-12-31T09:00")?.toInstant())
        }
    }

    @Nested
    inner class SkipAndLastTriggered {
        @Test
        fun `skip next jumps exactly one occurrence`() {
            val schedule = AlarmSchedule(
                TimeOfDay(7, 0),
                Recurrence.Weekly(setOf(MONDAY)),
                skippedOccurrence = at("2026-11-09T07:00"),
            )
            assertEquals(at("2026-11-16T07:00"), next(schedule, "2026-11-02T08:00")?.toInstant())
        }

        @Test
        fun `a skip in the past has no effect`() {
            val schedule = AlarmSchedule(
                TimeOfDay(7, 0),
                Recurrence.Weekly(setOf(MONDAY)),
                skippedOccurrence = at("2026-10-26T07:00"),
            )
            assertEquals(at("2026-11-09T07:00"), next(schedule, "2026-11-02T08:00")?.toInstant())
        }

        @Test
        fun `skipping the only remaining date leaves nothing`() {
            val schedule = AlarmSchedule(
                TimeOfDay(9, 0),
                Recurrence.Dates(setOf(LocalDate.parse("2026-11-03"))),
                skippedOccurrence = at("2026-11-03T09:00"),
            )
            assertNull(next(schedule, "2026-11-02T12:00"))
        }

        @Test
        fun `an occurrence that already rang is not returned again even if the clock went back`() {
            val schedule = once(7, 0).copy(lastTriggeredOccurrence = at("2026-11-02T07:00"))
            // Clock set back to 06:59 after the alarm rang: must not ring again today.
            assertEquals(at("2026-11-03T07:00"), next(schedule, "2026-11-02T06:59")?.toInstant())
        }
    }

    @Nested
    inner class TimeZones {
        @Test
        fun `alarm zone overrides the app zone`() {
            val schedule = once(8, 0).copy(zone = NEW_YORK)
            // 13:00 in Lisbon = 08:00 in New York (both on standard time in November).
            val result = AlarmCalculator.nextOccurrence(schedule, at("2026-11-02T12:00"), LISBON)
            assertEquals(at("2026-11-02T08:00", NEW_YORK), result?.toInstant())
            assertEquals(at("2026-11-02T13:00"), result?.toInstant())
        }

        @Test
        fun `effective zone priority is alarm, then manual, then system`() {
            assertEquals(TOKYO, AlarmCalculator.effectiveZone(TOKYO, NEW_YORK, LISBON))
            assertEquals(NEW_YORK, AlarmCalculator.effectiveZone(null, NEW_YORK, LISBON))
            assertEquals(LISBON, AlarmCalculator.effectiveZone(null, null, LISBON))
        }

        @Test
        fun `follows the alarm zone's DST when the device zone has not changed yet`() {
            // US springs forward on 2027-03-14, the EU on 2027-03-28. On 2027-03-15 New York is UTC-4
            // while Lisbon is still UTC+0, so 08:00 New York is 12:00 in Lisbon, not 13:00.
            val schedule = once(8, 0).copy(zone = NEW_YORK)
            val result = AlarmCalculator.nextOccurrence(schedule, at("2027-03-15T06:00"), LISBON)
            assertEquals(at("2027-03-15T12:00"), result?.toInstant())
        }

        @Test
        fun `an alarm in a zone ahead of the device can ring on the device's previous date`() {
            // 07:00 in Tokyo on 2026-11-03 is 22:00 on 2026-11-02 in Lisbon.
            val schedule = once(7, 0).copy(zone = TOKYO)
            val result = AlarmCalculator.nextOccurrence(schedule, at("2026-11-02T21:00"), LISBON)
            assertEquals(at("2026-11-02T22:00"), result?.toInstant())
        }

        @Test
        fun `follows the alarm zone's DST end while the device zone already ended it`() {
            // The EU falls back on 2026-10-25, the US on 2026-11-01. In between New York is UTC-4 and
            // Lisbon UTC+0, so 08:00 New York is 12:00 in Lisbon; from 2026-11-02 it is 13:00 again.
            val schedule = AlarmSchedule(TimeOfDay(8, 0), Recurrence.Weekly(setOf(MONDAY))).copy(zone = NEW_YORK)
            assertEquals(at("2026-10-26T12:00"), AlarmCalculator.nextOccurrence(schedule, at("2026-10-26T06:00"), LISBON)?.toInstant())
            assertEquals(at("2026-11-02T13:00"), AlarmCalculator.nextOccurrence(schedule, at("2026-10-27T06:00"), LISBON)?.toInstant())
        }

        @Test
        fun `weekdays are the alarm zone's weekdays`() {
            // Monday 07:00 in Tokyo is Sunday 22:00 in Lisbon; Sunday evening in Lisbon it rings.
            val schedule = AlarmSchedule(TimeOfDay(7, 0), Recurrence.Weekly(setOf(MONDAY))).copy(zone = TOKYO)
            val result = AlarmCalculator.nextOccurrence(schedule, at("2026-11-01T12:00"), LISBON)
            assertEquals(at("2026-11-01T22:00"), result?.toInstant())
            assertEquals(MONDAY, result?.dayOfWeek)
        }

        @Test
        fun `dates are the alarm zone's dates`() {
            val schedule = AlarmSchedule(TimeOfDay(7, 0), Recurrence.Dates(setOf(LocalDate.parse("2026-11-03")))).copy(zone = TOKYO)
            val result = AlarmCalculator.nextOccurrence(schedule, at("2026-11-01T12:00"), LISBON)
            assertEquals(at("2026-11-02T22:00"), result?.toInstant())
        }

        @Test
        fun `the alarm zone's spring-forward gap applies, not the device's`() {
            // New York skips 02:00-03:00 on 2027-03-14; Lisbon has no transition that day.
            val schedule = once(2, 30).copy(zone = NEW_YORK)
            val result = AlarmCalculator.nextOccurrence(schedule, at("2027-03-14T00:00"), LISBON)
            assertEquals(Instant.parse("2027-03-14T07:00:00Z"), result?.toInstant())
        }

        @Test
        fun `a manual app zone is used for alarms without their own zone`() {
            // A phone in New York with the app set to Lisbon: 07:30 is Lisbon time.
            val manual = AlarmCalculator.effectiveZone(null, LISBON, NEW_YORK)
            val result = AlarmCalculator.nextOccurrence(once(7, 30), at("2026-11-02T00:00", NEW_YORK), manual)
            assertEquals(at("2026-11-02T07:30"), result?.toInstant())
        }

        @Test
        fun `a missed occurrence is found in the alarm zone`() {
            val schedule = once(8, 0).copy(zone = NEW_YORK)
            val result = AlarmCalculator.missedOccurrence(schedule, at("2026-11-02T12:00"), at("2026-11-02T14:00"), LISBON)
            assertEquals(at("2026-11-02T13:00"), result?.toInstant())
        }

        @Test
        fun `shows the local time only when the wall-clock times differ`() {
            val instant = at("2026-11-02T13:00")
            assertEquals(true, AlarmCalculator.showsLocalTime(instant, NEW_YORK, LISBON))
            assertEquals(false, AlarmCalculator.showsLocalTime(instant, ZoneId.of("Europe/London"), LISBON))
            assertEquals(false, AlarmCalculator.showsLocalTime(instant, LISBON, LISBON))
        }
    }

    @Nested
    inner class DaylightSaving {
        // Lisbon springs forward on 2027-03-28 at 01:00 -> 02:00, and falls back on 2026-10-25
        // at 02:00 -> 01:00.

        @Test
        fun `a time inside the spring-forward gap rings at the transition`() {
            val result = next(once(1, 30), "2027-03-28T00:00")
            assertEquals(Instant.parse("2027-03-28T01:00:00Z"), result?.toInstant())
            assertEquals("02:00", result?.toLocalTime().toString())
        }

        @Test
        fun `a time inside the fall-back overlap rings once, at the first occurrence`() {
            val result = next(once(1, 30), "2026-10-25T00:00")
            assertEquals(Instant.parse("2026-10-25T00:30:00Z"), result?.toInstant())
            assertEquals(ZoneOffset.ofHours(1), result?.offset)
        }

        @Test
        fun `after ringing in the overlap the repeated hour does not ring again`() {
            val first = Instant.parse("2026-10-25T00:30:00Z")
            val schedule = AlarmSchedule(TimeOfDay(1, 30), Recurrence.Weekly(java.time.DayOfWeek.entries.toSet()))
                .copy(lastTriggeredOccurrence = first)
            // 01:45 in the second (standard time) pass of the repeated hour.
            val result = AlarmCalculator.nextOccurrence(schedule, Instant.parse("2026-10-25T01:45:00Z"), LISBON)
            assertEquals(at("2026-10-26T01:30"), result?.toInstant())
        }

        @Test
        fun `a regular time on a DST day keeps its wall-clock time`() {
            assertEquals("07:00", next(once(7, 0), "2027-03-28T00:00")?.toLocalTime().toString())
            assertEquals(Instant.parse("2027-03-28T06:00:00Z"), next(once(7, 0), "2027-03-28T00:00")?.toInstant())
        }
    }

    @Nested
    inner class Triggers {
        private val now = at("2026-11-02T07:00")

        @Test
        fun `disabled alarms never trigger`() {
            assertNull(AlarmCalculator.nextTrigger(once(8, 0).copy(enabled = false), now, LISBON))
        }

        @Test
        fun `an active snooze triggers before the next occurrence`() {
            val schedule = once(7, 0).copy(
                lastTriggeredOccurrence = now,
                snoozedUntil = now.plusSeconds(300),
            )
            assertEquals(
                Trigger(at = now.plusSeconds(300), occurrence = now, isSnooze = true),
                AlarmCalculator.nextTrigger(schedule, now.plusSeconds(10), LISBON),
            )
        }

        @Test
        fun `an expired snooze is ignored`() {
            val schedule = once(7, 0).copy(lastTriggeredOccurrence = now, snoozedUntil = now.plusSeconds(300))
            val trigger = AlarmCalculator.nextTrigger(schedule, now.plusSeconds(600), LISBON)
            assertEquals(Trigger(at("2026-11-03T07:00"), at("2026-11-03T07:00"), isSnooze = false), trigger)
        }

        @Test
        fun `a snooze for a dates alarm still fires after its last date`() {
            val last = at("2026-11-03T09:00")
            val schedule = AlarmSchedule(
                TimeOfDay(9, 0),
                Recurrence.Dates(setOf(LocalDate.parse("2026-11-03"))),
                lastTriggeredOccurrence = last,
                snoozedUntil = last.plusSeconds(600),
            )
            assertEquals(
                Trigger(last.plusSeconds(600), last, isSnooze = true),
                AlarmCalculator.nextTrigger(schedule, last.plusSeconds(30), LISBON),
            )
        }
    }

    @Nested
    inner class UpcomingAndMissed {
        private val weeklyMonday = AlarmSchedule(TimeOfDay(7, 0), Recurrence.Weekly(setOf(MONDAY)))

        @Test
        fun `upcoming ignores the current skip so it can be toggled`() {
            val skipped = weeklyMonday.copy(skippedOccurrence = at("2026-11-09T07:00"))
            assertEquals(at("2026-11-09T07:00"), AlarmCalculator.upcomingOccurrence(skipped, at("2026-11-02T08:00"), LISBON)?.toInstant())
        }

        @Test
        fun `an occurrence while the phone was off is missed`() {
            val missed = AlarmCalculator.missedOccurrence(
                weeklyMonday,
                armedAt = at("2026-11-01T12:00"),
                now = at("2026-11-02T07:20"),
                appZone = LISBON,
            )
            assertEquals(at("2026-11-02T07:00"), missed?.toInstant())
        }

        @Test
        fun `occurrences before the alarm was armed are not missed`() {
            val missed = AlarmCalculator.missedOccurrence(
                weeklyMonday,
                armedAt = at("2026-11-02T07:10"),
                now = at("2026-11-02T07:20"),
                appZone = LISBON,
            )
            assertNull(missed)
        }

        @Test
        fun `an occurrence that already rang is not missed`() {
            val rang = weeklyMonday.copy(lastTriggeredOccurrence = at("2026-11-02T07:00"))
            assertNull(AlarmCalculator.missedOccurrence(rang, at("2026-11-01T12:00"), at("2026-11-02T07:20"), LISBON))
        }

        @Test
        fun `after several days off only the latest occurrence is reported`() {
            val daily = AlarmSchedule(TimeOfDay(7, 0), Recurrence.Weekly(java.time.DayOfWeek.entries.toSet()))
            val missed = AlarmCalculator.missedOccurrence(daily, at("2026-10-30T12:00"), at("2026-11-02T09:00"), LISBON)
            assertEquals(at("2026-11-02T07:00"), missed?.toInstant())
        }

        @Test
        fun `disabled alarms are never missed`() {
            assertNull(
                AlarmCalculator.missedOccurrence(
                    weeklyMonday.copy(enabled = false),
                    at("2026-11-01T12:00"),
                    at("2026-11-02T07:20"),
                    LISBON,
                ),
            )
        }
    }

    @Nested
    inner class Collisions {
        private val now = at("2026-11-02T06:00") // a Monday

        @Test
        fun `two one-off alarms at the same time collide at their next ring`() {
            assertEquals(at("2026-11-02T07:30"), AlarmCalculator.firstCollision(once(7, 30), once(7, 30), now, LISBON))
        }

        @Test
        fun `different times never collide`() {
            assertNull(AlarmCalculator.firstCollision(once(7, 30), once(7, 31), now, LISBON))
        }

        @Test
        fun `a weekly alarm meets a dates alarm on the matching weekday`() {
            val weekly = AlarmSchedule(TimeOfDay(7, 0), Recurrence.Weekly(setOf(FRIDAY)))
            val dates = AlarmSchedule(TimeOfDay(7, 0), Recurrence.Dates(setOf(LocalDate.parse("2026-11-04"), LocalDate.parse("2026-11-13"))))
            assertEquals(at("2026-11-13T07:00"), AlarmCalculator.firstCollision(weekly, dates, now, LISBON))
        }

        @Test
        fun `weekly alarms on different days never collide`() {
            val monday = AlarmSchedule(TimeOfDay(7, 0), Recurrence.Weekly(setOf(MONDAY)))
            val friday = AlarmSchedule(TimeOfDay(7, 0), Recurrence.Weekly(setOf(FRIDAY)))
            assertNull(AlarmCalculator.firstCollision(monday, friday, now, LISBON))
        }

        @Test
        fun `alarms in different zones collide when they ring at the same instant`() {
            // 08:00 in New York is 13:00 in Lisbon in November.
            val newYork = once(8, 0).copy(zone = NEW_YORK)
            assertEquals(at("2026-11-02T13:00"), AlarmCalculator.firstCollision(newYork, once(13, 0), now, LISBON))
        }

        @Test
        fun `a disabled alarm never collides`() {
            assertNull(AlarmCalculator.firstCollision(once(7, 30), once(7, 30).copy(enabled = false), now, LISBON))
        }

        @Test
        fun `occurrences lists upcoming rings in order`() {
            val weekend = AlarmSchedule(TimeOfDay(9, 0), Recurrence.Weekly(setOf(SATURDAY, SUNDAY)))
            assertEquals(
                listOf(at("2026-11-07T09:00"), at("2026-11-08T09:00"), at("2026-11-14T09:00")),
                AlarmCalculator.occurrences(weekend, now, LISBON, 3),
            )
        }
    }
}
