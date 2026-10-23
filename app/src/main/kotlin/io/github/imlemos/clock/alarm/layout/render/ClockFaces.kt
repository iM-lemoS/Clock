package io.github.imlemos.clock.alarm.layout.render

import androidx.annotation.FontRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.layout.ClockElement
import io.github.imlemos.clock.alarm.layout.ClockFont
import io.github.imlemos.clock.alarm.layout.ClockStyle
import io.github.imlemos.clock.alarm.ui.formatTimeOfDay
import io.github.imlemos.clock.alarm.ui.timeParts
import io.github.imlemos.clock.core.designsystem.component.DotTime
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A bundled variable font and the weights it supports. */
data class ClockFontSpec(@param:FontRes val res: Int, val minWeight: Int, val maxWeight: Int, val defaultWeight: Int)

val ClockFont.spec: ClockFontSpec
    get() = when (this) {
        ClockFont.DOTO -> ClockFontSpec(R.font.doto, 100, 900, 700)
        ClockFont.MONO -> ClockFontSpec(R.font.jetbrains_mono, 100, 800, 300)
        ClockFont.OUTFIT -> ClockFontSpec(R.font.outfit, 100, 900, 300)
        ClockFont.SERIF -> ClockFontSpec(R.font.playfair, 400, 900, 500)
        ClockFont.ORBITRON -> ClockFontSpec(R.font.orbitron, 400, 900, 500)
        ClockFont.CONDENSED -> ClockFontSpec(R.font.big_shoulders, 100, 900, 700)
        ClockFont.ROUNDED -> ClockFontSpec(R.font.comfortaa, 300, 700, 400)
    }

/** The weight a clock is drawn at: its own, else the font's default, within the font's range. */
fun ClockElement.effectiveWeight(): Int = (weight ?: font.spec.defaultWeight).coerceIn(font.spec.minWeight, font.spec.maxWeight)

/** [font] at exactly [weight] (and Doto's dot [roundness]), as variable font settings. */
@OptIn(ExperimentalTextApi::class)
@Composable
fun rememberClockFontFamily(font: ClockFont, weight: Int, roundness: Int = 100): FontFamily = remember(font, weight, roundness) {
    val settings = buildList {
        add(FontVariation.weight(weight))
        if (font == ClockFont.DOTO) add(FontVariation.Setting("ROND", roundness.toFloat()))
    }
    FontFamily(Font(font.spec.res, FontWeight(weight), variationSettings = FontVariation.Settings(*settings.toTypedArray())))
}

/** The current time drawn as [element] says: digital, stacked or one of the analog faces. */
@Composable
fun ClockFace(element: ClockElement, time: TimeOfDay, color: Color, accent: Color) {
    val context = LocalContext.current
    val description = formatTimeOfDay(context, time)
    val weight = element.effectiveWeight()
    val family = rememberClockFontFamily(element.font, weight, element.roundness)
    val parts = timeParts(context, time)
    when (element.style) {
        ClockStyle.ANALOG, ClockStyle.ANALOG_LINES -> AnalogClock(
            time = time,
            color = color,
            accent = accent,
            thickness = weight / 400f,
            lines = element.style == ClockStyle.ANALOG_LINES,
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        )
        ClockStyle.STACKED -> {
            val style = digitStyle(family, weight, STACKED_SIZE)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy((-8).dp),
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            ) {
                Text(parts.hours, style = style, color = color, maxLines = 1)
                Text(parts.minutes, style = style, color = color, maxLines = 1)
                parts.dayPeriod?.let { Text(it, style = ClockTheme.typography.label, color = color, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
        else -> if (element.font == ClockFont.DOTO) {
            // Doto's own colon is two plus-shaped clusters; DotTime draws round dots instead.
            DotTime(
                hours = parts.hours,
                minutes = parts.minutes,
                dayPeriod = parts.dayPeriod,
                contentDescription = description,
                style = ClockTheme.typography.timeDisplay.copy(fontFamily = family, fontWeight = FontWeight(weight)),
                color = color,
            )
        } else {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            ) {
                Text("${parts.hours}:${parts.minutes}", style = digitStyle(family, weight, DIGITAL_SIZE), color = color, maxLines = 1)
                parts.dayPeriod?.let {
                    Text(it, style = ClockTheme.typography.label, color = color, modifier = Modifier.padding(start = 6.dp, bottom = 12.dp))
                }
            }
        }
    }
}

private fun digitStyle(family: FontFamily, weight: Int, size: Float) = TextStyle(
    fontFamily = family,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = size.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/**
 * An analog face: hour dots (larger at 12, 3, 6 and 9) or, with [lines], tick marks. [thickness]
 * scales the hands and marks (1 = regular).
 */
@Composable
private fun AnalogClock(
    time: TimeOfDay,
    color: Color,
    accent: Color,
    thickness: Float,
    lines: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(240.dp)) {
        val radius = size.minDimension / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)
        for (mark in 0 until 60) {
            val angle = mark / 60f * 2f * PI.toFloat() - PI.toFloat() / 2f
            val direction = Offset(cos(angle), sin(angle))
            val major = mark % 15 == 0
            val hour = mark % 5 == 0
            if (lines) {
                if (!hour) continue
                val length = radius * if (major) 0.16f else 0.09f
                drawLine(
                    color,
                    centre + direction * (radius * 0.96f - length),
                    centre + direction * (radius * 0.96f),
                    strokeWidth = radius * (if (major) 0.03f else 0.018f) * thickness,
                    cap = StrokeCap.Round,
                )
            } else {
                val dot = radius * when {
                    major -> 0.045f
                    hour -> 0.028f
                    else -> 0.01f
                } * thickness.coerceIn(0.6f, 1.6f)
                drawCircle(color.copy(alpha = if (hour) color.alpha else color.alpha * 0.4f), dot, centre + direction * (radius * 0.92f))
            }
        }
        val minuteAngle = time.minute / 60f * 2f * PI.toFloat() - PI.toFloat() / 2f
        val hourAngle = ((time.hour % 12) + time.minute / 60f) / 12f * 2f * PI.toFloat() - PI.toFloat() / 2f
        drawLine(color, centre, centre + Offset(cos(hourAngle), sin(hourAngle)) * radius * 0.5f, strokeWidth = radius * 0.06f * thickness, cap = StrokeCap.Round)
        drawLine(color, centre, centre + Offset(cos(minuteAngle), sin(minuteAngle)) * radius * 0.75f, strokeWidth = radius * 0.035f * thickness, cap = StrokeCap.Round)
        drawCircle(accent, radius * 0.05f * thickness.coerceAtLeast(1f), centre)
    }
}

private const val DIGITAL_SIZE = 80f
private const val STACKED_SIZE = 120f
