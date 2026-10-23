package io.github.imlemos.clock.alarm.ringing

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.designsystem.glass.GlassBackdrop
import io.github.imlemos.clock.core.designsystem.glass.GlassSurface
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import kotlin.math.roundToInt

private val TrackHeight = 72.dp
private val ThumbSize = 60.dp
private const val DISMISS_THRESHOLD = 0.8f

/** A fling towards the end this fast (px/s) dismisses from at least half way. */
private const val FLING_VELOCITY = 1500f

/**
 * Slide the thumb to the end to dismiss. Letting go near the end (or flinging towards it) dismisses;
 * anywhere else the thumb always springs back to the start. With TalkBack the whole control is a
 * button whose double-tap dismisses, so no gesture is required.
 */
@Composable
fun DismissSlider(
    backdrop: GlassBackdrop,
    label: String,
    actionLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ClockTheme.colors
    val haptics = LocalHapticFeedback.current
    // Plain state written synchronously by the drag, so no late update can cancel the return
    // animation and leave the thumb stuck half way.
    var offset by remember { mutableFloatStateOf(0f) }
    var dismissed by remember { mutableStateOf(false) }

    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .height(TrackHeight)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = actionLabel
                onClick(label = actionLabel) { onDismiss(); true }
            },
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(6.dp),
        ) {
            val maxOffset = with(LocalDensity.current) { (maxWidth - ThumbSize).toPx() }.coerceAtLeast(1f)
            val progress = (offset / maxOffset).coerceIn(0f, 1f)

            fun dismiss() {
                if (dismissed) return
                dismissed = true
                offset = maxOffset
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onDismiss()
            }

            // The gesture lives on the track, which stays still: on the moving thumb each step would
            // be measured against a thumb that had already moved, and the drag fell behind the finger.
            Box(
                Modifier
                    .fillMaxSize()
                    .draggable(
                        orientation = Orientation.Horizontal,
                        enabled = !dismissed,
                        state = rememberDraggableState { delta ->
                            offset = (offset + delta).coerceIn(0f, maxOffset)
                            // Reaching the end dismisses straight away.
                            if (offset >= maxOffset * 0.98f) dismiss()
                        },
                        onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                        onDragStopped = { velocity ->
                            val fraction = offset / maxOffset
                            if (fraction >= DISMISS_THRESHOLD || (velocity > FLING_VELOCITY && fraction >= 0.5f)) {
                                dismiss()
                            } else if (!dismissed) {
                                animate(offset, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                                    offset = value
                                }
                            }
                        },
                    ),
            )
            Text(
                text = label,
                style = ClockTheme.typography.label,
                color = colors.contentMuted,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = 1f - progress },
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(offset.roundToInt(), 0) }
                    .size(ThumbSize)
                    .background(colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}
