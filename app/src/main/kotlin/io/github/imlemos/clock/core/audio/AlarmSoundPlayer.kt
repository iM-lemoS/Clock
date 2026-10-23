package io.github.imlemos.clock.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays an alarm on the system alarm stream (separate from media and ringer volume, and not muted
 * by silent mode) and vibrates. If no ringtone can be opened (e.g. a custom file unavailable before
 * the first unlock) it falls back to the system default alarm, then to a generated tone, so an
 * alarm is never silent because of a missing file.
 */
@Singleton
class AlarmSoundPlayer @Inject constructor(@ApplicationContext private val context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var focusRequest: AudioFocusRequest? = null

    private val alarmAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun start(ringtone: Uri?, vibrate: Boolean) {
        stop()
        requestFocus()
        val candidates = listOfNotNull(
            ringtone,
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        ).distinct()
        player = candidates.firstNotNullOfOrNull(::openPlayer)
        if (player == null) {
            Log.w(TAG, "No ringtone could be opened; using a generated tone")
            tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull()
                ?.also { it.startTone(ToneGenerator.TONE_CDMA_HIGH_PBX_L, -1) }
        }
        if (vibrate) startVibration()
    }

    /**
     * Mutes the current ringing only (volume-down shortcut). The next [start] plays at full
     * volume again, so a snooze or the next alarm is unaffected.
     */
    fun silence(alsoVibration: Boolean) {
        player?.runCatching { setVolume(0f, 0f) }
        tone?.runCatching { stopTone() }
        if (alsoVibration) vibrator.cancel()
    }

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null
        tone?.runCatching { stopTone(); release() }
        tone = null
        vibrator.cancel()
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    private fun openPlayer(uri: Uri): MediaPlayer? = runCatching {
        MediaPlayer().apply {
            setAudioAttributes(alarmAttributes)
            setDataSource(context, uri)
            isLooping = true
            prepare()
            start()
        }
    }.onFailure { Log.w(TAG, "Cannot play $uri", it) }.getOrNull()

    private fun requestFocus() {
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(alarmAttributes)
            .build()
            .also(audioManager::requestAudioFocus)
    }

    private fun startVibration() {
        val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, alarmAttributes)
        }
    }

    private companion object {
        const val TAG = "AlarmSoundPlayer"
        val VIBRATION_PATTERN = longArrayOf(0, 600, 400, 600, 1200)
    }
}
