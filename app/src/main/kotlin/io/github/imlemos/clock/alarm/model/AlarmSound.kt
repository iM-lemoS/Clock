package io.github.imlemos.clock.alarm.model

/**
 * How an alarm vibrates (docs/PLANO.md 3.1). [SYNC] follows the sound itself on phones that support
 * audio-coupled haptics and falls back to [PULSE] elsewhere.
 */
enum class Vibration { NONE, SYNC, PULSE, HEARTBEAT, WAVE, STACCATO, LONG }

/** Volume that rises from silence to [targetPercent] of the alarm volume over [seconds]. */
data class GradualVolume(
    val enabled: Boolean = false,
    val targetPercent: Int = 100,
    val seconds: Int = 60,
) {
    init {
        require(targetPercent in 1..100) { "targetPercent must be in 1..100" }
        require(seconds in 1..600) { "seconds must be in 1..600" }
    }
}

/**
 * How an alarm rings: sound, vibration, ring duration and snooze length. [ringtoneUri] `null` plays the system's default alarm sound.
 * Files picked by the user are copied into the app (device-protected storage) and referenced by a
 * file URI, so they play before the first unlock and survive the original being deleted.
 */
data class AlarmSound(
    val ringtoneUri: String? = null,
    val ringtoneTitle: String? = null,
    val vibration: Vibration = Vibration.SYNC,
    val gradual: GradualVolume = GradualVolume(),
    val ringDurationSeconds: Int = Alarm.DEFAULT_RING_DURATION_SECONDS,
    /** A tap on "Snooze" waits this long; a long press offers the other choices. */
    val snoozeMinutes: Int = Alarm.DEFAULT_SNOOZE_MINUTES,
)
