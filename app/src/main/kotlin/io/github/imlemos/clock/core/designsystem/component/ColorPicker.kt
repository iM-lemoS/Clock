package io.github.imlemos.clock.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import kotlin.math.roundToInt

/**
 * A full colour picker: saturation and brightness square, hue bar, opacity bar and a few quick
 * colours. Returns ARGB, or `null` for the automatic colour when [allowAuto].
 */
@Composable
fun ColorPickerDialog(
    initial: Int?,
    allowAuto: Boolean,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit,
    showOpacity: Boolean = true,
    footer: @Composable (argb: Int) -> Unit = {},
) {
    val colors = ClockTheme.colors
    val start = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial ?: DEFAULT_PICK, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var brightness by remember { mutableFloatStateOf(start[2]) }
    var alpha by remember { mutableFloatStateOf(if (showOpacity) ((initial ?: DEFAULT_PICK) ushr 24) / 255f else 1f) }
    val argb = android.graphics.Color.HSVToColor((alpha * 255).roundToInt(), floatArrayOf(hue, saturation, brightness))

    fun setFrom(value: Int) {
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(value, it) }
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
        alpha = if (showOpacity) (value ushr 24) / 255f else 1f
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(colors.surfaceRaised)
                .padding(20.dp),
        ) {
            Text(stringResource(R.string.color_picker_title), style = ClockTheme.typography.title, color = colors.content)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).border(1.dp, colors.outline, CircleShape)) {
                    Canvas(Modifier.size(44.dp)) {
                        checkerboard()
                        drawRect(Color(argb))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    if (showOpacity) "#%06X · %d%%".format(argb and 0xFFFFFF, (alpha * 100).roundToInt()) else "#%06X".format(argb and 0xFFFFFF),
                    style = ClockTheme.typography.body,
                    color = colors.content,
                )
            }
            Spacer(Modifier.height(16.dp))
            SaturationBrightnessSquare(hue, saturation, brightness) { s, v -> saturation = s; brightness = v }
            Spacer(Modifier.height(16.dp))
            val hueLabel = stringResource(R.string.color_hue)
            GradientBar(
                value = hue / 360f,
                onValueChange = { hue = it * 360f },
                brush = Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }),
                label = hueLabel,
                stateText = "${hue.roundToInt()}°",
            )
            val opaque = Color.hsv(hue, saturation, brightness)
            if (showOpacity) {
                Spacer(Modifier.height(12.dp))
                GradientBar(
                    value = alpha,
                    onValueChange = { alpha = it },
                    brush = Brush.horizontalGradient(listOf(opaque.copy(alpha = 0f), opaque)),
                    label = stringResource(R.string.color_opacity),
                    stateText = "${(alpha * 100).roundToInt()}%",
                    checkered = true,
                )
            }
            footer(argb)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickColors.forEach { quick ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(quick), CircleShape)
                            .border(1.dp, colors.outline, CircleShape)
                            .clickable { setFrom(quick) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (allowAuto) {
                TextButton(onClick = { onPick(null) }) {
                    Text(stringResource(R.string.layout_color_auto), color = colors.content)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = colors.content) }
                TextButton(onClick = { onPick(argb) }) { Text(stringResource(R.string.color_pick), color = colors.accent) }
            }
        }
    }
}

/** Saturation left to right, brightness bottom to top, for the current hue. */
@Composable
private fun SaturationBrightnessSquare(hue: Float, saturation: Float, brightness: Float, onChange: (Float, Float) -> Unit) {
    val label = stringResource(R.string.color_saturation_brightness)
    val actions = listOf(
        stringResource(R.string.color_more_saturated) to (STEP to 0f),
        stringResource(R.string.color_less_saturated) to (-STEP to 0f),
        stringResource(R.string.color_brighter) to (0f to STEP),
        stringResource(R.string.color_darker) to (0f to -STEP),
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                fun update(position: Offset) = onChange(
                    (position.x / size.width).coerceIn(0f, 1f),
                    1f - (position.y / size.height).coerceIn(0f, 1f),
                )
                detectTapGestures { update(it) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onChange(
                        (change.position.x / size.width).coerceIn(0f, 1f),
                        1f - (change.position.y / size.height).coerceIn(0f, 1f),
                    )
                }
            }
            .semantics {
                contentDescription = label
                stateDescription = "${(saturation * 100).roundToInt()}% · ${(brightness * 100).roundToInt()}%"
                customActions = actions.map { (name, step) ->
                    CustomAccessibilityAction(name) {
                        onChange((saturation + step.first).coerceIn(0f, 1f), (brightness + step.second).coerceIn(0f, 1f))
                        true
                    }
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val point = Offset(saturation * size.width, (1f - brightness) * size.height)
        drawCircle(Color.White, 10.dp.toPx(), point, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black, 12.dp.toPx(), point, style = Stroke(1.dp.toPx()))
    }
}

/** A draggable bar from 0 to 1; TalkBack adjusts it like a slider. */
@Composable
private fun GradientBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    brush: Brush,
    label: String,
    stateText: String,
    checkered: Boolean = false,
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .pointerInput(Unit) { detectTapGestures { onValueChange((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> onValueChange((change.position.x / size.width).coerceIn(0f, 1f)) }
            }
            .semantics {
                contentDescription = label
                stateDescription = stateText
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f, steps = 19)
                setProgress { target -> onValueChange(target.coerceIn(0f, 1f)); true }
            },
    ) {
        if (checkered) checkerboard()
        drawRect(brush)
        val x = value * size.width
        drawRoundRect(
            Color.White,
            topLeft = Offset(x - 3.dp.toPx(), 0f),
            size = Size(6.dp.toPx(), size.height),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
    }
}

/** Grey squares behind translucent colours, so opacity is visible. */
private fun DrawScope.checkerboard() {
    val cell = 6.dp.toPx()
    val columns = (size.width / cell).toInt() + 1
    val rows = (size.height / cell).toInt() + 1
    drawRect(Color(0xFFBDBDBD))
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            if ((row + column) % 2 == 0) drawRect(Color(0xFF7A7A7A), Offset(column * cell, row * cell), Size(cell, cell))
        }
    }
}

private const val STEP = 0.1f
private const val DEFAULT_PICK = 0xFFFFFFFF.toInt()

private val QuickColors: List<Int> = listOf(
    0xFFFFFFFF, 0xFF9E9E9E, 0xFF000000, 0xFFE5302B, 0xFFFF7A1A, 0xFFFFC21A,
    0xFF34C759, 0xFF2EC4D6, 0xFF3478F6, 0xFF8E5CF7, 0xFFFF4FA3,
).map { it.toInt() }
