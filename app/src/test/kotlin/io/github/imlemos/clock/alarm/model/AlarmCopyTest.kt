package io.github.imlemos.clock.alarm.model

import io.github.imlemos.clock.core.scheduling.Recurrence
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AlarmCopyTest {

    private val original = Alarm(
        id = 7,
        time = TimeOfDay(6, 45),
        recurrence = Recurrence.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
        label = "Gym",
        enabled = true,
        zone = ZoneId.of("Asia/Tokyo"),
        skippedOccurrence = Instant.parse("2026-11-09T11:45:00Z"),
        lastTriggeredOccurrence = Instant.parse("2026-11-02T11:45:00Z"),
        snoozedUntil = Instant.parse("2026-11-02T11:50:00Z"),
        sound = AlarmSound(vibration = Vibration.HEARTBEAT, ringDurationSeconds = 120, snoozeMinutes = 25),
        singleUse = true,
        groupId = 3,
        layoutId = -2,
        createdAt = Instant.parse("2026-10-01T00:00:00Z"),
        armedAt = Instant.parse("2026-10-20T00:00:00Z"),
    )
    private val now = Instant.parse("2026-10-24T12:00:00Z")

    @Test
    fun `copy is a new alarm, switched off`() {
        val copy = original.asCopy(now)
        assertEquals(0, copy.id)
        assertFalse(copy.enabled)
        assertEquals(now, copy.createdAt)
        assertEquals(now, copy.armedAt)
    }

    @Test
    fun `copy forgets what the original rang, skipped or snoozed`() {
        val copy = original.asCopy(now)
        assertNull(copy.skippedOccurrence)
        assertNull(copy.lastTriggeredOccurrence)
        assertNull(copy.snoozedUntil)
    }

    @Test
    fun `copy keeps every setting`() {
        val copy = original.asCopy(now)
        assertEquals(
            original.copy(
                id = 0,
                enabled = false,
                skippedOccurrence = null,
                lastTriggeredOccurrence = null,
                snoozedUntil = null,
                createdAt = now,
                armedAt = now,
            ),
            copy,
        )
    }
}
