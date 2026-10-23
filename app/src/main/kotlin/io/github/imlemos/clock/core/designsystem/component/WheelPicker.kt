package io.github.imlemos.clock.core.designsystem.component

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import kotlin.math.abs

/**
 * A scrolling wheel (like iOS pickers) that snaps the chosen value to the centre.
 *
 * @param looping Wraps around (59 → 00) by repeating the values many times.
 * @param accessibilityLabel What the wheel sets, e.g. "Hours". TalkBack treats the wheel as one
 *   control with the current value and increase/decrease actions, so no scrolling gesture is needed.
 */
@Composable
fun WheelPicker(
    count: Int,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    label: (Int) -> String,
    accessibilityLabel: String,
    increaseLabel: String,
    decreaseLabel: String,
    modifier: Modifier = Modifier,
    looping: Boolean = true,
    visibleItems: Int = 5,
    itemHeight: Dp = 56.dp,
    textStyle: TextStyle = ClockTheme.typography.timeLarge,
) {
    val colors = ClockTheme.colors
    val haptics = LocalHapticFeedback.current
    val virtualCount = if (looping) count * LOOPS else count
    val base = if (looping) (virtualCount / 2) - (virtualCount / 2) % count else 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = base + selected)
    val centered by remember { derivedStateOf { centeredIndex(listState) } }
    val currentSelected by rememberUpdatedState(selected)

    // Report the value under the centre line while scrolling, with a light tick per step.
    LaunchedEffect(centered) {
        val value = centered % count
        if (value != currentSelected) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onSelectedChange(value)
        }
    }
    // Follow external changes (loading an alarm, accessibility actions).
    LaunchedEffect(selected) {
        if (centered % count != selected && !listState.isScrollInProgress) {
            val target = if (looping) centered - centered % count + selected else selected
            listState.animateScrollToItem(target)
        }
    }

    val padding = itemHeight * (visibleItems / 2)
    Box(
        modifier
            .height(itemHeight * visibleItems)
            .clearAndSetSemantics {
                contentDescription = accessibilityLabel
                stateDescription = label(selected)
                customActions = listOf(
                    CustomAccessibilityAction(increaseLabel) { onSelectedChange((selected + 1).mod(count)); true },
                    CustomAccessibilityAction(decreaseLabel) { onSelectedChange((selected - 1).mod(count)); true },
                )
            },
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(vertical = padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(virtualCount) { index ->
                Box(
                    Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .graphicsLayer {
                            // Fade and shrink with the distance from the centre line.
                            val distance = distanceFromCentre(listState, index, itemHeight.toPx())
                            val factor = (1f - distance / (visibleItems / 2 + 0.5f)).coerceIn(0f, 1f)
                            alpha = 0.15f + 0.85f * factor * factor
                            scaleX = 0.8f + 0.2f * factor
                            scaleY = scaleX
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(index % count), style = textStyle, color = colors.content, maxLines = 1)
                }
            }
        }
    }
}

/** Index of the item whose centre is closest to the viewport centre. */
private fun centeredIndex(state: LazyListState): Int {
    val info = state.layoutInfo
    val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - centre) }?.index
        ?: state.firstVisibleItemIndex
}

/** Distance of item [index] from the centre line, in items. */
private fun distanceFromCentre(state: LazyListState, index: Int, itemPx: Float): Float {
    val info = state.layoutInfo
    val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return Float.MAX_VALUE
    return abs(item.offset + item.size / 2f - centre) / itemPx
}

private const val LOOPS = 1_000
