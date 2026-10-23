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

    fun isZero(): Boolean = audioManager.getStreamVolume(AudioManager.STREAM_ALARM) == 0

    /** Raises the alarm volume one step and shows the system volume panel. */
    fun raise() {
        audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
    }
}
