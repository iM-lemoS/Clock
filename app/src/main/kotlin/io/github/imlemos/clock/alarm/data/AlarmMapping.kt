package io.github.imlemos.clock.alarm.data

import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.model.AlarmGroup
import io.github.imlemos.clock.alarm.model.AlarmSound
import io.github.imlemos.clock.alarm.model.GradualVolume
import io.github.imlemos.clock.alarm.model.Vibration
import io.github.imlemos.clock.core.data.db.AlarmEntity
import io.github.imlemos.clock.core.data.db.AlarmGroupEntity
import io.github.imlemos.clock.core.scheduling.Recurrence
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Stored values are persisted on users' devices: never rename them.
private const val ONCE = "ONCE"
private const val WEEKLY = "WEEKLY"
private const val DATES = "DATES"

internal fun AlarmEntity.toAlarm(): Alarm = Alarm(
    id = id,
    time = TimeOfDay(hour, minute),
    recurrence = when (recurrenceType) {
        WEEKLY -> Recurrence.Weekly(DayOfWeek.entries.filter { weekDays and it.bit != 0 }.toSet())
        DATES -> Recurrence.Dates(dates.split(',').filter(String::isNotBlank).map(LocalDate::parse).toSet())
        else -> Recurrence.Once
    },
    label = label,
    enabled = enabled,
    zone = zoneId?.let(ZoneId::of),
    skippedOccurrence = skippedOccurrence?.let(Instant::ofEpochMilli),
    lastTriggeredOccurrence = lastTriggeredOccurrence?.let(Instant::ofEpochMilli),
    snoozedUntil = snoozedUntil?.let(Instant::ofEpochMilli),
    snoozeMinutes = snoozeMinutes,
    sound = AlarmSound(
        ringtoneUri = ringtoneUri,
        ringtoneTitle = ringtoneTitle,
        vibration = if (!vibrate) {
            Vibration.NONE
        } else {
            Vibration.entries.firstOrNull { it.name == vibrationPattern && it != Vibration.NONE } ?: Vibration.SYNC
        },
        gradual = GradualVolume(
            enabled = gradualEnabled,
            targetPercent = gradualTargetPercent.coerceIn(1, 100),
            seconds = gradualSeconds.coerceIn(1, 600),
        ),
        ringDurationSeconds = ringDurationSeconds,
    ),
    singleUse = singleUse,
    groupId = groupId,
    createdAt = Instant.ofEpochMilli(createdAt),
    armedAt = Instant.ofEpochMilli(if (armedAt == 0L) createdAt else armedAt),
)

internal fun Alarm.toEntity(): AlarmEntity = AlarmEntity(
    id = id,
    hour = time.hour,
    minute = time.minute,
    recurrenceType = when (recurrence) {
        Recurrence.Once -> ONCE
        is Recurrence.Weekly -> WEEKLY
        is Recurrence.Dates -> DATES
    },
    weekDays = (recurrence as? Recurrence.Weekly)?.days?.fold(0) { acc, day -> acc or day.bit } ?: 0,
    dates = (recurrence as? Recurrence.Dates)?.dates?.sorted()?.joinToString(",") ?: "",
    label = label,
    enabled = enabled,
    zoneId = zone?.id,
    skippedOccurrence = skippedOccurrence?.toEpochMilli(),
    lastTriggeredOccurrence = lastTriggeredOccurrence?.toEpochMilli(),
    snoozedUntil = snoozedUntil?.toEpochMilli(),
    snoozeMinutes = snoozeMinutes,
    ringDurationSeconds = sound.ringDurationSeconds,
    vibrate = sound.vibration != Vibration.NONE,
    ringtoneUri = sound.ringtoneUri,
    createdAt = createdAt.toEpochMilli(),
    singleUse = singleUse,
    armedAt = armedAt.toEpochMilli(),
    vibrationPattern = (sound.vibration.takeIf { it != Vibration.NONE } ?: Vibration.SYNC).name,
    gradualEnabled = sound.gradual.enabled,
    gradualTargetPercent = sound.gradual.targetPercent,
    gradualSeconds = sound.gradual.seconds,
    ringtoneTitle = sound.ringtoneTitle,
    groupId = groupId,
)

internal fun AlarmGroupEntity.toGroup(): AlarmGroup =
    AlarmGroup(id, name, enabled, expanded, Instant.ofEpochMilli(createdAt))

internal fun AlarmGroup.toEntity(): AlarmGroupEntity =
    AlarmGroupEntity(id, name, enabled, expanded, createdAt.toEpochMilli())

private val DayOfWeek.bit: Int get() = 1 shl ordinal
