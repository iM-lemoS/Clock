package io.github.imlemos.clock.alarm.model

import io.github.imlemos.clock.core.scheduling.AlarmSchedule
import io.github.imlemos.clock.core.scheduling.Recurrence
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import java.time.Instant
import java.time.ZoneId

/** An alarm as the rest of the app sees it. Scheduling decisions are delegated to `:core:scheduling`. */
data class Alarm(
    val id: Long = 0,
    val time: TimeOfDay,
    val recurrence: Recurrence = Recurrence.Once,
    val label: String = "",
    val enabled: Boolean = true,
    val zone: ZoneId? = null,
    val skippedOccurrence: Instant? = null,
    val lastTriggeredOccurrence: Instant? = null,
    val snoozedUntil: Instant? = null,
    val sound: AlarmSound = AlarmSound(),
    val singleUse: Boolean = false,
    val groupId: Long? = null,
    /** Alarm screen layout (docs/PLANO.md 3.7); `null` uses the default layout from settings. */
    val layoutId: Long? = null,
    val createdAt: Instant,
    /** When the alarm was last saved or switched on (see AlarmCalculator.missedOccurrence). */
    val armedAt: Instant = createdAt,
) {
    val schedule: AlarmSchedule
        get() = AlarmSchedule(
            time = time,
            recurrence = recurrence,
            enabled = enabled,
            zone = zone,
            skippedOccurrence = skippedOccurrence,
            lastTriggeredOccurrence = lastTriggeredOccurrence,
            snoozedUntil = snoozedUntil,
        )

    /**
     * A new alarm with the same settings, switched off: no id, and nothing it has rung, skipped
     * or snoozed so far.
     */
    fun asCopy(now: Instant): Alarm = copy(
        id = 0,
        enabled = false,
        skippedOccurrence = null,
        lastTriggeredOccurrence = null,
        snoozedUntil = null,
        createdAt = now,
        armedAt = now,
    )

    companion object {
        // Defaults from docs/PLANO.md section 6; settings hold the user's own defaults.
        const val DEFAULT_SNOOZE_MINUTES = 5

        /** Choices on the snooze wheel and in the editor (docs/PLANO.md 3.3). */
        val SNOOZE_CHOICES = listOf(5, 10, 15, 20, 30, 45, 60)

        /** [SNOOZE_CHOICES] plus the alarm's own length when it is a custom one, in order. */
        fun snoozeChoices(own: Int): List<Int> = (SNOOZE_CHOICES + own).distinct().sorted()
        const val DEFAULT_RING_DURATION_SECONDS = 60
    }
}

/** A named group of alarms; its switch is a master switch (docs/PLANO.md 3.8). */
data class AlarmGroup(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val expanded: Boolean = true,
    val createdAt: Instant,
)
