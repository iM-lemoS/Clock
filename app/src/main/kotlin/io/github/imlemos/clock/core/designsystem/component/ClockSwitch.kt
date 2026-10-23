package io.github.imlemos.clock.core.designsystem.component

import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import io.github.imlemos.clock.core.designsystem.rememberHaptics
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme

/** Switch in the app's palette: accent when on (an action point), outline when off. */
@Composable
fun ClockSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = ClockTheme.colors
    // Every switch in the app clicks when it flips (rows toggle it, so the switch itself watches).
    val haptics = rememberHaptics()
    val previous = remember { booleanArrayOf(checked) }
    LaunchedEffect(checked) {
        if (checked != previous[0]) {
            previous[0] = checked
            haptics.toggle(checked)
        }
    }
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onAccent,
            checkedTrackColor = colors.accent,
            checkedBorderColor = colors.accent,
            uncheckedThumbColor = colors.contentMuted,
            uncheckedTrackColor = colors.background,
            uncheckedBorderColor = colors.outline,
            // Forced on by the system: still clearly "on", just dimmed.
            disabledCheckedThumbColor = colors.onAccent,
            disabledCheckedTrackColor = colors.accent.copy(alpha = 0.45f),
            disabledCheckedBorderColor = colors.accent.copy(alpha = 0.45f),
        ),
    )
}
