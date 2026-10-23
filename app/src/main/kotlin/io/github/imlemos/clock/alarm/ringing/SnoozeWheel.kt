package io.github.imlemos.clock.alarm.ringing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.ui.formatSnooze
import io.github.imlemos.clock.core.designsystem.component.WheelPicker
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.glass.GlassBackdrop
import io.github.imlemos.clock.core.designsystem.glass.GlassSurface
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme

/**
 * The snooze wheel (docs/PLANO.md 3.3), opened with a long press on "Snooze": 5 min to 1 h,
 * starting at the alarm's own length. Tapping outside closes it without snoozing.
 */
@Composable
fun SnoozeWheel(
    backdrop: GlassBackdrop,
    initialMinutes: Int,
    onSnooze: (minutes: Int) -> Unit,
    onClose: () -> Unit,
) {
    val resources = LocalResources.current
    val choices = Alarm.snoozeChoices(initialMinutes)
    var index by rememberSaveable { mutableIntStateOf(choices.indexOf(initialMinutes).coerceAtLeast(0)) }
    val colors = ClockTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        GlassSurface(
            backdrop = backdrop,
            shape = RoundedCornerShape(36.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                // Swallow taps so only the scrim closes the wheel.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.snooze_wheel_title), style = ClockTheme.typography.title, color = colors.content)
                WheelPicker(
                    count = choices.size,
                    selected = index,
                    onSelectedChange = { index = it },
                    label = { formatSnooze(resources, choices[it]) },
                    accessibilityLabel = stringResource(R.string.snooze_wheel_title),
                    increaseLabel = stringResource(R.string.wheel_increase),
                    decreaseLabel = stringResource(R.string.wheel_decrease),
                    looping = false,
                    textStyle = ClockTheme.typography.headline,
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clip(CapsuleShape)
                        .background(colors.accent, CapsuleShape)
                        .clickable(role = Role.Button) { onSnooze(choices[index]) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.snooze_wheel_confirm, formatSnooze(resources, choices[index])),
                        style = ClockTheme.typography.title,
                        color = colors.onAccent,
                    )
                }
            }
        }
    }
}
