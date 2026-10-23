package io.github.imlemos.clock.core.scheduling

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val MIN = 60_000L
private val fiveMinutes = TimerRun(TimerStatus.IDLE, durationMillis = 5 * MIN)

class TimerMathTest {
    @Test
    fun `a running timer counts down from its start`() {
        val run = TimerMath.start(fiveMinutes, nowElapsed = 1_000, nowWall = 50_000)
        assertEquals(5 * MIN, TimerMath.remaining(run, 1_000))
        assertEquals(4 * MIN, TimerMath.remaining(run, 1_000 + MIN))
        assertEquals(0, TimerMath.remaining(run, 1_000 + 10 * MIN))
        assertTrue(TimerMath.isDue(run, 1_000 + 5 * MIN))
        assertFalse(TimerMath.isDue(run, 1_000 + 5 * MIN - 1))
    }

    @Test
    fun `pausing keeps what is left and resuming continues from there`() {
        val running = TimerMath.start(fiveMinutes, 0, 0)
        val paused = TimerMath.pause(running, 2 * MIN)
        assertEquals(TimerStatus.PAUSED, paused.status)
        assertEquals(3 * MIN, TimerMath.remaining(paused, 99 * MIN))
        val resumed = TimerMath.start(paused, 10 * MIN, 10 * MIN)
        assertEquals(10 * MIN + 3 * MIN, resumed.endElapsed)
    }

    @Test
    fun `reset returns to the full duration`() {
        val paused = TimerMath.pause(TimerMath.start(fiveMinutes, 0, 0), MIN)
        assertEquals(fiveMinutes, TimerMath.reset(paused))
    }

    @Test
    fun `plus one minute extends a running, paused or ringing timer`() {
        val running = TimerMath.start(fiveMinutes, 0, 0)
        assertEquals(6 * MIN, TimerMath.remaining(TimerMath.add(running, MIN, 0, 0), 0))
        val paused = TimerMath.pause(running, MIN)
        assertEquals(5 * MIN, TimerMath.remaining(TimerMath.add(paused, MIN, 0, 0), 0))
        val ringing = running.copy(status = TimerStatus.RINGING)
        val again = TimerMath.add(ringing, MIN, nowElapsed = 7 * MIN, nowWall = 7 * MIN)
        assertEquals(TimerStatus.RUNNING, again.status)
        assertEquals(MIN, TimerMath.remaining(again, 7 * MIN))
    }

    @Test
    fun `no timer goes past 24 hours`() {
        val almostDay = TimerRun(TimerStatus.IDLE, TimerMath.MAX_DURATION_MILLIS - MIN)
        assertEquals(TimerMath.MAX_DURATION_MILLIS, TimerMath.add(almostDay, 10 * MIN, 0, 0).remainingMillis)
        val running = TimerMath.start(almostDay, 0, 0)
        assertEquals(TimerMath.MAX_DURATION_MILLIS, TimerMath.remaining(TimerMath.add(running, 10 * MIN, 0, 0), 0))
    }

    @Test
    fun `after a reboot the end comes from the wall clock`() {
        val running = TimerMath.start(fiveMinutes, nowElapsed = 900_000, nowWall = 1_000_000)
        // Rebooted two minutes later: elapsed time starts again near zero.
        val rebuilt = TimerMath.afterReboot(running, nowElapsed = 10_000, nowWall = 1_000_000 + 2 * MIN)
        assertEquals(3 * MIN, TimerMath.remaining(rebuilt, 10_000))
    }

    @Test
    fun `a manual clock change does not move the end`() {
        val running = TimerMath.start(fiveMinutes, nowElapsed = 0, nowWall = 1_000_000)
        // The user sets the clock one hour back, one minute in.
        val moved = TimerMath.afterClockChange(running, nowElapsed = MIN, nowWall = 1_000_000 + MIN - 60 * MIN)
        assertEquals(4 * MIN, TimerMath.remaining(moved, MIN))
        assertEquals(1_000_000 + MIN - 60 * MIN + 4 * MIN, moved.endWall)
    }

    @Test
    fun `starting a ringing timer starts it over`() {
        val ringing = TimerRun(TimerStatus.RINGING, 5 * MIN, remainingMillis = 0)
        assertEquals(5 * MIN, TimerMath.remaining(TimerMath.start(ringing, 0, 0), 0))
    }
}
