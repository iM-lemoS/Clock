package io.github.imlemos.clock.alarm.data

import android.util.Log
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.scheduling.AlarmScheduler
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.core.notifications.AlarmNotifications
import io.github.imlemos.clock.core.scheduling.AfterOccurrence
import io.github.imlemos.clock.core.scheduling.AlarmCalculator
import io.github.imlemos.clock.core.scheduling.AlarmLifecycle
import io.github.imlemos.clock.core.scheduling.Recurrence
import io.github.imlemos.clock.trash.TrashRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every change to an alarm goes through here, so the database, AlarmManager, notifications and the
 * trash never disagree. Lifecycle decisions come from `AlarmLifecycle` (pure, unit tested).
 */
@Singleton
class AlarmController @Inject constructor(
    private val repository: AlarmRepository,
    private val trash: TrashRepository,
    private val scheduler: AlarmScheduler,
    private val notifications: AlarmNotifications,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    /** Saves an edited or new alarm. Editing clears any snooze or skip and re-arms the alarm. */
    suspend fun save(alarm: Alarm): Alarm {
        val saved = repository.save(alarm.copy(snoozedUntil = null, skippedOccurrence = null, armedAt = clock.instant()))
        schedule(saved)
        return saved
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val alarm = repository.get(id) ?: return
        save(alarm.copy(enabled = enabled))
    }

    /** Deleted by the user: goes to the trash and is offered for undo. Returns the trash id. */
    suspend fun delete(id: Long): Long? {
        val alarm = repository.get(id) ?: return null
        unschedule(alarm.id)
        val trashId = trash.moveToTrash(alarm, automatic = false)
        settings.addPendingUndo(trashId)
        return trashId
    }

    /**
     * Brings an alarm back from the trash. One that the app removed after its last ring comes back
     * switched off: it already rang.
     */
    suspend fun restore(trashId: Long): Alarm? {
        val automatic = trash.get(trashId)?.automatic ?: return null
        val restored = trash.restore(trashId) { alarm ->
            alarm.copy(
                enabled = alarm.enabled && !automatic,
                snoozedUntil = null,
                skippedOccurrence = null,
                armedAt = clock.instant(),
            )
        } ?: return null
        settings.removePendingUndo(trashId)
        notifications.cancelDeleted(trashId)
        schedule(restored)
        return restored
    }

    /** Re-registers every alarm with AlarmManager (boot, time or zone change, app update). */
    suspend fun rescheduleAll() {
        repository.getAll().forEach { schedule(it) }
        trash.prune()
    }

    /**
     * Reports occurrences that should have rung while the phone was off or the clock jumped
     * forward, then applies the usual after-ring rules (a one-off alarm switches off, and so on).
     */
    suspend fun checkMissed() {
        val now = clock.instant()
        repository.getAll().forEach { alarm ->
            val missed = AlarmCalculator.missedOccurrence(alarm.schedule, alarm.armedAt, now, scheduler.appZone())
                ?: return@forEach
            Log.i(TAG, "Alarm ${alarm.id} missed its occurrence at $missed")
            notifications.showMissed(alarm.id, alarm.time, alarm.label)
            finishOccurrence(alarm, missed.toInstant())
        }
    }

    /**
     * An alarm fired. Records the occurrence (so it can never ring twice, even if the clock moves
     * back) and immediately schedules the following one, before anything else can fail.
     *
     * Returns the alarm to ring, or `null` if it should stay silent (deleted or disabled meanwhile).
     */
    suspend fun onFired(id: Long, occurrence: Instant, snooze: Boolean): Alarm? {
        notifications.cancelUpcoming(id)
        val alarm = repository.get(id) ?: return null
        if (!alarm.enabled) return null
        if (snooze && alarm.snoozedUntil == null) return null
        val last = listOfNotNull(alarm.lastTriggeredOccurrence, occurrence).max()
        val updated = repository.save(alarm.copy(lastTriggeredOccurrence = last, snoozedUntil = null))
        schedule(updated)
        return updated
    }

    suspend fun snooze(id: Long) {
        val alarm = repository.get(id) ?: return
        val until = clock.instant().plus(Duration.ofMinutes(alarm.snoozeMinutes.toLong()))
        val updated = repository.save(alarm.copy(snoozedUntil = until))
        schedule(updated)
    }

    /** Dismissed by the user after ringing, or stopped after ringing for its full duration. */
    suspend fun dismiss(id: Long) {
        val alarm = repository.get(id) ?: return
        finishOccurrence(alarm, alarm.lastTriggeredOccurrence ?: clock.instant())
    }

    /**
     * "Dismiss this one" from the upcoming notice (docs/PLANO.md 3.4): a recurring alarm skips only
     * that occurrence; a dates alarm counts it as done; a single-use alarm goes to the trash.
     */
    suspend fun dismissEarly(id: Long) {
        notifications.cancelUpcoming(id)
        val alarm = repository.get(id) ?: return
        val occurrence = AlarmCalculator.nextOccurrence(alarm.schedule, clock.instant(), scheduler.appZone())
            ?.toInstant() ?: return
        if (alarm.recurrence is Recurrence.Weekly && !alarm.singleUse) {
            schedule(repository.save(alarm.copy(skippedOccurrence = occurrence)))
        } else {
            finishOccurrence(alarm, occurrence)
        }
    }

    /** Toggles "Skip next" on a recurring alarm (docs/PLANO.md 3.5). */
    suspend fun setSkipNext(id: Long, skip: Boolean) {
        val alarm = repository.get(id) ?: return
        val skipped = if (skip) {
            AlarmCalculator.upcomingOccurrence(alarm.schedule, clock.instant(), scheduler.appZone())?.toInstant()
        } else {
            null
        }
        schedule(repository.save(alarm.copy(skippedOccurrence = skipped)))
    }

    /** The upcoming-notice alarm fired: refresh the notification and schedule the next notice. */
    suspend fun onNotice(id: Long) {
        repository.get(id)?.let { schedule(it) }
    }

    private suspend fun finishOccurrence(alarm: Alarm, occurrence: Instant) {
        val last = listOfNotNull(alarm.lastTriggeredOccurrence, occurrence).max()
        val done = alarm.copy(lastTriggeredOccurrence = last, snoozedUntil = null)
        when (AlarmLifecycle.afterOccurrence(done.schedule, done.singleUse, occurrence, scheduler.appZone())) {
            AfterOccurrence.KEEP -> schedule(repository.save(done))
            AfterOccurrence.DISABLE -> schedule(repository.save(done.copy(enabled = false)))
            AfterOccurrence.MOVE_TO_TRASH -> {
                unschedule(alarm.id)
                val trashId = trash.moveToTrash(done, automatic = true)
                settings.addPendingUndo(trashId)
                notifications.showDeleted(done, trashId)
                Log.i(TAG, "Alarm ${alarm.id} rang for the last time; moved to trash $trashId")
            }
        }
    }

    /** Schedules the alarm and its upcoming notice, or cancels both. */
    private suspend fun schedule(alarm: Alarm) {
        val trigger = scheduler.schedule(alarm)
        if (trigger == null || trigger.isSnooze) {
            scheduler.cancelNotice(alarm.id)
            notifications.cancelUpcoming(alarm.id)
            return
        }
        val minutes = settings.noticeMinutes.first()
        val now = clock.instant()
        if (AlarmLifecycle.isInNoticeWindow(trigger.occurrence, minutes, now)) {
            notifications.showUpcoming(alarm, trigger.occurrence)
        } else {
            notifications.cancelUpcoming(alarm.id)
        }
        val nextNotice = AlarmLifecycle.nextNotice(trigger.occurrence, minutes, now)
        if (nextNotice != null) scheduler.scheduleNotice(alarm.id, nextNotice) else scheduler.cancelNotice(alarm.id)
    }

    private fun unschedule(id: Long) {
        scheduler.cancel(id)
        notifications.cancelUpcoming(id)
    }

    private companion object {
        const val TAG = "AlarmController"
    }
}
