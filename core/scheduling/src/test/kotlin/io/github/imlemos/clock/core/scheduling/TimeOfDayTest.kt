package io.github.imlemos.clock.core.scheduling

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeOfDayTest {

    @ParameterizedTest
    @CsvSource("0, 0, 0", "7, 30, 450", "12, 0, 720", "23, 59, 1439")
    fun `minuteOfDay round-trips`(hour: Int, minute: Int, minuteOfDay: Int) {
        val time = TimeOfDay(hour, minute)
        assertEquals(minuteOfDay, time.minuteOfDay)
        assertEquals(time, TimeOfDay.ofMinuteOfDay(minuteOfDay))
    }

    @ParameterizedTest
    @CsvSource("-1, 0", "24, 0", "0, -1", "0, 60")
    fun `rejects out of range values`(hour: Int, minute: Int) {
        assertThrows<IllegalArgumentException> { TimeOfDay(hour, minute) }
    }

    @Test
    fun `rejects out of range minute of day`() {
        assertThrows<IllegalArgumentException> { TimeOfDay.ofMinuteOfDay(1440) }
        assertThrows<IllegalArgumentException> { TimeOfDay.ofMinuteOfDay(-1) }
    }

    @Test
    fun `orders by time of day`() {
        assertTrue(TimeOfDay(6, 59) < TimeOfDay(7, 0))
        assertTrue(TimeOfDay(23, 59) > TimeOfDay(0, 0))
    }
}
