package io.github.imlemos.clock.alarm.layout.editor

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.layout.ClockFont
import io.github.imlemos.clock.alarm.layout.DateArrangement
import io.github.imlemos.clock.alarm.layout.DateParts
import io.github.imlemos.clock.alarm.layout.render.rememberClockFontFamily
import io.github.imlemos.clock.alarm.layout.render.spec
import io.github.imlemos.clock.alarm.ui.Label
import io.github.imlemos.clock.core.designsystem.animatedColor
import io.github.imlemos.clock.core.designsystem.component.ColorPickerDialog
import io.github.imlemos.clock.core.designsystem.component.DotDigits
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.rememberHaptics
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme

// Controls shared by the layout editor and the widget editor.

/** The bundled clock fonts, each shown in itself; the chosen one outlined in the accent colour. */
@Composable
internal fun FontChips(selected: ClockFont, onSelect: (ClockFont) -> Unit) {
    val colors = ClockTheme.colors
    val haptics = rememberHaptics()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClockFont.entries.forEach { font ->
            val isSelected = font == selected
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) colors.accent.copy(alpha = 0.18f) else colors.content.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                    .border(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.accent else colors.content.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .selectable(selected = isSelected, role = Role.RadioButton) {
                        if (!isSelected) haptics.tick()
                        onSelect(font)
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Each font shows itself; Doto through DotDigits, which draws round colon dots.
                val sample = TextStyle(fontFamily = rememberClockFontFamily(font, font.spec.defaultWeight), fontSize = 22.sp, lineHeight = 26.sp)
                val sampleColor = colors.content
                if (font == ClockFont.DOTO) {
                    DotDigits("12:30", style = sample, color = sampleColor, minFontSize = 22.sp)
                } else {
                    Text("12:30", style = sample, color = sampleColor, maxLines = 1)
                }
                Text(
                    stringResource(font.nameRes),
                    style = ClockTheme.typography.caption,
                    color = if (isSelected) colors.content else colors.contentMuted,
                )
            }
        }
    }
}

internal val ClockFont.nameRes: Int
    get() = when (this) {
        ClockFont.DOTO -> R.string.font_dots
        ClockFont.MONO -> R.string.font_mono
        ClockFont.OUTFIT -> R.string.font_geometric
        ClockFont.SERIF -> R.string.font_serif
        ClockFont.ORBITRON -> R.string.font_futuristic
        ClockFont.CONDENSED -> R.string.font_condensed
        ClockFont.ROUNDED -> R.string.font_rounded
    }


