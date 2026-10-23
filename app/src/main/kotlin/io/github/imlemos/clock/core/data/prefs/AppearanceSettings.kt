package io.github.imlemos.clock.core.data.prefs

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * User choices for how the app looks.
 *
 * @property accentArgb Accent color as ARGB, or `null` for the app default.
 * @property reduceTransparency Replaces glass with solid surfaces. The system can also force this
 *   (see `core/designsystem/glass`), independently of this flag.
 */
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentArgb: Int? = null,
    val reduceTransparency: Boolean = false,
)

/** What the volume-down button does while an alarm rings (docs/PLANO.md 3.3). */
enum class VolumeButtonAction {
    /** The system handles the key as usual (changes the alarm volume). */
    OFF,

    /** Mutes this ringing only; vibration continues and the alarm stays on screen. */
    SILENCE_SOUND,

    /** Mutes sound and stops vibration for this ringing only. */
    SILENCE_SOUND_AND_VIBRATION,
}

/** Where alarms play (docs/PLANO.md 3.2). */
enum class AudioOutput {
    /** The system's normal alarm routing: the phone speaker and any connected Bluetooth device. */
    PHONE_AND_BLUETOOTH,

    PHONE_ONLY,

    /** Falls back to the phone when no Bluetooth device is connected, so an alarm is never silent. */
    BLUETOOTH_ONLY,
}
