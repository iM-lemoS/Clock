package io.github.imlemos.clock.core.audio

import android.content.Context
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The system alarm volume. Silent mode does not mute alarms; only this volume at zero does, so the
 * app warns about it (docs/PLANO.md 3.2).
 */
class AlarmVolume @Inject constructor(@ApplicationContext context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    enum class Level { OK, LOW, ZERO }

    /**
     * ZERO only where the phone allows it; LOW at the stream minimum (Android 9+ keeps the alarm
     * volume at 1 or more, which can still be barely audible).
     */
    fun level(): Level {
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val minimum = audioManager.getStreamMinVolume(AudioManager.STREAM_ALARM)
        return when {
            volume == 0 -> Level.ZERO
            volume <= minimum -> Level.LOW
            else -> Level.OK
        }
    }

    /** Raises the alarm volume one step and shows the system volume panel. */
    fun raise() {
        audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
    }
}
