package io.github.imlemos.clock.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Any dot-matrix readout ("00:00:00", "00:00,00"): colons are drawn like in [DotTime], every other
 * character uses Doto. Shrinks to fit the available width instead of clipping.
 */
@Composable
fun DotDigits(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = ClockTheme.typography.timeDisplay,
    color: Color = ClockTheme.colors.content,
    minFontSize: TextUnit = 24.sp,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Doto draws ":" and "." as plus-shaped clusters; those two are drawn as single round dots.
    val tokens = remember(text) { tokenize(text) }
    BoxWithConstraints(modifier) {
        val fitted = remember(text, style, maxWidth) {
            val maxPx = with(density) { maxWidth.toPx() }
            val fontPx = with(density) { style.fontSize.toPx() }
            val gapPx = with(density) { 2.dp.toPx() }
            val widthPx = tokens.sumOf { token ->
                when (token) {
                    ":" -> (fontPx * COLON_WIDTH + gapPx).toDouble()
                    "." -> (fontPx * POINT_WIDTH + gapPx).toDouble()
                    else -> measurer.measure(token, style).size.width.toDouble()
                }
            }.toFloat()
            val scale = (maxPx / widthPx).coerceAtMost(1f)
            val size = (style.fontSize.value * scale).coerceAtLeast(minFontSize.value)
            style.copy(fontSize = size.sp, lineHeight = size.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            tokens.forEach { token ->
                when (token) {
                    ":" -> DotColon(fitted, color)
                    "." -> DotPoint(fitted, color)
                    else -> Text(token, style = fitted, color = color, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** Splits "00:00.00" into ["00", ":", "00", ".", "00"]. */
private fun tokenize(text: String): List<String> = buildList {
    val run = StringBuilder()
    text.forEach { char ->
        if (char == ':' || char == '.') {
            if (run.isNotEmpty()) add(run.toString()).also { run.clear() }
            add(char.toString())
        } else {
            run.append(char)
        }
    }
    if (run.isNotEmpty()) add(run.toString())
}

@Composable
private fun DotPoint(style: TextStyle, color: Color) {
    val density = LocalDensity.current
    val fontPx = with(density) { style.fontSize.toPx() }
    Canvas(
        Modifier
            .padding(horizontal = 1.dp)
            .size(width = with(density) { (fontPx * POINT_WIDTH).toDp() }, height = with(density) { (fontPx * 0.8f).toDp() }),
    ) {
        drawCircle(color, fontPx * DOT_RADIUS, Offset(size.width / 2f, size.height / 2f + fontPx * 0.27f))
    }
}

private const val POINT_WIDTH = 0.26f
private const val DOT_RADIUS = 0.055f
private const val COLON_WIDTH = 0.34f

@Composable
private fun DotColon(style: TextStyle, color: Color) {
    val density = LocalDensity.current
    val fontPx = with(density) { style.fontSize.toPx() }
    Canvas(
        Modifier
            .padding(horizontal = 1.dp)
            .size(width = with(density) { (fontPx * COLON_WIDTH).toDp() }, height = with(density) { (fontPx * 0.8f).toDp() }),
    ) {
        // Close to the dot size of Doto's digits, whose grid is about a ninth of the font size.
        val radius = fontPx * DOT_RADIUS
        val gap = fontPx * 0.16f
        val centre = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color, radius, centre.copy(y = centre.y - gap))
        drawCircle(color, radius, centre.copy(y = centre.y + gap))
    }
}
