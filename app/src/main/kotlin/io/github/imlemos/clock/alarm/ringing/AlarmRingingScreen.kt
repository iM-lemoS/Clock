package io.github.imlemos.clock.alarm.ringing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.ui.formatAlarmTime
import io.github.imlemos.clock.alarm.ui.formatTimeOfDay
import io.github.imlemos.clock.alarm.ui.timeParts
import io.github.imlemos.clock.core.designsystem.component.DotGrid
import io.github.imlemos.clock.core.designsystem.component.DotTime
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.glass.GlassSurface
import io.github.imlemos.clock.core.designsystem.glass.glassBackdropSource
import io.github.imlemos.clock.core.designsystem.glass.rememberGlassBackdrop
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun AlarmRingingScreen(session: RingingSession, onDismiss: () -> Unit, onSnooze: () -> Unit) {
    val context = LocalContext.current
    val colors = ClockTheme.colors
    val backdrop = rememberGlassBackdrop()
    val now by rememberCurrentMinute()
    val nowTime = TimeOfDay(now.hour, now.minute)
    val parts = timeParts(context, nowTime)
    val alarm = session.primary
    val dismissLabel = stringResource(R.string.alarm_action_dismiss)
    val snoozeLabel = pluralStringResource(R.plurals.alarm_action_snooze_minutes, alarm.snoozeMinutes, alarm.snoozeMinutes)

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            // TalkBack users get both actions directly, without gestures (docs/PLANO.md 3.3).
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(dismissLabel) { onDismiss(); true },
                    CustomAccessibilityAction(snoozeLabel) { onSnooze(); true },
                )
            },
    ) {
        DotGrid(Modifier.fillMaxSize().glassBackdropSource(backdrop), fadeStart = 0f)

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            DotTime(
                hours = parts.hours,
                minutes = parts.minutes,
                dayPeriod = parts.dayPeriod,
                contentDescription = formatTimeOfDay(context, nowTime),
                style = ClockTheme.typography.timeDisplay,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(now),
                style = ClockTheme.typography.body,
                color = colors.contentMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            session.alarms.forEach { ringing ->
                Text(
                    text = ringing.label.ifBlank { stringResource(R.string.alarm_notification_title) },
                    style = ClockTheme.typography.headline,
                    color = colors.content,
                    textAlign = TextAlign.Center,
                )
                if (ringing.zone != null) {
                    Text(
                        text = formatAlarmTime(context, ringing.time, ringing.zone),
                        style = ClockTheme.typography.body,
                        color = colors.contentMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (session.silenced) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.alarm_silenced),
                    style = ClockTheme.typography.label,
                    color = colors.contentMuted,
                )
            }
            Spacer(Modifier.weight(1.4f))

            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier
                    .clip(CapsuleShape)
                    .clickable(role = Role.Button, onClick = onSnooze)
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 32.dp),
            ) {
                Text(snoozeLabel, style = ClockTheme.typography.title, color = colors.content)
            }
            Spacer(Modifier.height(20.dp))
            DismissSlider(
                backdrop = backdrop,
                label = stringResource(R.string.alarm_slide_to_dismiss),
                actionLabel = dismissLabel,
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The current local time, updated at each minute boundary. */
@Composable
private fun rememberCurrentMinute() = remember { mutableStateOf(LocalDateTime.now()) }.also { state ->
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            state.value = now
            delay((60 - now.second) * 1_000L - now.nano / 1_000_000L)
        }
    }
}

