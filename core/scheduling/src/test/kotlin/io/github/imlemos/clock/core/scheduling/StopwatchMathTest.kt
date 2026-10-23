package io.github.imlemos.clock.core.scheduling

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StopwatchMathTest {
    @Test
    fun `counts while running and holds while paused`() {
        val running = StopwatchMath.start(StopwatchRun(), nowElapsed = 1_000, nowWall = 0)
        assertEquals(500, StopwatchMath.elapsed(running, 1_500))
        val paused = StopwatchMath.pause(running, 2_000)
        assertEquals(1_000, StopwatchMath.elapsed(paused, 9_999))
        val resumed = StopwatchMath.start(paused, 10_000, 0)
        assertEquals(1_250, StopwatchMath.elapsed(resumed, 10_250))
    }

    @Test
    fun `laps have their own durations, newest first`() {
        var run = StopwatchMath.start(StopwatchRun(), 0, 0)
        run = StopwatchMath.lap(run, 1_000)
        run = StopwatchMath.lap(run, 3_500)
        run = StopwatchMath.lap(run, 4_000)
        assertEquals(
            listOf(Lap(3, 500, 4_000), Lap(2, 2_500, 3_500), Lap(1, 1_000, 1_000)),
            StopwatchMath.laps(run),
        )
        assertEquals(3 to 2, StopwatchMath.extremes(run))
    }

    @Test
    fun `fastest and slowest need two laps`() {
        val run = StopwatchMath.lap(StopwatchMath.start(StopwatchRun(), 0, 0), 1_000)
        assertNull(StopwatchMath.extremes(run))
    }

    @Test
    fun `laps only while running, and reset clears everything`() {
        val paused = StopwatchMath.pause(StopwatchMath.start(StopwatchRun(), 0, 0), 1_000)
        assertEquals(paused, StopwatchMath.lap(paused, 2_000))
        assertEquals(StopwatchRun(), StopwatchMath.reset())
    }

    @Test
    fun `survives a reboot and a clock change`() {
        val running = StopwatchMath.start(StopwatchRun(), nowElapsed = 500_000, nowWall = 1_000_000)
        val rebooted = StopwatchMath.afterReboot(running, nowElapsed = 5_000, nowWall = 1_060_000)
        assertEquals(60_000, StopwatchMath.elapsed(rebooted, 5_000))
        val changed = StopwatchMath.afterClockChange(running, nowElapsed = 560_000, nowWall = 0)
        assertEquals(-60_000, changed.startedWall)
    }
}
