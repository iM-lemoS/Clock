package io.github.imlemos.clock.alarm.data

import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.scheduling.AlarmScheduler
import io.github.imlemos.clock.core.scheduling.Recurrence
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every change to an alarm goes through here, so the database and AlarmManager never disagree:
 * each operation saves the alarm and then (re)schedules or cancels it.
 */
@Singleton
class AlarmController @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val clock: Clock,
) {
    /** Saves an edited or new alarm. Editing clears any snooze or skip. */
    suspend fun save(alarm: Alarm): Alarm {
        val saved = repository.save(alarm.copy(snoozedUntil = null, skippedOccurrence = null))
        scheduler.schedule(saved)
        return saved
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val alarm = repository.get(id) ?: return
        save(alarm.copy(enabled = enabled))
    }

    suspend fun delete(id: Long) {
        scheduler.cancel(id)
        repository.delete(id)
    }

    /** Re-registers every alarm with AlarmManager (boot, time or zone change, app update). */
    suspend fun rescheduleAll() {
        repository.getAll().forEach(scheduler::schedule)
    }

    /**
     * An alarm fired. Records the occurrence (so it can never ring twice, even if the clock moves
     * back) and immediately schedules the following one, before anything else can fail.
     *
     * Returns the alarm to ring, or `null` if it should stay silent (deleted or disabled meanwhile).
     */
    suspend fun onFired(id: Long, occurrence: Instant, snooze: Boolean): Alarm? {
        val alarm = repository.get(id) ?: return null
        if (!alarm.enabled) return null
        if (snooze && alarm.snoozedUntil == null) return null
        val last = listOfNotNull(alarm.lastTriggeredOccurrence, occurrence).max()
        val updated = repository.save(alarm.copy(lastTriggeredOccurrence = last, snoozedUntil = null))
        scheduler.schedule(updated)
        return updated
    }

    suspend fun snooze(id: Long) {
        val alarm = repository.get(id) ?: return
        val until = clock.instant().plus(Duration.ofMinutes(alarm.snoozeMinutes.toLong()))
        val updated = repository.save(alarm.copy(snoozedUntil = until))
        scheduler.schedule(updated)
    }

    /** Dismissed by the user, or stopped after ringing for its full duration. */
    suspend fun dismiss(id: Long) {
        val alarm = repository.get(id) ?: return
        // A one-off alarm stays in the list, switched off, until the user turns it on again.
        val enabled = alarm.enabled && alarm.recurrence != Recurrence.Once
        val updated = repository.save(alarm.copy(enabled = enabled, snoozedUntil = null))
        scheduler.schedule(updated)
    }
}
