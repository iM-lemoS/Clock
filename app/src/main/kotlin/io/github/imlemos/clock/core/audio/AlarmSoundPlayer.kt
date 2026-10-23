package io.github.imlemos.clock.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.media.audiofx.HapticGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.imlemos.clock.alarm.model.AlarmSound
import io.github.imlemos.clock.alarm.model.Vibration
import io.github.imlemos.clock.core.audio.AudioMath.OutputKind
import io.github.imlemos.clock.core.data.prefs.AudioOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays an alarm on the system alarm stream (separate from media and ringer volume, and not muted
 * by silent mode) and vibrates.
 *
 * - If no ringtone can be opened (e.g. a missing file) it falls back to the system default alarm,
 *   then to a generated tone, so an alarm is never silent because of a file.
 * - Gradual volume ramps the player volume; the alarm stream volume itself is never changed.
 * - "Synced" vibration uses HapticGenerator (haptics derived from the audio) where the phone
 *   supports it, and a regular pattern elsewhere.
 */
@Singleton
class AlarmSoundPlayer @Inject constructor(@ApplicationContext private val context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var haptics: HapticGenerator? = null
    private var rampJob: Job? = null
    private var focusRequest: AudioFocusRequest? = null

    fun start(sound: AlarmSound, output: AudioOutput) {
        stop()
        val useHaptics = sound.vibration == Vibration.SYNC && HapticGenerator.isAvailable()
        val attributes = alarmAttributes(hapticsMuted = !useHaptics)
        requestFocus(attributes)

        val candidates = listOfNotNull(
            sound.ringtoneUri?.toUri(),
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        ).distinct()
        val initialVolume = if (sound.gradual.enabled) 0f else 1f
        player = candidates.firstNotNullOfOrNull { openPlayer(it, attributes, output, initialVolume) }

        val player = player
        if (player == null) {
            Log.w(TAG, "No ringtone could be opened; using a generated tone")
            tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull()
                ?.also { it.startTone(ToneGenerator.TONE_CDMA_HIGH_PBX_L, -1) }
        } else if (sound.gradual.enabled) {
            startRamp(player, sound.gradual.seconds * 1_000L, sound.gradual.targetPercent / 100f)
        }

        haptics = if (useHaptics && player != null) attachHaptics(player.audioSessionId) else null
        if (haptics == null) startVibration(sound.vibration)
    }

    /**
     * Mutes the current ringing only (volume-down shortcut). The next [start] plays at full
     * volume again, so a snooze or the next alarm is unaffected.
     */
    fun silence(alsoVibration: Boolean) {
        rampJob?.cancel()
        player?.runCatching { setVolume(0f, 0f) }
        tone?.runCatching { stopTone() }
        val hadAudioHaptics = haptics != null
        releaseHaptics()
        if (alsoVibration) {
            vibrator.cancel()
        } else if (hadAudioHaptics) {
            // Haptics generated from the audio stop with it; keep the phone vibrating.
            startVibration(Vibration.PULSE)
        }
    }

    fun stop() {
        rampJob?.cancel()
        rampJob = null
        releaseHaptics()
        player?.runCatching { stop(); release() }
        player = null
        tone?.runCatching { stopTone(); release() }
        tone = null
        vibrator.cancel()
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    /** Plays [vibration] once, for previews in the editor. */
    fun previewVibration(vibration: Vibration) {
        val pattern = AudioMath.pattern(vibration) ?: return vibrator.cancel()
        vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun alarmAttributes(hapticsMuted: Boolean): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setHapticChannelsMuted(hapticsMuted)
        .build()

    private fun openPlayer(uri: Uri, attributes: AudioAttributes, output: AudioOutput, volume: Float): MediaPlayer? =
        runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(context, uri)
                isLooping = true
                prepare()
                outputDevice(output)?.let(::setPreferredDevice)
                setVolume(volume, volume)
                start()
                Log.i(TAG, "Playing $uri (output=$output, haptics=${!attributes.areHapticChannelsMuted()})")
            }
        }.onFailure { Log.w(TAG, "Cannot play $uri", it) }.getOrNull()

    /** Maps the output setting to a concrete device, or `null` to keep the system routing. */
    private fun outputDevice(output: AudioOutput): AudioDeviceInfo? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        val kinds = devices.map { it.kind }
        val wanted = AudioMath.preferredOutput(output, kinds) ?: return null
        return devices.firstOrNull { it.kind == wanted }
    }

    private val AudioDeviceInfo.kind: OutputKind
        get() = when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputKind.SPEAKER
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID,
            -> OutputKind.BLUETOOTH
            else -> OutputKind.OTHER
        }

    private fun startRamp(player: MediaPlayer, rampMs: Long, target: Float) {
        val startedAt = SystemClock.elapsedRealtime()
        rampJob = scope.launch {
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                val volume = AudioMath.rampVolume(elapsed, rampMs, target)
                runCatching { player.setVolume(volume, volume) }
                if (elapsed >= rampMs) break
                delay(RAMP_STEP_MS)
            }
        }
    }

    private fun attachHaptics(sessionId: Int): HapticGenerator? = runCatching {
        HapticGenerator.create(sessionId).apply { enabled = true }
    }.onFailure { Log.w(TAG, "HapticGenerator unavailable; using a vibration pattern", it) }.getOrNull()

    private fun releaseHaptics() {
        haptics?.runCatching { enabled = false; release() }
        haptics = null
    }

    /**
     * Claims audio focus for the alarm. Declaring that we handle ducking ourselves stops the system
     * from automatically lowering the alarm to 20% whenever a notification sound plays; we then
     * simply keep ringing at full volume.
     */
    private fun requestFocus(attributes: AudioAttributes) {
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change -> Log.i(TAG, "Audio focus change $change ignored while ringing") }, mainHandler)
            .build()
            .also(audioManager::requestAudioFocus)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun startVibration(vibration: Vibration) {
        val pattern = AudioMath.pattern(vibration) ?: return
        vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun vibrate(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, alarmAttributes(hapticsMuted = true))
        }
    }

    private companion object {
        const val TAG = "AlarmSoundPlayer"
        const val RAMP_STEP_MS = 100L
    }
}
