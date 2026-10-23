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
    Orange(Color(0xFFD9580D), R.string.accent_orange),
    Green(Color(0xFF1E9E4A), R.string.accent_green),
    Blue(Color(0xFF2F6BFF), R.string.accent_blue),
    Violet(Color(0xFF9B4DFF), R.string.accent_violet),
    Pink(Color(0xFFE5337A), R.string.accent_pink),
    ;

    companion object {
        val Default = Red

        /** Resolves a stored ARGB value; `null` means the default accent. */
        fun colorFor(argb: Int?): Color = argb?.let(::Color) ?: Default.color

        fun matching(argb: Int?): AccentPalette? =
            if (argb == null) Default else entries.firstOrNull { it.color.toArgb() == argb }
    }
}
