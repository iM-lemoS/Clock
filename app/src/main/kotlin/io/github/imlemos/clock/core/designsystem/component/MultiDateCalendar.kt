package io.github.imlemos.clock.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Month calendar where any number of days can be toggled (docs/PLANO.md 3.1: "datas específicas").
 * Days before [minDate] cannot be selected.
 */
@Composable
fun MultiDateCalendar(
    selected: Set<LocalDate>,
    onToggle: (LocalDate) -> Unit,
    minDate: LocalDate,
    locale: Locale,
    previousLabel: String,
    nextLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = ClockTheme.colors
    var month by rememberSaveable { mutableStateOf(YearMonth.from(selected.minOrNull() ?: minDate).toString()) }
    val shown = YearMonth.parse(month)
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val leadingBlanks = (shown.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val cells: List<LocalDate?> = List(leadingBlanks) { null } + (1..shown.lengthOfMonth()).map(shown::atDay)
    val dayFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { month = shown.minusMonths(1).toString() },
                enabled = shown > YearMonth.from(minDate),
            ) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = previousLabel,
                    tint = colors.content,
                    modifier = Modifier.graphicsLayer(scaleX = -1f),
                )
            }
            Text(
                text = DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(shown).replaceFirstChar { it.titlecase(locale) },
                style = ClockTheme.typography.title,
                color = colors.content,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { month = shown.plusMonths(1).toString() }) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = nextLabel, tint = colors.content)
            }
        }
        Row(Modifier.clearAndSetSemantics { }) {
            (0L until 7L).map { firstDayOfWeek.plus(it) }.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                    style = ClockTheme.typography.caption,
                    color = colors.contentMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                week.forEach { date -> DayCell(date, date in selected, date != null && date >= minDate, onToggle, dayFormatter, Modifier.weight(1f)) }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    isSelected: Boolean,
    enabled: Boolean,
    onToggle: (LocalDate) -> Unit,
    formatter: DateTimeFormatter,
    modifier: Modifier,
) {
    val colors = ClockTheme.colors
    if (date == null) {
        Spacer(modifier.aspectRatio(1f))
        return
    }
    val today = date == LocalDate.now()
    Box(
        modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(if (isSelected) colors.accent else colors.background, CircleShape)
            .border(1.dp, if (today && !isSelected) colors.content else colors.background, CircleShape)
            .toggleable(value = isSelected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle(date) })
            .semantics { contentDescription = formatter.format(date) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = ClockTheme.typography.body,
            color = when {
                isSelected -> colors.onAccent
                enabled -> colors.content
                else -> colors.contentFaint
            },
        )
    }
}
