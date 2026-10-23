package io.github.imlemos.clock.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import java.util.Locale

/**
 * Typing the time instead of turning the wheels: hours, the drawn colon and minutes in the same
 * dot font as the wheels, plus AM/PM in 12-hour format. The hours field takes focus and the
 * keyboard opens; after the hours it moves on to the minutes. [hour] is always 0..23.
 */
@Composable
fun KeyboardTimeInput(
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    onChange: (hour: Int, minute: Int) -> Unit,
    labels: TimeWheelLabels,
    modifier: Modifier = Modifier,
) {
    val colors = ClockTheme.colors
    val style = ClockTheme.typography.timeLarge.copy(fontSize = 64.sp, lineHeight = 64.sp, textAlign = TextAlign.Center, color = colors.content)
    // The time being typed lives here: callbacks run before the caller recomposes with new values.
    var h by remember { mutableIntStateOf(hour) }
    var m by remember { mutableIntStateOf(minute) }
    fun shownHour(): Int = if (is24Hour) h else (h % 12).let { if (it == 0) 12 else it }
    val pm = h >= 12
    val hourFocus = remember { FocusRequester() }
    val minuteFocus = remember { FocusRequester() }
    var hourText by remember { mutableStateOf(TextFieldValue(two(shownHour()))) }
    var minuteText by remember { mutableStateOf(TextFieldValue(two(minute))) }
    // Once the hours are complete and focus moves on, the keyboard echoes the field's old text
    // back; that is ignored until the hours field is focused again.
    var hoursDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hourFocus.requestFocus() }

    fun hour24(typed: Int): Int = if (is24Hour) typed else typed % 12 + if (pm) 12 else 0

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        DigitsField(
            value = hourText,
            onValueChange = onValueChange@{ new ->
                if (hoursDone) return@onValueChange
                val digits = new.text.filter(Char::isDigit).take(2)
                hourText = new.copy(text = digits, selection = TextRange(digits.length))
                val typed = digits.toIntOrNull()
                val valid = typed != null && if (is24Hour) typed in 0..23 else typed in 1..12
                if (valid) {
                    h = hour24(typed)
                    onChange(h, m)
                }
                // Two digits, or one that cannot start a two-digit hour, completes the hours.
                val complete = digits.length == 2 || (typed != null && typed > if (is24Hour) 2 else 1)
                if (valid && complete) {
                    hoursDone = true
                    minuteFocus.requestFocus()
                }
            },
            onFocus = { focused ->
                if (focused) hoursDone = false
                hourText = if (focused) hourText.copy(selection = TextRange(0, hourText.text.length)) else TextFieldValue(two(shownHour()))
            },
            description = labels.hours,
            style = style,
            imeAction = ImeAction.Next,
            onImeAction = { minuteFocus.requestFocus() },
            modifier = Modifier.focusRequester(hourFocus),
        )
        DotColon(style, colors.content)
        DigitsField(
            value = minuteText,
            onValueChange = { new ->
                val digits = new.text.filter(Char::isDigit).take(2)
                minuteText = new.copy(text = digits, selection = TextRange(digits.length))
                digits.toIntOrNull()?.takeIf { it in 0..59 }?.let {
                    m = it
                    onChange(h, m)
                }
            },
            onFocus = { focused ->
                minuteText = if (focused) minuteText.copy(selection = TextRange(0, minuteText.text.length)) else TextFieldValue(two(m))
            },
            description = labels.minutes,
            style = style,
            imeAction = ImeAction.Done,
            onImeAction = null,
            modifier = Modifier.focusRequester(minuteFocus),
        )
        if (!is24Hour) {
            Spacer(Modifier.width(12.dp))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(false to labels.am, true to labels.pm).forEach { (isPm, label) ->
                    val selected = isPm == pm
                    Box(
                        Modifier
                            .clip(CapsuleShape)
                            .background(if (selected) colors.content else colors.background, CapsuleShape)
                            .border(1.dp, if (selected) colors.content else colors.outline, CapsuleShape)
                            .selectable(selected = selected, role = Role.RadioButton) {
                                h = h % 12 + if (isPm) 12 else 0
                                onChange(h, m)
                            }
                            .heightIn(min = 40.dp)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = ClockTheme.typography.label, color = if (selected) colors.background else colors.content)
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitsField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onFocus: (Boolean) -> Unit,
    description: String,
    style: androidx.compose.ui.text.TextStyle,
    imeAction: ImeAction,
    onImeAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = ClockTheme.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onImeAction?.invoke() }),
        modifier = modifier
            .width(128.dp)
            .clip(shape)
            .background(colors.surface, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.accent else colors.outline, shape)
            .onFocusChanged {
                if (it.isFocused != focused) {
                    focused = it.isFocused
                    onFocus(it.isFocused)
                }
            }
            .padding(vertical = 18.dp)
            .semantics { contentDescription = description },
    )
}

private fun two(value: Int): String = "%02d".format(Locale.ROOT, value)
