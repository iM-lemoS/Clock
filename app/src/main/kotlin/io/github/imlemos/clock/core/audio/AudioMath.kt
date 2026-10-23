package io.github.imlemos.clock.core.audio

import io.github.imlemos.clock.alarm.model.Vibration
import io.github.imlemos.clock.core.data.prefs.AudioOutput

/** Pure audio decisions, kept free of Android types so they can be unit tested. */
object AudioMath {

    /**
     * Player volume (0..1) [elapsedMs] into a gradual ramp. Quadratic, so the perceived loudness
     * rises roughly evenly instead of jumping in the first seconds.
     */
    fun rampVolume(elapsedMs: Long, rampMs: Long, target: Float): Float {
        if (rampMs <= 0) return target
        val t = (elapsedMs.toFloat() / rampMs).coerceIn(0f, 1f)
        return target * t * t
    }

    enum class OutputKind { SPEAKER, BLUETOOTH, OTHER }

    /**
     * Where to play for [preference]: one entry per player, each the device to force, or `null` for
     * the system's normal routing. "Phone and Bluetooth" plays on both at once when Bluetooth is
     * connected (the system routing alone often picks just the speaker). Bluetooth only falls back
     * to the speaker when nothing is connected, so an alarm is never silent.
     */
    fun outputTargets(preference: AudioOutput, available: List<OutputKind>): List<OutputKind?> {
        val speaker = OutputKind.SPEAKER.takeIf { it in available }
        val bluetooth = OutputKind.BLUETOOTH.takeIf { it in available }
        return when (preference) {
            AudioOutput.PHONE_AND_BLUETOOTH -> if (bluetooth != null && speaker != null) listOf(speaker, bluetooth) else listOf(null)
            AudioOutput.PHONE_ONLY -> listOf(speaker)
            AudioOutput.BLUETOOTH_ONLY -> listOf(bluetooth ?: speaker)
        }
    }

    /**
     * Vibration waveforms: alternating off/on durations in ms, repeated from index 0. [Vibration.SYNC]
     * uses [Vibration.PULSE] when the phone cannot generate haptics from audio.
     */
    fun pattern(vibration: Vibration): LongArray? = when (vibration) {
        Vibration.NONE -> null
        Vibration.SYNC, Vibration.PULSE -> longArrayOf(0, 600, 400, 600, 1200)
        Vibration.HEARTBEAT -> longArrayOf(0, 120, 120, 180, 900)
        Vibration.WAVE -> longArrayOf(0, 1500, 500)
        Vibration.STACCATO -> longArrayOf(0, 80, 80, 80, 80, 80, 80, 80, 600)
        Vibration.LONG -> longArrayOf(0, 3000, 1000)
    }
}
