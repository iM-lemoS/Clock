package io.github.imlemos.clock.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.imlemos.clock.core.designsystem.glass.GlassLevel
import io.github.imlemos.clock.core.designsystem.glass.LocalGlassLevel

/**
 * App theme. No dynamic color: the palette is always our own black/white plus [accent].
 *
 * @param glassLevel How floating layers render; resolve it with `rememberGlassLevel`.
 */
@Composable
fun ClockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Color = AccentPalette.Default.color,
    glassLevel: GlassLevel = GlassLevel.Solid,
    content: @Composable () -> Unit,
) {
    val colors = remember(darkTheme, accent) { clockColors(darkTheme, accent) }
    val typography = remember { clockTypography() }
    CompositionLocalProvider(
        LocalClockColors provides colors,
        LocalClockTypography provides typography,
        LocalGlassLevel provides glassLevel,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = typography.toMaterial(),
            content = content,
        )
    }
}

object ClockTheme {
    val colors: ClockColors
        @Composable @ReadOnlyComposable get() = LocalClockColors.current

    val typography: ClockTypography
        @Composable @ReadOnlyComposable get() = LocalClockTypography.current
}

private fun ClockColors.toMaterialColorScheme() = if (isDark) {
    darkColorScheme(
        primary = accent, onPrimary = onAccent,
        secondary = content, onSecondary = background,
        background = background, onBackground = content,
        surface = background, onSurface = content,
        surfaceVariant = surface, onSurfaceVariant = contentMuted,
        surfaceContainer = surface, surfaceContainerHigh = surfaceRaised,
        outline = outline, outlineVariant = contentFaint,
    )
} else {
    lightColorScheme(
        primary = accent, onPrimary = onAccent,
        secondary = content, onSecondary = background,
        background = background, onBackground = content,
        surface = background, onSurface = content,
        surfaceVariant = surface, onSurfaceVariant = contentMuted,
        surfaceContainer = surface, surfaceContainerHigh = surfaceRaised,
        outline = outline, outlineVariant = contentFaint,
    )
}
