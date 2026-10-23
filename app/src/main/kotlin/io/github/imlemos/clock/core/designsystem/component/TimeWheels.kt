package io.github.imlemos.clock.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme

/** Labels for the time wheels, all from resources. */
data class TimeWheelLabels(
    val hours: String,
    val minutes: String,
    val period: String,
    val am: String,
    val pm: String,
    val increase: String,
    val decrease: String,
)

/**
 * Hour and minute wheels (plus AM/PM in 12-hour format) with a highlighted centre row.
 * [hour] is always 0..23.
 */
@Composable
fun TimeWheels(
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    onChange: (hour: Int, minute: Int) -> Unit,
    labels: TimeWheelLabels,
    modifier: Modifier = Modifier,
) {
    val colors = ClockTheme.colors
    val style = ClockTheme.typography.timeLarge
    val itemHeight = 60.dp
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // The selection band behind the centre row.
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(colors.surface, CapsuleShape),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (is24Hour) {
                WheelPicker(
                    count = 24,
                    selected = hour,
                    onSelectedChange = { onChange(it, minute) },
                    label = { "%02d".format(it) },
                    accessibilityLabel = labels.hours,
                    increaseLabel = labels.increase,
                    decreaseLabel = labels.decrease,
                    itemHeight = itemHeight,
                    textStyle = style,
                    modifier = Modifier.width(96.dp),
                )
            } else {
                WheelPicker(
                    count = 12,
                    selected = (hour % 12),
                    onSelectedChange = { index -> onChange(index + if (hour >= 12) 12 else 0, minute) },
                    label = { if (it == 0) "12" else it.toString() },
                    accessibilityLabel = labels.hours,
                    increaseLabel = labels.increase,
                    decreaseLabel = labels.decrease,
                    itemHeight = itemHeight,
                    textStyle = style,
                    modifier = Modifier.width(96.dp),
                )
            }
            DotColon(style, colors.content)
            WheelPicker(
                count = 60,
                selected = minute,
                onSelectedChange = { onChange(hour, it) },
                label = { "%02d".format(it) },
                accessibilityLabel = labels.minutes,
                increaseLabel = labels.increase,
                decreaseLabel = labels.decrease,
                itemHeight = itemHeight,
                textStyle = style,
                modifier = Modifier.width(96.dp),
            )
            if (!is24Hour) {
                Spacer(Modifier.width(8.dp))
                WheelPicker(
                    count = 2,
                    selected = if (hour >= 12) 1 else 0,
                    onSelectedChange = { period -> onChange(hour % 12 + if (period == 1) 12 else 0, minute) },
                    label = { if (it == 0) labels.am else labels.pm },
                    accessibilityLabel = labels.period,
                    increaseLabel = labels.increase,
                    decreaseLabel = labels.decrease,
                    looping = false,
                    itemHeight = itemHeight,
                    textStyle = ClockTheme.typography.title,
                    modifier = Modifier
                        .width(64.dp)
                        .padding(start = 4.dp),
                )
            }
        }
    }
}

/** Hours, minutes and seconds wheels for timers (docs/PLANO.md 2: the same wheels as alarms). */
@Composable
fun DurationWheels(
    hours: Int,
    minutes: Int,
    seconds: Int,
    onChange: (hours: Int, minutes: Int, seconds: Int) -> Unit,
    labels: DurationWheelLabels,
    modifier: Modifier = Modifier,
) {
    val colors = ClockTheme.colors
    val style = ClockTheme.typography.timeLarge
    val itemHeight = 60.dp
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(colors.surface, CapsuleShape),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(
                Triple(24, hours, labels.hours),
                Triple(60, minutes, labels.minutes),
                Triple(60, seconds, labels.seconds),
            ).forEachIndexed { index, (count, value, label) ->
                if (index > 0) DotColon(style, colors.content)
                WheelPicker(
                    count = count,
                    selected = value,
                    onSelectedChange = { new ->
                        when (index) {
                            0 -> onChange(new, minutes, seconds)
                            1 -> onChange(hours, new, seconds)
                            else -> onChange(hours, minutes, new)
                        }
                    },
                    label = { "%02d".format(java.util.Locale.ROOT, it) },
                    accessibilityLabel = label,
                    increaseLabel = labels.increase,
                    decreaseLabel = labels.decrease,
                    itemHeight = itemHeight,
                    textStyle = style,
                    modifier = Modifier.width(88.dp),
                )
            }
        }
    }
}

/** Accessibility names for [DurationWheels]. */
data class DurationWheelLabels(
    val hours: String,
    val minutes: String,
    val seconds: String,
    val increase: String,
    val decrease: String,
)
