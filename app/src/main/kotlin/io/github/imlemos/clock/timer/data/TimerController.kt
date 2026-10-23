package io.github.imlemos.clock.timer.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.imlemos.clock.core.scheduling.TimerMath
import io.github.imlemos.clock.core.scheduling.TimerRun
import io.github.imlemos.clock.core.scheduling.TimerStatus
import io.github.imlemos.clock.core.time.BootDetector
import io.github.imlemos.clock.core.time.ElapsedClock
import io.github.imlemos.clock.timer.TimerNotifications
import io.github.imlemos.clock.timer.ringing.TimerRingingService
import java.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every timer change goes through here (docs/PLANO.md 4): the database, the scheduled end, the
 * ongoing notifications and the ringing service stay in step.
 */
@Singleton
class TimerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TimerRepository,
    private val scheduler: TimerScheduler,
    private val notifications: TimerNotifications,
    private val clock: Clock,
    private val elapsed: ElapsedClock,
    private val boot: BootDetector,
) {
    private val resyncLock = Mutex()

    /** A new stopped timer. Returns its id. */
    suspend fun create(name: String, durationMillis: Long): Long {
        val duration = durationMillis.coerceIn(1_000, TimerMath.MAX_DURATION_MILLIS)
        return repository.save(Timer(name = name.trim(), run = TimerRun(TimerStatus.IDLE, duration), createdAt = clock.instant())).id
    }

    /** Changes name and duration; a running timer is reset to the new duration. */
    suspend fun edit(id: Long, name: String, durationMillis: Long) {
        val timer = repository.get(id) ?: return
        val duration = durationMillis.coerceIn(1_000, TimerMath.MAX_DURATION_MILLIS)
        val run = if (duration == timer.run.durationMillis) timer.run else TimerMath.reset(timer.run.copy(durationMillis = duration))
        update(timer.copy(name = name.trim(), run = run))
    }

    suspend fun delete(id: Long) {
        scheduler.cancel(id)
        repository.delete(id)
        TimerRingingService.stop(context, id)
        refreshNotifications()
    }

    suspend fun start(id: Long) = change(id) { TimerMath.start(it, elapsed.millis(), clock.millis()) }

    suspend fun pause(id: Long) = change(id) { TimerMath.pause(it, elapsed.millis()) }

    suspend fun reset(id: Long) = change(id) { TimerMath.reset(it) }

    /** "+1 min": also restarts a ringing timer for one more minute. */
    suspend fun addMinute(id: Long) = change(id) { TimerMath.add(it, 60_000, elapsed.millis(), clock.millis()) }

    /**
     * The scheduled end arrived. Rings only if the timer is still running and really at zero (it
     * may have been paused or extended since).
     */
    suspend fun onFired(id: Long) {
        val timer = repository.get(id) ?: return
        if (timer.status != TimerStatus.RUNNING) return
        if (!TimerMath.isDue(timer.run, elapsed.millis() + EARLY_TOLERANCE_MS)) {
            update(timer)
            return
        }
        ring(timer)
    }

    /** The user stopped a ringing timer: it is ready to start again from its full duration. */
    suspend fun stopRinging(id: Long) {
        val timer = repository.get(id) ?: return
        if (timer.status == TimerStatus.RINGING) update(timer.copy(run = TimerMath.reset(timer.run), ringingSince = null))
    }

    /**
     * Brings running timers back in line after a reboot, a clock change or a fresh process start,
     * rings those that ended meanwhile, schedules the rest, and resumes ringing any timer whose
     * process died while it rang. Whether the phone rebooted comes from [BootDetector].
     */
    suspend fun resync() = resyncLock.withLock {
        // App start and boot broadcasts can arrive together: one at a time, each on fresh data.
        val afterBoot = boot.rebootedSinceLastCheck(BOOT_CONSUMER)
        val nowElapsed = elapsed.millis()
        val nowWall = clock.millis()
        repository.getAll().forEach { timer ->
            if (timer.status == TimerStatus.RINGING) {
                TimerRingingService.start(context, timer.id)
                return@forEach
            }
            val run = if (afterBoot) TimerMath.afterReboot(timer.run, nowElapsed, nowWall) else TimerMath.afterClockChange(timer.run, nowElapsed, nowWall)
            val fixed = timer.copy(run = run)
            if (TimerMath.isDue(fixed.run, nowElapsed)) ring(fixed) else repository.save(fixed).also { schedule(it) }
        }
        refreshNotifications()
    }

    private suspend fun ring(timer: Timer) {
        val ringing = timer.copy(run = timer.run.copy(status = TimerStatus.RINGING, remainingMillis = 0, endElapsed = null, endWall = null), ringingSince = clock.instant())
        repository.save(ringing)
        scheduler.cancel(timer.id)
        refreshNotifications()
        Log.i(TAG, "Timer ${timer.id} finished")
        TimerRingingService.start(context, timer.id)
    }

    private suspend fun change(id: Long, transform: (TimerRun) -> TimerRun) {
        val timer = repository.get(id) ?: return
        val wasRinging = timer.status == TimerStatus.RINGING
        val updated = timer.copy(run = transform(timer.run))
        update(if (updated.status == TimerStatus.RINGING) updated else updated.copy(ringingSince = null))
        if (wasRinging && updated.status != TimerStatus.RINGING) TimerRingingService.stop(context, id)
    }

    private suspend fun update(timer: Timer) {
        repository.save(timer)
        schedule(timer)
        refreshNotifications()
    }

    private fun schedule(timer: Timer) {
        val end = timer.run.endElapsed
        if (timer.status == TimerStatus.RUNNING && end != null) scheduler.schedule(timer.id, end) else scheduler.cancel(timer.id)
    }

    private suspend fun refreshNotifications() = notifications.showRunning(repository.getAll())

    private companion object {
        const val TAG = "TimerController"
        const val BOOT_CONSUMER = "timers"

        /** AlarmManager may deliver a few milliseconds early; still ring. */
        const val EARLY_TOLERANCE_MS = 1_000L
    }
}
