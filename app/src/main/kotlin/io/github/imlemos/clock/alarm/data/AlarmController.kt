package io.github.imlemos.clock.alarm.data

import android.util.Log
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.model.AlarmGroup
import io.github.imlemos.clock.alarm.model.AlarmPatch
import io.github.imlemos.clock.alarm.scheduling.AlarmScheduler
import io.github.imlemos.clock.core.audio.RingtoneStore
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.core.notifications.AlarmNotifications
import io.github.imlemos.clock.core.scheduling.AfterOccurrence
import io.github.imlemos.clock.core.scheduling.AlarmCalculator
import io.github.imlemos.clock.core.scheduling.AlarmLifecycle
import io.github.imlemos.clock.core.scheduling.Recurrence
import io.github.imlemos.clock.trash.TrashRepository
import io.github.imlemos.clock.trash.TrashedAlarm
import io.github.imlemos.clock.trash.TrashedGroup
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
    private val ringtones: RingtoneStore,
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
     * Brings an item back from the trash: an alarm, or a group with all its alarms. An alarm the app
     * removed after its last ring comes back switched off: it already rang.
     */
    suspend fun restore(trashId: Long): List<Alarm> = when (val item = trash.get(trashId)) {
        is TrashedAlarm -> listOfNotNull(restoreAlarm(trashId, item.automatic))
        is TrashedGroup -> restoreGroup(trashId, alarmIds = null)
        null -> emptyList()
    }

    /** Restores a deleted group with the chosen alarms (`null` = all of them). */
    suspend fun restoreGroup(trashId: Long, alarmIds: Set<Long>?): List<Alarm> {
        val restored = trash.restoreGroup(trashId, alarmIds) { it.readyToRestore(automatic = false) } ?: return emptyList()
        afterRestore(trashId)
        restored.forEach { schedule(it) }
        return restored
    }

    private suspend fun restoreAlarm(trashId: Long, automatic: Boolean): Alarm? {
        val restored = trash.restoreAlarm(trashId) { it.readyToRestore(automatic) } ?: return null
        afterRestore(trashId)
        schedule(restored)
        return restored
    }

    private fun Alarm.readyToRestore(automatic: Boolean) = copy(
        enabled = enabled && !automatic,
        snoozedUntil = null,
        skippedOccurrence = null,
        armedAt = clock.instant(),
    )

    private suspend fun afterRestore(trashId: Long) {
        settings.removePendingUndo(trashId)
        notifications.cancelDeleted(trashId)
    }

    // --- Groups and batch actions (docs/PLANO.md 3.8, 3.9) ---

    suspend fun createGroup(name: String): AlarmGroup =
        repository.saveGroup(AlarmGroup(name = name.trim(), createdAt = clock.instant()))

    suspend fun renameGroup(id: Long, name: String) {
        val group = repository.getGroup(id) ?: return
        repository.saveGroup(group.copy(name = name.trim()))
    }

    /** Master switch: silences or restores the group's alarms without touching their own switches. */
    suspend fun setGroupEnabled(id: Long, enabled: Boolean) {
        val group = repository.getGroup(id) ?: return
        repository.saveGroup(group.copy(enabled = enabled))
        repository.inGroup(id).forEach { alarm ->
            // Re-arm so occurrences while the group was off are not reported as missed.
            schedule(if (enabled) repository.save(alarm.copy(armedAt = clock.instant())) else alarm)
        }
    }

    suspend fun setGroupExpanded(id: Long, expanded: Boolean) {
        val group = repository.getGroup(id) ?: return
        repository.saveGroup(group.copy(expanded = expanded))
    }

    /** Moves alarms into [groupId], or out of any group when `null`. */
    suspend fun moveToGroup(ids: Collection<Long>, groupId: Long?) {
        ids.forEach { id ->
            val alarm = repository.get(id) ?: return@forEach
            schedule(repository.save(alarm.copy(groupId = groupId, armedAt = clock.instant())))
        }
    }

    /**
     * Deletes a group: only the group (its alarms stay, ungrouped) or the group with its alarms.
     * Either way it goes to the trash and is offered for undo. Returns the trash id.
     */
    suspend fun deleteGroup(id: Long, withAlarms: Boolean): Long? {
        val group = repository.getGroup(id) ?: return null
        val members = repository.inGroup(id)
        if (withAlarms) members.forEach { unschedule(it.id) }
        val trashId = trash.moveGroupToTrash(group, withAlarms)
        // Alarms left without their (possibly disabled) group may be active again.
        if (!withAlarms) members.forEach { member -> repository.get(member.id)?.let { schedule(it) } }
        settings.addPendingUndo(trashId)
        return trashId
    }

    suspend fun setEnabled(ids: Collection<Long>, enabled: Boolean) = ids.forEach { setEnabled(it, enabled) }

    suspend fun delete(ids: Collection<Long>) = ids.forEach { delete(it) }

    /** Applies a batch edit to each alarm. */
    suspend fun applyPatch(ids: Collection<Long>, patch: AlarmPatch) {
        if (patch.isEmpty) return
        ids.forEach { id -> repository.get(id)?.let { save(patch.applyTo(it)) } }
    }

    /**
     * The first other alarm that would ring at the same moment as [alarm] (docs/PLANO.md 3.11),
     * with that moment. Used to warn when saving.
     */
    suspend fun findCollision(alarm: Alarm): Pair<Alarm, Instant>? {
        val now = clock.instant()
        val candidate = alarm.schedule.copy(enabled = true, snoozedUntil = null)
        return repository.getAll()
            .filter { it.id != alarm.id && isActive(it) }
            .firstNotNullOfOrNull { other ->
                AlarmCalculator.firstCollision(candidate, other.schedule, now, scheduler.appZone())?.let { other to it }
            }
    }

    /** An alarm rings only if both its own switch and its group's switch are on. */
    private suspend fun isActive(alarm: Alarm): Boolean =
        alarm.enabled && (alarm.groupId?.let { repository.getGroup(it)?.enabled } ?: true)

    /** Re-registers every alarm with AlarmManager (boot, time or zone change, app update). */
    suspend fun rescheduleAll() {
        val alarms = repository.getAll()
        alarms.forEach { schedule(it) }
        trash.prune()
        // Imported sound files still referenced by an alarm, the trash or the default sound.
        val inUse = (alarms + trash.allAlarms()).mapNotNull { it.sound.ringtoneUri } +
            listOfNotNull(settings.defaultSound.first().ringtoneUri)
        ringtones.deleteUnused(inUse.toSet())
    }

    /**
     * Reports occurrences that should have rung while the phone was off or the clock jumped
     * forward, then applies the usual after-ring rules (a one-off alarm switches off, and so on).
     */
    suspend fun checkMissed() {
        val now = clock.instant()
        repository.getAll().filter { isActive(it) }.forEach { alarm ->
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
        if (!isActive(alarm)) return null
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

    /** Schedules the alarm and its upcoming notice, or cancels both (also when its group is off). */
    private suspend fun schedule(alarm: Alarm) {
        val trigger = scheduler.schedule(if (isActive(alarm)) alarm else alarm.copy(enabled = false))
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
