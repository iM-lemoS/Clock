package io.github.imlemos.clock.alarm.ringing

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import io.github.imlemos.clock.alarm.model.Alarm
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.layout.LayoutSpec
import io.github.imlemos.clock.alarm.layout.render.AlarmLayoutView
import io.github.imlemos.clock.alarm.layout.render.LayoutLabel
import io.github.imlemos.clock.core.designsystem.glass.GlassBackdrop
import io.github.imlemos.clock.alarm.ui.formatAlarmTime
import io.github.imlemos.clock.alarm.ui.formatSnooze
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

/**
 * The alarm screen, drawn with the alarm's layout (docs/PLANO.md 3.7). Also used by "Test alarm",
 * where dismissing and snoozing just close the preview.
 */
@Composable
fun AlarmRingingScreen(
    session: RingingSession,
    spec: LayoutSpec,
    onDismiss: () -> Unit,
    onSnooze: (minutes: Int?) -> Unit,
) {
    val context = LocalContext.current
    val backdrop = rememberGlassBackdrop()
    val now by rememberCurrentMinute()
    val alarm = session.primary
    val dismissLabel = stringResource(R.string.alarm_action_dismiss)
    val snoozeLabel = stringResource(R.string.alarm_action_snooze, formatSnooze(LocalResources.current, alarm.snoozeMinutes))
    val chooseLabel = stringResource(R.string.snooze_choose)
    var choosingSnooze by rememberSaveable { mutableStateOf(false) }
    var dial by remember { mutableStateOf<SnoozeDialState?>(null) }
    // A custom snooze length joins the usual ones on the dial.
    val snoozeChoices = remember(alarm.snoozeMinutes) { Alarm.snoozeChoices(alarm.snoozeMinutes) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val window = LocalWindowInfo.current
    val defaultLabel = stringResource(R.string.alarm_notification_title)
    val labels = session.alarms.map { ringing ->
        LayoutLabel(
            text = ringing.label.ifBlank { defaultLabel },
            detail = ringing.zone?.let { formatAlarmTime(context, ringing.time, it) },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            // TalkBack users get both actions directly, without gestures (docs/PLANO.md 3.3).
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(dismissLabel) { onDismiss(); true },
                    CustomAccessibilityAction(snoozeLabel) { onSnooze(null); true },
                    CustomAccessibilityAction(chooseLabel) { choosingSnooze = true; true },
                )
            },
    ) {
        AlarmLayoutView(
            spec = spec,
            time = TimeOfDay(now.hour, now.minute),
            date = now.toLocalDate(),
            labels = labels,
            backdrop = backdrop,
            controls = {
                AlarmControls(
                    backdrop = backdrop,
                    snoozeLabel = snoozeLabel,
                    chooseLabel = chooseLabel,
                    dismissLabel = dismissLabel,
                    note = if (session.silenced) stringResource(R.string.alarm_silenced) else null,
                    onSnooze = { onSnooze(null) },
                    onChooseSnooze = { choosingSnooze = true },
                    onDismiss = onDismiss,
                    onDialStart = { center ->
                        val width = window.containerSize.width.toFloat()
                        val edge = with(density) { DIAL_EDGE.toPx() }
                        // As wide as the screen allows, and the whole half circle on screen.
                        val radius = minOf(with(density) { DIAL_RADIUS.toPx() }, width / 2f - edge)
                        val reach = radius + edge
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        dial = SnoozeDialState(
                            center = center.copy(x = center.x.coerceIn(reach, (width - reach).coerceAtLeast(reach))),
                            up = center.y > reach + with(density) { 80.dp.toPx() },
                            radius = radius,
                        )
                    },
                    onDialMove = { finger ->
                        dial?.let { current ->
                            val offset = finger - current.center
                            val choice = SnoozeDialMath.choiceAt(
                                offset.x, offset.y, snoozeChoices.size, with(density) { DIAL_DEAD_ZONE.toPx() }, current.up,
                            )
                            if (choice != current.selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                dial = current.copy(selected = choice)
                            }
                        }
                    },
                    onDialEnd = {
                        val choice = dial?.selected
                        dial = null
                        if (choice != null) onSnooze(snoozeChoices[choice])
                    },
                )
            },
        )
        dial?.let { SnoozeDialOverlay(it, snoozeChoices) }
        if (choosingSnooze) {
            SnoozeWheel(
                backdrop = backdrop,
                initialMinutes = alarm.snoozeMinutes,
                onSnooze = { minutes -> choosingSnooze = false; onSnooze(minutes) },
                onClose = { choosingSnooze = false },
            )
        }
    }
}

/**
 * Snooze and the dismiss slider. A tap on Snooze uses the alarm's length; holding it opens the
 * dial ([onDialStart] with the button's centre in root coordinates), the finger moves over it
 * ([onDialMove]) and letting go chooses ([onDialEnd]). TalkBack gets the accessible wheel through
 * [onChooseSnooze]. With [interactive] off (the layout editor) they only show how they look.
 */
@Composable
fun AlarmControls(
    backdrop: GlassBackdrop,
    snoozeLabel: String,
    chooseLabel: String,
    dismissLabel: String,
    onSnooze: () -> Unit,
    onChooseSnooze: () -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
    interactive: Boolean = true,
    onDialStart: (Offset) -> Unit = {},
    onDialMove: (Offset) -> Unit = {},
    onDialEnd: () -> Unit = {},
) {
    var buttonCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentOnSnooze by rememberUpdatedState(onSnooze)
    val currentOnDialStart by rememberUpdatedState(onDialStart)
    val currentOnDialMove by rememberUpdatedState(onDialMove)
    val currentOnDialEnd by rememberUpdatedState(onDialEnd)
    val colors = ClockTheme.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (note != null) {
            Text(note, style = ClockTheme.typography.label, color = colors.contentMuted)
            Spacer(Modifier.height(12.dp))
        }
        GlassSurface(
            backdrop = backdrop,
            modifier = Modifier
                .clip(CapsuleShape)
                .onGloballyPositioned { buttonCoordinates = it }
                .semantics {
                    role = Role.Button
                    onClick { onSnooze(); true }
                    onLongClick(chooseLabel) { onChooseSnooze(); true }
                }
                .then(
                    if (!interactive) {
                        Modifier
                    } else {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val held = awaitLongPressOrCancellation(down.id)
                                if (held == null) {
                                    // Released before the long-press timeout: a tap, if it ended on the button.
                                    val up = currentEvent.changes.firstOrNull { it.id == down.id }
                                    val inside = up != null && up.position.x in 0f..size.width.toFloat() && up.position.y in 0f..size.height.toFloat()
                                    if (up != null && up.changedToUp() && !up.isConsumed && inside) currentOnSnooze()
                                    return@awaitEachGesture
                                }
                                val coordinates = buttonCoordinates ?: return@awaitEachGesture
                                currentOnDialStart(coordinates.localToRoot(Offset(size.width / 2f, size.height / 2f)))
                                currentOnDialMove(coordinates.localToRoot(held.position))
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.changedToUp()) break
                                    currentOnDialMove(coordinates.localToRoot(change.position))
                                    change.consume()
                                }
                                currentOnDialEnd()
                            }
                        }
                    },
                )
                .heightIn(min = 64.dp),
        ) {
            // Padding inside the glass, so the label never touches its rim.
            Text(snoozeLabel, style = ClockTheme.typography.title, color = colors.content, modifier = Modifier.padding(horizontal = 32.dp))
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

