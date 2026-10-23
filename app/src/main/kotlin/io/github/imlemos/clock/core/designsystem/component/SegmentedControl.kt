package io.github.imlemos.clock.core.designsystem.component

import io.github.imlemos.clock.core.designsystem.animatedColor
import io.github.imlemos.clock.core.designsystem.rememberHaptics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme

/** Solid capsule with mutually exclusive options; the selected one is inverted. */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ClockTheme.colors
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CapsuleShape)
            .border(1.dp, colors.outline, CapsuleShape)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(CapsuleShape)
                    .background(animatedColor(if (isSelected) colors.content else colors.background), CapsuleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = {
                        if (!isSelected) haptics.tick()
                        onSelect(value)
                    })
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = ClockTheme.typography.label,
                    color = animatedColor(if (isSelected) colors.background else colors.content),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}
