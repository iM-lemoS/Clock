package io.github.imlemos.clock.alarm.ringing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Resources
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.ui.formatSnooze
import io.github.imlemos.clock.alarm.ui.formatSnoozeCompact
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The dial while the finger is down: its centre (the Snooze button) and the choice under it. */
data class SnoozeDialState(val center: Offset, val up: Boolean, val radius: Float, val selected: Int? = null)

/**
 * Snooze choices on a half circle around the held Snooze button (docs/PLANO.md 3.3). Drawn only;
 * the finger that opened it keeps driving it through the button's own gesture.
 */
@Composable
fun SnoozeDialOverlay(state: SnoozeDialState, choices: List<Int>) {
    val colors = ClockTheme.colors
    val resources = LocalResources.current
    val radiusPx = state.radius
    val labelGapPx = with(LocalDensity.current) { 72.dp.toPx() }
    val direction = if (state.up) -1f else 1f
    val angles = SnoozeDialMath.angles(choices.size)
    // Bubbles shrink when many choices (a custom snooze adds one) share a narrow arc.
    val step = if (angles.size > 1) Math.toRadians((angles[0] - angles[1]).toDouble()) else Math.PI
    val chord = with(LocalDensity.current) { (2 * radiusPx * sin(step / 2).toFloat()).toDp() }
    val bubble = minOf(56.dp, chord - 4.dp)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
        angles.forEachIndexed { index, angle ->
            val radians = Math.toRadians(angle.toDouble())
            val point = state.center + Offset(cos(radians).toFloat(), direction * sin(radians).toFloat()) * radiusPx
            val selected = index == state.selected
            val size: Dp = if (selected) bubble + 14.dp else bubble
            Box(
                Modifier
                    .centeredAt(point)
                    .size(size)
                    .clip(CircleShape)
                    .background(if (selected) colors.accent else colors.surfaceRaised, CircleShape)
                    .border(1.dp, if (selected) colors.accent else colors.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // The number large and the unit small under it, so "20 min" fits a small bubble.
                val (amount, unit) = dialLabel(resources, choices[index])
                val color = if (selected) colors.onAccent else colors.content
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(amount, style = ClockTheme.typography.label, color = color, textAlign = TextAlign.Center, maxLines = 1)
                    if (unit != null) Text(unit, style = ClockTheme.typography.caption.copy(fontSize = 10.sp, lineHeight = 12.sp), color = color, maxLines = 1)
                }
            }
        }
        val label = state.selected?.let { stringResource(R.string.snooze_wheel_confirm, formatSnooze(resources, choices[it])) }
            ?: stringResource(R.string.snooze_dial_hint)
        // On its own pill, so it stays readable over the alarm's label behind the scrim.
        Text(
            label,
            style = ClockTheme.typography.title,
            color = colors.content,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .centeredAt(state.center + Offset(0f, direction * (radiusPx + labelGapPx)))
                .clip(CircleShape)
                .background(colors.surfaceRaised, CircleShape)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

/** "20" and "min", "1" and "h", or "1h30" alone. */
private fun dialLabel(resources: Resources, minutes: Int): Pair<String, String?> = when {
    minutes < 60 -> minutes.toString() to resources.getString(R.string.unit_minutes_short)
    minutes % 60 == 0 -> (minutes / 60).toString() to resources.getString(R.string.unit_hours_short)
    else -> formatSnoozeCompact(resources, minutes) to null
}

/** Places the element with its centre on [point] (pixels from the parent's top-left). */
private fun Modifier.centeredAt(point: Offset): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place((point.x - placeable.width / 2f).roundToInt(), (point.y - placeable.height / 2f).roundToInt())
    }
}

/** Largest distance from the Snooze button to the choices; narrow screens use less. */
val DIAL_RADIUS = 152.dp

/** Half a selected choice plus a margin: how far choices stay from the screen edges. */
val DIAL_EDGE = 44.dp

/** Letting go this close to the button cancels. */
val DIAL_DEAD_ZONE = 48.dp