/** The current colour (or "Automatic colour"); tapping opens the full colour picker. */
@Composable
internal fun ColorSwatches(selected: Int?, allowAuto: Boolean, showLabel: Boolean = true, onSelect: (Int?) -> Unit) {
    val colors = ClockTheme.colors
    var picking by rememberSaveable { mutableStateOf(false) }
    if (showLabel) Label(stringResource(R.string.layout_color))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CapsuleShape)
            .border(1.dp, colors.outline, CapsuleShape)
            .clickable(role = Role.Button) { picking = true }
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(selected?.let(::Color) ?: Color.Transparent, CircleShape)
                .border(1.dp, colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected == null) Text("A", style = ClockTheme.typography.label, color = colors.content)
        }
        Spacer(Modifier.size(12.dp))
        Text(
            selected?.let { "#%06X · %d%%".format(it and 0xFFFFFF, ((it ushr 24) * 100 / 255)) }
                ?: stringResource(R.string.layout_color_auto),
            style = ClockTheme.typography.body,
            color = colors.content,
        )
    }
    Spacer(Modifier.height(8.dp))
    if (picking) {
        ColorPickerDialog(
            initial = selected,
            allowAuto = allowAuto,
            onPick = { onSelect(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
internal fun sliderColors() = SliderDefaults.colors(
    thumbColor = ClockTheme.colors.accent,
    activeTrackColor = ClockTheme.colors.accent,
    inactiveTrackColor = ClockTheme.colors.outline,
)


/**
 * Which parts of the date show (at least one stays on) and, with the weekday and something else,
 * how they are arranged.
 */
@Composable
internal fun DateOptions(parts: DateParts, onChange: (DateParts) -> Unit) {
    Spacer(Modifier.height(8.dp))
    Label(stringResource(R.string.layout_date_parts))
    val options = listOf(
        stringResource(R.string.layout_date_weekday) to parts.weekday,
        stringResource(R.string.layout_date_day) to parts.day,
        stringResource(R.string.layout_date_month) to parts.month,
        stringResource(R.string.layout_date_year) to parts.year,
    )
    ToggleChips(options) { index, on ->
        val next = when (index) {
            0 -> parts.copy(weekday = on)
            1 -> parts.copy(day = on)
            2 -> parts.copy(month = on)
            else -> parts.copy(year = on)
        }
        if (next.weekday || next.hasRest) onChange(next)
    }
    if (parts.weekday && parts.hasRest) {
        Spacer(Modifier.height(12.dp))
        Label(stringResource(R.string.layout_date_arrangement))
        OptionChips(
            options = listOf(
                DateArrangement.INLINE to stringResource(R.string.layout_date_inline),
                DateArrangement.WEEKDAY_ABOVE to stringResource(R.string.layout_date_weekday_above),
                DateArrangement.WEEKDAY_BELOW to stringResource(R.string.layout_date_weekday_below),
            ),
            selected = parts.arrangement,
            onSelect = { onChange(parts.copy(arrangement = it)) },
        )
    }
    Spacer(Modifier.height(4.dp))
}

/** Several options that can be on together, in the same style as [OptionChips]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToggleChips(options: List<Pair<String, Boolean>>, onToggle: (index: Int, on: Boolean) -> Unit) {
    val colors = ClockTheme.colors
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(12.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, (label, on) ->
            Row(
                Modifier
                    .clip(shape)
                    .background(animatedColor(if (on) colors.accent.copy(alpha = 0.18f) else colors.content.copy(alpha = 0.06f)), shape)
                    .border(if (on) 2.dp else 1.dp, animatedColor(if (on) colors.accent else colors.content.copy(alpha = 0.2f)), shape)
                    .toggleable(value = on, role = Role.Checkbox) {
                        haptics.toggle(it)
                        onToggle(index, it)
                    }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (on) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = ClockTheme.typography.label, color = colors.content)
            }
        }
    }
}

/**
 * Choices for the chosen element (clock style, background type): rounded rectangles, the chosen
 * one outlined in the accent colour with a check.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> OptionChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val colors = ClockTheme.colors
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(12.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Row(
                Modifier
                    .clip(shape)
                    .background(animatedColor(if (isSelected) colors.accent.copy(alpha = 0.18f) else colors.content.copy(alpha = 0.06f)), shape)
                    .border(if (isSelected) 2.dp else 1.dp, animatedColor(if (isSelected) colors.accent else colors.content.copy(alpha = 0.2f)), shape)
                    .selectable(selected = isSelected, role = Role.RadioButton) {
                        if (!isSelected) haptics.tick()
                        onSelect(value)
                    }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelected) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = ClockTheme.typography.label, color = colors.content)
            }
        }
    }
}

/** One tab of [EditorTabRow]. */
internal data class EditorTab<T>(val id: T, @param:StringRes val label: Int, @param:DrawableRes val icon: Int)

/**
 * Tabs: icon and name, the chosen one underlined in the accent colour. They look
 * nothing like the option chips below them, which hold the chosen element's settings.
 */
@Composable
internal fun <T> EditorTabRow(tabs: List<EditorTab<T>>, selected: T, onSelect: (T) -> Unit) {
    val colors = ClockTheme.colors
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            tabs.forEach { (id, label, icon) ->
                val isSelected = id == selected
                val tint = if (isSelected) colors.content else colors.contentMuted
                Column(
                    Modifier
                        .weight(1f)
                        .selectable(selected = isSelected, role = Role.Tab) {
                            if (!isSelected) haptics.tick()
                            onSelect(id)
                        }
                        .padding(top = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(painterResource(icon), contentDescription = null, tint = if (isSelected) colors.accent else tint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(label),
                        style = ClockTheme.typography.caption,
                        color = tint,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = ClockTheme.typography.caption.fontSize),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .size(width = 28.dp, height = 3.dp)
                            .clip(CapsuleShape)
                            .background(if (isSelected) colors.accent else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.content.copy(alpha = 0.16f))
    }
}
