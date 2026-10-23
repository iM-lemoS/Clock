package io.github.imlemos.clock.alarm.data

import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.model.AlarmSound
import io.github.imlemos.clock.alarm.model.GradualVolume
import io.github.imlemos.clock.alarm.model.Vibration
import io.github.imlemos.clock.core.scheduling.Recurrence
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

class AlarmMappingTest {

    private val base = Alarm(
        id = 7,
        time = TimeOfDay(6, 45),
        label = "Gym",
        zone = ZoneId.of("America/New_York"),
        skippedOccurrence = Instant.parse("2026-11-09T11:45:00Z"),
        lastTriggeredOccurrence = Instant.parse("2026-11-02T11:45:00Z"),
        snoozedUntil = Instant.parse("2026-11-02T11:50:00Z"),
        sound = AlarmSound(
            ringtoneUri = "content://media/internal/audio/media/42",
            ringtoneTitle = "Oxygen",
            vibration = Vibration.HEARTBEAT,
            gradual = GradualVolume(enabled = true, targetPercent = 70, seconds = 90),
            ringDurationSeconds = 120,
            snoozeMinutes = 10,
        ),
        layoutId = -2,
        createdAt = Instant.parse("2026-10-01T00:00:00Z"),
    )

    @Test
    fun `once alarm round-trips`() {
        assertEquals(base, base.toEntity().toAlarm())
    }

    @Test
    fun `no vibration is stored as vibrate off and read back as none`() {
        val alarm = base.copy(sound = base.sound.copy(vibration = Vibration.NONE))
        val entity = alarm.toEntity()
        assertEquals(false, entity.vibrate)
        assertEquals(alarm, entity.toAlarm())
    }

    @Test
    fun `weekly days round-trip through the bitmask`() {
        val alarm = base.copy(recurrence = Recurrence.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)))
        val entity = alarm.toEntity()
        assertEquals(1 or (1 shl 6), entity.weekDays)
        assertEquals(alarm, entity.toAlarm())
    }

    @Test
    fun `dates are stored sorted and round-trip`() {
        val alarm = base.copy(
            recurrence = Recurrence.Dates(setOf(LocalDate.parse("2026-11-17"), LocalDate.parse("2026-11-03"))),
        )
        val entity = alarm.toEntity()
        assertEquals("2026-11-03,2026-11-17", entity.dates)
        assertEquals(alarm, entity.toAlarm())
    }
}
