package io.github.imlemos.clock.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Black-and-white palette with a single accent. Light mode is a direct inversion of dark mode.
 *
 * The accent is reserved for action points: active switches, "+", selection and primary buttons.
 */
@Immutable
data class ClockColors(
    val isDark: Boolean,
    /** Screen background: pure black (OLED friendly) or pure white. */
    val background: Color,
    /** Solid content surfaces such as list cards. */
    val surface: Color,
    /** Opaque stand-in for glass when transparency is reduced. */
    val surfaceRaised: Color,
    val content: Color,
    val contentMuted: Color,
    /** Decorative only (dot patterns, idle digits); never for text that must be read. */
    val contentFaint: Color,
    val outline: Color,
    val accent: Color,
    val onAccent: Color,
)

fun clockColors(isDark: Boolean, accent: Color = AccentPalette.Default.color): ClockColors {
    val onAccent = if (contrastRatio(accent, Color.White) >= contrastRatio(accent, Color.Black)) {
        Color.White
    } else {
        Color.Black
    }
    return if (isDark) {
        ClockColors(
            isDark = true,
            background = Color(0xFF000000),
            surface = Color(0xFF111111),
            surfaceRaised = Color(0xFF1C1C1C),
            content = Color(0xFFFFFFFF),
            contentMuted = Color(0xFFA0A0A0),
            contentFaint = Color(0xFF333333),
            outline = Color(0xFF474747),
            accent = accent,
            onAccent = onAccent,
        )
    } else {
        ClockColors(
            isDark = false,
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFEEEEEE),
            surfaceRaised = Color(0xFFE3E3E3),
            content = Color(0xFF000000),
            contentMuted = Color(0xFF5F5F5F),
            contentFaint = Color(0xFFCCCCCC),
            outline = Color(0xFFB5B5B5),
            accent = accent,
            onAccent = onAccent,
        )
    }
}

/** WCAG 2.x contrast ratio between two opaque colors, in `1..21`. */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

internal val LocalClockColors = staticCompositionLocalOf { clockColors(isDark = true) }
