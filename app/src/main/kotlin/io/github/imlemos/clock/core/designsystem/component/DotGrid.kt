package io.github.imlemos.clock.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme

/**
 * Decorative dot-matrix background. Also gives glass something to refract on empty screens.
 *
 * Dots fade in from [fadeStart] (fraction of the height) to the bottom edge, so they sit behind
 * the floating glass but never behind text.
 */
@Composable
fun DotGrid(
    modifier: Modifier = Modifier,
    spacing: Dp = 18.dp,
    dotRadius: Dp = 1.2.dp,
    fadeStart: Float = 0.62f,
    color: Color = ClockTheme.colors.contentFaint,
) {
    Canvas(modifier) {
        val step = spacing.toPx()
        val radius = dotRadius.toPx()
        val columns = (size.width / step).toInt()
        val rows = (size.height / step).toInt()
        // Centre the grid so the margins are even on every screen size.
        val originX = (size.width - columns * step) / 2f + step / 2f
        val originY = (size.height - rows * step) / 2f + step / 2f
        for (row in 0 until rows) {
            val y = originY + row * step
            val alpha = ((y / size.height - fadeStart) / (1f - fadeStart)).coerceIn(0f, 1f)
            if (alpha == 0f) continue
            for (column in 0 until columns) {
                drawCircle(color, radius, Offset(originX + column * step, y), alpha = alpha)
            }
        }
    }
}
