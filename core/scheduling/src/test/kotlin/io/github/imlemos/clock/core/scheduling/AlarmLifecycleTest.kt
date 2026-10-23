package io.github.imlemos.clock.core.scheduling

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val ZONE = ZoneId.of("Europe/Lisbon")

private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(ZONE).toInstant()

class AlarmLifecycleTest {

    private val once = AlarmSchedule(TimeOfDay(7, 0), Recurrence.Once)
    private val weekly = AlarmSchedule(TimeOfDay(7, 0), Recurrence.Weekly(setOf(DayOfWeek.MONDAY)))
    private fun dates(vararg iso: String) =
        AlarmSchedule(TimeOfDay(7, 0), Recurrence.Dates(iso.map(LocalDate::parse).toSet()))

    @Nested
    inner class AfterRinging {
        @Test
        fun `one-off alarms are switched off`() {
            assertEquals(AfterOccurrence.DISABLE, AlarmLifecycle.afterOccurrence(once, false, at("2026-11-02T07:00"), ZONE))
        }

        @Test
        fun `weekly alarms stay on`() {
            assertEquals(AfterOccurrence.KEEP, AlarmLifecycle.afterOccurrence(weekly, false, at("2026-11-02T07:00"), ZONE))
        }

        @Test
        fun `dates alarms stay on until their last date, then go to the trash`() {
            val schedule = dates("2026-11-03", "2026-11-10")
            assertEquals(AfterOccurrence.KEEP, AlarmLifecycle.afterOccurrence(schedule, false, at("2026-11-03T07:00"), ZONE))
            assertEquals(AfterOccurrence.MOVE_TO_TRASH, AlarmLifecycle.afterOccurrence(schedule, false, at("2026-11-10T07:00"), ZONE))
        }

        @Test
        fun `single use always goes to the trash`() {
            assertEquals(AfterOccurrence.MOVE_TO_TRASH, AlarmLifecycle.afterOccurrence(weekly, true, at("2026-11-02T07:00"), ZONE))
            assertEquals(AfterOccurrence.MOVE_TO_TRASH, AlarmLifecycle.afterOccurrence(once, true, at("2026-11-02T07:00"), ZONE))
        }

        @Test
        fun `a skip on the last date does not keep a dates alarm alive`() {
            val schedule = dates("2026-11-03", "2026-11-10").copy(skippedOccurrence = at("2026-11-10T07:00"))
            assertEquals(AfterOccurrence.MOVE_TO_TRASH, AlarmLifecycle.afterOccurrence(schedule, false, at("2026-11-10T07:00"), ZONE))
        }
    }

    @Nested
    inner class Notices {
        private val occurrence = at("2026-11-02T07:00")

        @Test
        fun `next notice is the earliest one still ahead`() {
            val notices = setOf(1440, 60, 30, 5)
            assertEquals(at("2026-11-01T07:00"), AlarmLifecycle.nextNotice(occurrence, notices, at("2026-10-31T12:00")))
            assertEquals(at("2026-11-02T06:30"), AlarmLifecycle.nextNotice(occurrence, notices, at("2026-11-02T06:10")))
            assertEquals(at("2026-11-02T06:55"), AlarmLifecycle.nextNotice(occurrence, notices, at("2026-11-02T06:30")))
            assertNull(AlarmLifecycle.nextNotice(occurrence, notices, at("2026-11-02T06:56")))
        }

        @Test
        fun `notice window spans from the earliest notice to the alarm`() {
            val notices = setOf(30, 5)
            assertFalse(AlarmLifecycle.isInNoticeWindow(occurrence, notices, at("2026-11-02T06:29")))
            assertTrue(AlarmLifecycle.isInNoticeWindow(occurrence, notices, at("2026-11-02T06:30")))
            assertTrue(AlarmLifecycle.isInNoticeWindow(occurrence, notices, at("2026-11-02T06:59")))
            assertFalse(AlarmLifecycle.isInNoticeWindow(occurrence, notices, at("2026-11-02T07:00")))
        }

        @Test
        fun `no notices configured means no notification`() {
            assertNull(AlarmLifecycle.nextNotice(occurrence, emptySet(), at("2026-11-02T06:00")))
            assertFalse(AlarmLifecycle.isInNoticeWindow(occurrence, emptySet(), at("2026-11-02T06:59")))
        }
    }
}
