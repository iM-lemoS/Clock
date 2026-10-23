package io.github.imlemos.clock.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme

/**
 * A time in dot-matrix digits. The colon is drawn here instead of using Doto's glyph, which renders
 * as two plus-shaped clusters: two single round dots read better at every size.
 *
 * @param contentDescription What screen readers announce, e.g. the system-formatted time.
 */
@Composable
fun DotTime(
    hours: String,
    minutes: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    dayPeriod: String? = null,
    style: TextStyle = ClockTheme.typography.timeLarge,
    color: Color = ClockTheme.colors.content,
) {
    Row(
        modifier = modifier.clearAndSetSemantics { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(hours, style = style, color = color, maxLines = 1)
        DotColon(style = style, color = color)
        Text(minutes, style = style, color = color, maxLines = 1)
        if (dayPeriod != null) {
            Text(
                text = dayPeriod,
                style = ClockTheme.typography.label,
                color = color,
                modifier = Modifier.padding(start = 6.dp).align(Alignment.Bottom),
            )
        }
    }
}

@Composable
private fun DotColon(style: TextStyle, color: Color) {
    val density = LocalDensity.current
    val fontPx = with(density) { style.fontSize.toPx() }
    Canvas(
        Modifier
            .padding(horizontal = 1.dp)
            .size(width = with(density) { (fontPx * 0.34f).toDp() }, height = with(density) { (fontPx * 0.8f).toDp() }),
    ) {
        // Close to the dot size of Doto's digits, whose grid is about a ninth of the font size.
        val radius = fontPx * 0.055f
        val gap = fontPx * 0.16f
        val centre = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color, radius, centre.copy(y = centre.y - gap))
        drawCircle(color, radius, centre.copy(y = centre.y + gap))
    }
}
