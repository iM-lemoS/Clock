package io.github.imlemos.clock.core.designsystem.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import io.github.imlemos.clock.core.designsystem.theme.ClockColors
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme

/**
 * The content that glass surfaces sample. Screens only see this type: Haze stays an
 * implementation detail of this package, so the renderer can be swapped in one place.
 */
@Stable
class GlassBackdrop internal constructor(internal val haze: HazeState)

@Composable
fun rememberGlassBackdrop(): GlassBackdrop = remember { GlassBackdrop(HazeState()) }

/** Marks the layer behind floating glass. Glass surfaces must not be inside this layer. */
fun Modifier.glassBackdropSource(backdrop: GlassBackdrop): Modifier = hazeSource(backdrop.haze)

val CapsuleShape = RoundedCornerShape(percent = 50)

/**
 * A floating "Liquid Glass" layer. Renders according to [LocalGlassLevel]:
 * full glass (Android 13+), blur only (Android 12) or solid (reduced transparency).
 *
 * Only floating layers use glass; list cards and primary text stay solid for legibility.
 */
@Composable
fun GlassSurface(
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = CapsuleShape,
    level: GlassLevel = LocalGlassLevel.current,
    contentAlignment: Alignment = Alignment.Center,
    raised: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = ClockTheme.colors
    val tint = glassTint(colors, raised)
    val surfaceModifier = when (level) {
        GlassLevel.Full -> Modifier.fullGlass(backdrop, shape, colors, tint)
        GlassLevel.BlurOnly -> Modifier.blurGlass(backdrop, shape, colors, tint)
        GlassLevel.Solid -> Modifier
            .clip(shape)
            .background(if (raised) colors.raisedSolid else colors.surfaceRaised, shape)
            .border(1.dp, if (raised) colors.content.copy(alpha = 0.22f) else colors.outline, shape)
    }
    Box(modifier = modifier.then(surfaceModifier), contentAlignment = contentAlignment, content = content)
}

@OptIn(ExperimentalHazeApi::class)
private fun Modifier.fullGlass(backdrop: GlassBackdrop, shape: RoundedCornerShape, colors: ClockColors, tint: Color): Modifier =
    this
        .hazeGlass(
            input = HazeInput.Sources(backdrop.haze),
            style = GlassStyle.regular.then {
                shape(shape)
                tint(tint)
                backgroundColor(colors.background)
            },
        )
        .border(1.dp, rimBrush(colors), shape)

private fun Modifier.blurGlass(backdrop: GlassBackdrop, shape: RoundedCornerShape, colors: ClockColors, tint: Color): Modifier =
    this
        .clip(shape)
        .hazeBlur(
            input = HazeInput.Sources(backdrop.haze),
            style = HazeBlurStyle {
                blurRadius(24.dp)
                backgroundColor(colors.background)
                colorEffects(listOf(HazeColorEffect.tint(tint.copy(alpha = (tint.alpha + 0.2f).coerceAtMost(1f)))))
                noiseFactor(0.04f)
            },
        )
        .border(1.dp, rimBrush(colors), shape)

/**
 * The glass colour. [raised] layers (dialogs, sheets) sit above a dimmed screen, so they are
 * lighter than the background in dark mode and brighter in light mode, and read as one panel.
 */
private fun glassTint(colors: ClockColors, raised: Boolean): Color = when {
    !raised -> colors.background.copy(alpha = if (colors.isDark) 0.35f else 0.45f)
    colors.isDark -> Color(0xFF2C2C2E).copy(alpha = 0.72f)
    else -> Color.White.copy(alpha = 0.78f)
}

/** Opaque raised panel when transparency is reduced. */
private val ClockColors.raisedSolid: Color get() = if (isDark) Color(0xFF242426) else Color.White

/** Subtle reflection on the outline: brighter at the top edge, fading towards the bottom. */
private fun rimBrush(colors: ClockColors): Brush = Brush.verticalGradient(
    0f to colors.content.copy(alpha = if (colors.isDark) 0.28f else 0.18f),
    0.5f to colors.content.copy(alpha = 0.06f),
    1f to colors.content.copy(alpha = if (colors.isDark) 0.14f else 0.10f),
)

