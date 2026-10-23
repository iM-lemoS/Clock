package io.github.imlemos.clock.core.scheduling

/**
 * The stopwatch (docs/PLANO.md 5): [accumulatedMillis] counted before the current run, which (if
 * [running]) started at [startedElapsed] (`elapsedRealtime`) and [startedWall] (epoch, for
 * reboots). [laps] are the total elapsed times at which each lap was taken.
 */
public data class StopwatchRun(
    val running: Boolean = false,
    val accumulatedMillis: Long = 0,
    val startedElapsed: Long? = null,
    val startedWall: Long? = null,
    val laps: List<Long> = emptyList(),
)

/** One lap: its number (from 1), its own duration and the total at its end. */
public data class Lap(val number: Int, val durationMillis: Long, val totalMillis: Long)

/** Stopwatch arithmetic; Android-free and unit tested (StopwatchMathTest). */
public object StopwatchMath {

    public fun elapsed(run: StopwatchRun, nowElapsed: Long): Long =
        run.accumulatedMillis + if (run.running && run.startedElapsed != null) (nowElapsed - run.startedElapsed).coerceAtLeast(0) else 0

    public fun start(run: StopwatchRun, nowElapsed: Long, nowWall: Long): StopwatchRun =
        if (run.running) run else run.copy(running = true, startedElapsed = nowElapsed, startedWall = nowWall)

    public fun pause(run: StopwatchRun, nowElapsed: Long): StopwatchRun =
        if (!run.running) run else run.copy(running = false, accumulatedMillis = elapsed(run, nowElapsed), startedElapsed = null, startedWall = null)

    public fun reset(): StopwatchRun = StopwatchRun()

    /** Records a lap at the current total (only while running). */
    public fun lap(run: StopwatchRun, nowElapsed: Long): StopwatchRun =
        if (!run.running) run else run.copy(laps = run.laps + elapsed(run, nowElapsed))

    /** Laps newest first, with their own durations. */
    public fun laps(run: StopwatchRun): List<Lap> =
        run.laps.mapIndexed { index, total -> Lap(index + 1, total - (run.laps.getOrNull(index - 1) ?: 0L), total) }.reversed()

    /** Numbers of the fastest and slowest laps, once there are at least two to compare. */
    public fun extremes(run: StopwatchRun): Pair<Int, Int>? {
        val laps = laps(run)
        if (laps.size < 2) return null
        return laps.minBy { it.durationMillis }.number to laps.maxBy { it.durationMillis }.number
    }

    /** After a reboot, rebuild the running start from the wall clock. */
    public fun afterReboot(run: StopwatchRun, nowElapsed: Long, nowWall: Long): StopwatchRun {
        if (!run.running || run.startedWall == null) return run
        return run.copy(startedElapsed = nowElapsed - (nowWall - run.startedWall))
    }

    /** After a manual clock change, keep the wall-clock start consistent with the elapsed one. */
    public fun afterClockChange(run: StopwatchRun, nowElapsed: Long, nowWall: Long): StopwatchRun {
        if (!run.running || run.startedElapsed == null) return run
        return run.copy(startedWall = nowWall - (nowElapsed - run.startedElapsed))
    }
}
