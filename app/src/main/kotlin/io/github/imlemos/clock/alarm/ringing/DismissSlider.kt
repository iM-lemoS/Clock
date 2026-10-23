package io.github.imlemos.clock.alarm.ringing

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val TrackHeight = 72.dp
private val ThumbSize = 60.dp
private const val DISMISS_THRESHOLD = 0.75f

/**
 * Slide the thumb to the end to dismiss. With TalkBack the whole control is a button whose
 * double-tap dismisses, so no gesture is required.
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
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }

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
        BoxWithConstraints(Modifier.padding(6.dp)) {
            val maxOffset = with(LocalDensity.current) { (maxWidth - ThumbSize).toPx() }.coerceAtLeast(1f)
            val progress = offset.value / maxOffset
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
                    .offset { IntOffset(offset.value.roundToInt(), 0) }
                    .size(ThumbSize)
                    .background(colors.accent, CircleShape)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, maxOffset)) }
                        },
                        onDragStopped = {
                            if (offset.value / maxOffset >= DISMISS_THRESHOLD) {
                                offset.animateTo(maxOffset)
                                onDismiss()
                            } else {
                                offset.animateTo(0f)
                            }
                        },
                    ),
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
