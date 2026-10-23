package io.github.imlemos.clock.core.designsystem.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.imlemos.clock.R

/**
 * Preset accents. Each one keeps at least 3:1 contrast (WCAG non-text) against both the black
 * and the white background, so it works unchanged in dark and light mode (see AccentPaletteTest).
 */
enum class AccentPalette(val color: Color, @param:StringRes val label: Int) {
    Red(Color(0xFFE0282E), R.string.accent_red),
    Violet(Color(0xFF9B4DFF), R.string.accent_violet),
    Orange(Color(0xFFD9580D), R.string.accent_orange),
    Blue(Color(0xFF2F6BFF), R.string.accent_blue),
    Green(Color(0xFF1E9E4A), R.string.accent_green),
    ;

    companion object {
        val Default = Red

        /** Resolves a stored ARGB value; `null` means the default accent. */
        fun colorFor(argb: Int?): Color = argb?.let(::Color) ?: Default.color

        fun matching(argb: Int?): AccentPalette? =
            if (argb == null) Default else entries.firstOrNull { it.color.toArgb() == argb }

        /** Minimum contrast against the background for an accent to stay visible (WCAG non-text). */
        const val MIN_CONTRAST = 3f

        /** Whether [color] is too dark for the black theme and/or too light for the white one. */
        fun contrastProblems(color: Color): ContrastProblems = ContrastProblems(
            tooDarkForDark = contrastRatio(color, Color.Black) < MIN_CONTRAST,
            tooLightForLight = contrastRatio(color, Color.White) < MIN_CONTRAST,
        )
    }
}

/** A custom accent's visibility on each theme. */
data class ContrastProblems(val tooDarkForDark: Boolean, val tooLightForLight: Boolean) {
    val any: Boolean get() = tooDarkForDark || tooLightForLight
}
