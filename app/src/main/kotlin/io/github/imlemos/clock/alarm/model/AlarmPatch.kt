package io.github.imlemos.clock.alarm.model

import java.time.ZoneId

/**
 * Batch edit (docs/PLANO.md 3.9): every field starts as "don't change" (`null`); only the fields
 * the user sets are applied to each selected alarm.
 */
data class AlarmPatch(
    val label: String? = null,
    val ringtone: Ringtone? = null,
    val vibration: Vibration? = null,
    val gradual: GradualVolume? = null,
    val ringDurationSeconds: Int? = null,
    val singleUse: Boolean? = null,
    val snoozeMinutes: Int? = null,
    val zone: Zone? = null,
    val layout: Layout? = null,
) {
    /** A layout choice; [id] `null` is the default layout. */
    data class Layout(val id: Long?)

    /** A time zone choice; [id] `null` is "Local time" (follows the app). */
    data class Zone(val id: ZoneId?)

    /** A sound choice; [uri] `null` is the system default alarm sound. */
    data class Ringtone(val uri: String?, val title: String?)

    val isEmpty: Boolean get() = this == AlarmPatch()

    fun applyTo(alarm: Alarm): Alarm = alarm.copy(
        label = label ?: alarm.label,
        singleUse = singleUse ?: alarm.singleUse,
        zone = if (zone != null) zone.id else alarm.zone,
        layoutId = if (layout != null) layout.id else alarm.layoutId,
        sound = alarm.sound.copy(
            ringtoneUri = if (ringtone != null) ringtone.uri else alarm.sound.ringtoneUri,
            ringtoneTitle = if (ringtone != null) ringtone.title else alarm.sound.ringtoneTitle,
            vibration = vibration ?: alarm.sound.vibration,
            gradual = gradual ?: alarm.sound.gradual,
            ringDurationSeconds = ringDurationSeconds ?: alarm.sound.ringDurationSeconds,
            snoozeMinutes = snoozeMinutes ?: alarm.sound.snoozeMinutes,
        ),
    )
}
