package io.github.imlemos.clock.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import java.util.Locale

/**
 * Picks a length of time on wheels: hours and minutes, plus days when [maxDays] > 0. Returns the
 * total in minutes; "Choose" stays off until it is at least [minMinutes].
 */
@Composable
fun DurationPickerDialog(
    title: String,
    initialMinutes: Int,
    onPick: (minutes: Int) -> Unit,
    onDismiss: () -> Unit,
    maxDays: Int = 0,
    minMinutes: Int = 1,
    note: String? = null,
) {
    val start = initialMinutes.coerceIn(0, (maxDays + 1) * MINUTES_PER_DAY - 1)
    var days by rememberSaveable { mutableIntStateOf(if (maxDays > 0) start / MINUTES_PER_DAY else 0) }
    var hours by rememberSaveable { mutableIntStateOf(if (maxDays > 0) start % MINUTES_PER_DAY / 60 else (start / 60).coerceAtMost(23)) }
    var minutes by rememberSaveable { mutableIntStateOf(start % 60) }
    val total = days * MINUTES_PER_DAY + hours * 60 + minutes
    GlassDialog(
        title = title,
        onDismiss = onDismiss,
        actions = listOf(
            DialogAction(stringResource(R.string.action_cancel), onClick = onDismiss),
            DialogAction(stringResource(R.string.color_pick), DialogButtonStyle.Primary, enabled = total >= minMinutes) { onPick(total) },
        ),
    ) {
        val increase = stringResource(R.string.wheel_increase)
        val decrease = stringResource(R.string.wheel_decrease)
        val wheels = buildList {
            if (maxDays > 0) add(Wheel(maxDays + 1, days, stringResource(R.string.wheel_days)) { days = it })
            add(Wheel(24, hours, stringResource(R.string.wheel_hours)) { hours = it })
            add(Wheel(60, minutes, stringResource(R.string.wheel_minutes)) { minutes = it })
        }
        val colors = ClockTheme.colors
        val itemHeight = 52.dp
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(itemHeight).background(colors.content.copy(alpha = 0.08f), CapsuleShape))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                wheels.forEach { wheel ->
                    WheelPicker(
                        count = wheel.count,
                        selected = wheel.value,
                        onSelectedChange = wheel.onChange,
                        label = { "%02d".format(Locale.ROOT, it) },
                        accessibilityLabel = wheel.label,
                        increaseLabel = increase,
                        decreaseLabel = decrease,
                        itemHeight = itemHeight,
                        visibleItems = 3,
                        textStyle = ClockTheme.typography.timeLarge,
                        modifier = Modifier.width(76.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            wheels.forEach { wheel ->
                Text(
                    wheel.label,
                    style = ClockTheme.typography.caption,
                    color = colors.contentMuted,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(76.dp),
                )
            }
        }
        if (note != null) {
            Column(Modifier.padding(top = 16.dp)) {
                Text(note, style = ClockTheme.typography.caption, color = colors.contentMuted)
            }
        }
    }
}

private class Wheel(val count: Int, val value: Int, val label: String, val onChange: (Int) -> Unit)

const val MINUTES_PER_DAY = 24 * 60
