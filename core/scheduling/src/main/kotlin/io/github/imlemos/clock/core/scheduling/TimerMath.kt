package io.github.imlemos.clock.core.scheduling

/** What a timer is doing (docs/PLANO.md 4). */
public enum class TimerStatus { IDLE, RUNNING, PAUSED, RINGING }

/**
 * A timer's countdown. While running it ends at [endElapsed] (milliseconds of
 * `elapsedRealtime`, immune to manual clock changes) and at [endWall] (epoch milliseconds, the
 * only reference that survives a reboot). Otherwise [remainingMillis] is what is left.
 */
public data class TimerRun(
    val status: TimerStatus,
    val durationMillis: Long,
    val remainingMillis: Long = durationMillis,
    val endElapsed: Long? = null,
    val endWall: Long? = null,
)

/** Timer arithmetic; Android-free so every rule is unit tested (TimerMathTest). */
public object TimerMath {
    public const val MAX_DURATION_MILLIS: Long = 24L * 60 * 60 * 1000

    /** Time left at [nowElapsed]; never negative. */
    public fun remaining(run: TimerRun, nowElapsed: Long): Long = when (run.status) {
        TimerStatus.RUNNING -> ((run.endElapsed ?: nowElapsed) - nowElapsed).coerceAtLeast(0)
        TimerStatus.RINGING -> 0
        else -> run.remainingMillis.coerceAtLeast(0)
    }

    /** Starts or resumes from what is left (a finished or ringing timer starts over). */
    public fun start(run: TimerRun, nowElapsed: Long, nowWall: Long): TimerRun {
        val left = when (run.status) {
            TimerStatus.RINGING -> run.durationMillis
            TimerStatus.RUNNING -> return run
            else -> run.remainingMillis.takeIf { it > 0 } ?: run.durationMillis
        }
        return run.copy(status = TimerStatus.RUNNING, remainingMillis = left, endElapsed = nowElapsed + left, endWall = nowWall + left)
    }

    public fun pause(run: TimerRun, nowElapsed: Long): TimerRun {
        if (run.status != TimerStatus.RUNNING) return run
        return run.copy(status = TimerStatus.PAUSED, remainingMillis = remaining(run, nowElapsed), endElapsed = null, endWall = null)
    }

    /** Back to the full duration, stopped. */
    public fun reset(run: TimerRun): TimerRun =
        run.copy(status = TimerStatus.IDLE, remainingMillis = run.durationMillis, endElapsed = null, endWall = null)

    /**
     * Adds [millis] ("+1 min"). A ringing timer restarts with just that time. Never more than
     * [MAX_DURATION_MILLIS] left.
     */
    public fun add(run: TimerRun, millis: Long, nowElapsed: Long, nowWall: Long): TimerRun = when (run.status) {
        TimerStatus.RUNNING -> {
            val extra = millis.coerceAtMost(MAX_DURATION_MILLIS - remaining(run, nowElapsed)).coerceAtLeast(0)
            run.copy(endElapsed = run.endElapsed?.plus(extra), endWall = run.endWall?.plus(extra))
        }
        TimerStatus.RINGING -> start(run.copy(status = TimerStatus.PAUSED, remainingMillis = millis), nowElapsed, nowWall)
        else -> run.copy(remainingMillis = (run.remainingMillis + millis).coerceAtMost(MAX_DURATION_MILLIS))
    }

    /** Whether a running timer has reached zero. */
    public fun isDue(run: TimerRun, nowElapsed: Long): Boolean =
        run.status == TimerStatus.RUNNING && remaining(run, nowElapsed) == 0L

    /** After a reboot `elapsedRealtime` restarts from zero: rebuild the end from the wall clock. */
    public fun afterReboot(run: TimerRun, nowElapsed: Long, nowWall: Long): TimerRun {
        if (run.status != TimerStatus.RUNNING || run.endWall == null) return run
        return run.copy(endElapsed = nowElapsed + (run.endWall - nowWall))
    }

    /** After a manual clock change the elapsed end still holds: move the wall-clock end with it. */
    public fun afterClockChange(run: TimerRun, nowElapsed: Long, nowWall: Long): TimerRun {
        if (run.status != TimerStatus.RUNNING || run.endElapsed == null) return run
        return run.copy(endWall = nowWall + (run.endElapsed - nowElapsed))
    }
}
