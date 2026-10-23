package io.github.imlemos.clock.core.scheduling

import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val LISBON = ZoneId.of("Europe/Lisbon")
private val NEW_YORK = ZoneId.of("America/New_York")
private val TOKYO = ZoneId.of("Asia/Tokyo")
private val INDIA = ZoneId.of("Asia/Kolkata")

class WorldClockMathTest {
    @Test
    fun `differences follow both sides' daylight saving time`() {
        // Between the EU (25 Oct) and US (1 Nov) changes New York is 4 h behind, otherwise 5.
        assertEquals(-4 * 60, WorldClockMath.differenceMinutes(NEW_YORK, LISBON, Instant.parse("2026-10-27T12:00:00Z")))
        assertEquals(-5 * 60, WorldClockMath.differenceMinutes(NEW_YORK, LISBON, Instant.parse("2026-11-03T12:00:00Z")))
        assertEquals(9 * 60, WorldClockMath.differenceMinutes(TOKYO, LISBON, Instant.parse("2026-11-03T12:00:00Z")))
    }

    @Test
    fun `half-hour zones keep their minutes`() {
        assertEquals(5 * 60 + 30, WorldClockMath.differenceMinutes(INDIA, LISBON, Instant.parse("2026-12-01T12:00:00Z")))
    }

    @Test
    fun `the date can be yesterday or tomorrow`() {
        val lateInLisbon = Instant.parse("2026-11-03T23:30:00Z")
        assertEquals(1, WorldClockMath.dayOffset(TOKYO, LISBON, lateInLisbon))
        assertEquals(0, WorldClockMath.dayOffset(NEW_YORK, LISBON, lateInLisbon))
        val earlyInLisbon = Instant.parse("2026-11-03T01:00:00Z")
        assertEquals(-1, WorldClockMath.dayOffset(NEW_YORK, LISBON, earlyInLisbon))
    }

    @Test
    fun `day runs from 7 to 19 local time`() {
        assertTrue(WorldClockMath.isDaytime(LISBON, Instant.parse("2026-11-03T07:00:00Z")))
        assertFalse(WorldClockMath.isDaytime(LISBON, Instant.parse("2026-11-03T19:00:00Z")))
        assertFalse(WorldClockMath.isDaytime(TOKYO, Instant.parse("2026-11-03T12:00:00Z")))
    }
}
