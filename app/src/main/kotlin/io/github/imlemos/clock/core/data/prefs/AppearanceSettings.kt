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
