package io.github.imlemos.clock.alarm.layout.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.imlemos.clock.alarm.layout.ClockStyle
import io.github.imlemos.clock.alarm.ui.formatTimeOfDay
import io.github.imlemos.clock.alarm.ui.timeParts
import io.github.imlemos.clock.core.designsystem.component.DotTime
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The current time in one of the layout clock styles. */
@Composable
fun ClockFace(style: ClockStyle, time: TimeOfDay, color: Color, accent: Color) {
    val context = LocalContext.current
    val description = formatTimeOfDay(context, time)
    when (style) {
        ClockStyle.DOTS -> {
            val parts = timeParts(context, time)
            DotTime(
                hours = parts.hours,
                minutes = parts.minutes,
                dayPeriod = parts.dayPeriod,
                contentDescription = description,
                style = ClockTheme.typography.timeDisplay,
                color = color,
            )
        }
        ClockStyle.MONO -> Text(
            text = description,
            style = ClockTheme.typography.headline.copy(fontSize = 84.sp, lineHeight = 84.sp, fontWeight = FontWeight.Light),
            color = color,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        )
        ClockStyle.ANALOG -> AnalogClock(time, color, accent, Modifier.clearAndSetSemantics { contentDescription = description })
    }
}

/** Dots for the hours (larger at 12, 3, 6 and 9), rounded hands and an accent centre. */
@Composable
private fun AnalogClock(time: TimeOfDay, color: Color, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(240.dp)) {
        val radius = size.minDimension / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)
        for (mark in 0 until 60) {
            val angle = mark / 60f * 2f * PI.toFloat() - PI.toFloat() / 2f
            val major = mark % 15 == 0
            val hour = mark % 5 == 0
            val dot = when {
                major -> radius * 0.045f
                hour -> radius * 0.028f
                else -> radius * 0.01f
            }
            val distance = radius * 0.92f
            drawCircle(color.copy(alpha = if (hour) color.alpha else color.alpha * 0.4f), dot, centre + Offset(cos(angle), sin(angle)) * distance)
        }
        val minuteAngle = time.minute / 60f * 2f * PI.toFloat() - PI.toFloat() / 2f
        val hourAngle = ((time.hour % 12) + time.minute / 60f) / 12f * 2f * PI.toFloat() - PI.toFloat() / 2f
        drawLine(color, centre, centre + Offset(cos(hourAngle), sin(hourAngle)) * radius * 0.5f, strokeWidth = radius * 0.06f, cap = StrokeCap.Round)
        drawLine(color, centre, centre + Offset(cos(minuteAngle), sin(minuteAngle)) * radius * 0.75f, strokeWidth = radius * 0.035f, cap = StrokeCap.Round)
        drawCircle(accent, radius * 0.05f, centre)
    }
}
