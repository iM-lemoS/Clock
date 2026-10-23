package io.github.imlemos.clock.core.designsystem.component

import io.github.imlemos.clock.core.designsystem.rememberHaptics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Seven toggles starting on the locale's first day of the week. */
@Composable
fun WeekdayPicker(
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val colors = ClockTheme.colors
    val first = WeekFields.of(locale).firstDayOfWeek
    val haptics = rememberHaptics()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0L until 7L).map { first.plus(it) }.forEach { day ->
            val isSelected = day in selected
            val fullName = day.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(if (isSelected) colors.accent else colors.background, CircleShape)
                    .border(1.dp, if (isSelected) colors.accent else colors.outline, CircleShape)
                    .toggleable(value = isSelected, role = Role.Checkbox, onValueChange = {
                        haptics.toggle(it)
                        onToggle(day)
                    })
                    .semantics { contentDescription = fullName },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.getDisplayName(TextStyle.SHORT_STANDALONE, locale).take(3).trimEnd('.'),
                    style = ClockTheme.typography.caption,
                    color = if (isSelected) colors.onAccent else colors.content,
                    maxLines = 1,
                )
            }
        }
    }
}
