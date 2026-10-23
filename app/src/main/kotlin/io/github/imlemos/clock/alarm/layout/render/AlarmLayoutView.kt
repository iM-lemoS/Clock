package io.github.imlemos.clock.alarm.layout.render

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.alarm.layout.LayoutGeometry
import io.github.imlemos.clock.alarm.layout.LayoutSpec
import io.github.imlemos.clock.alarm.layout.Placement
import io.github.imlemos.clock.alarm.layout.Size2
import io.github.imlemos.clock.core.designsystem.glass.GlassBackdrop
import io.github.imlemos.clock.core.designsystem.glass.glassBackdropSource
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import kotlin.math.roundToInt

enum class LayoutElementId { CLOCK, DATE, LABEL, CONTROLS }

/** A ringing alarm's label, with an optional second line (its time in another zone). */
data class LayoutLabel(val text: String, val detail: String? = null)

/** Hooks for the layout editor: elements become selectable, draggable and pinch-resizable. */
class LayoutEditing(
    val selected: LayoutElementId?,
    val onSelect: (LayoutElementId?) -> Unit,
    val onPlacementChange: (LayoutElementId, Placement) -> Unit,
)

/**
 * Renders a [LayoutSpec] full screen (docs/PLANO.md 3.7). Used by the alarm screen, the "Test"
 * preview and the editor. Elements sit at relative positions inside the safe area and are always
 * kept entirely inside it ([LayoutGeometry]).
 */
@Composable
fun AlarmLayoutView(
    spec: LayoutSpec,
    time: TimeOfDay,
    date: String,
    labels: List<LayoutLabel>,
    backdrop: GlassBackdrop,
    controls: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    editing: LayoutEditing? = null,
) {
    val colors = ClockTheme.colors
    val currentSpec by rememberUpdatedState(spec)
    // Scaled element sizes and the area, written while measuring, read by the editor's gestures.
    val measured = remember { mutableMapOf<LayoutElementId, Pair<Size2, Size2>>() }

    fun placementOf(id: LayoutElementId): Placement = when (id) {
        LayoutElementId.CLOCK -> currentSpec.clock.placement
        LayoutElementId.DATE -> currentSpec.date.placement
        LayoutElementId.LABEL -> currentSpec.label.placement
        LayoutElementId.CONTROLS -> currentSpec.controls.placement
    }

    @Composable
    fun Element(id: LayoutElementId, visible: Boolean, content: @Composable () -> Unit) {
        if (!visible && editing == null) {
            Box {}
            return
        }
        Box(contentAlignment = Alignment.Center) {
            content()
            if (editing != null) {
                val selected = editing.selected == id
                Box(
                    Modifier
                        .matchParentSize()
                        .then(
                            if (selected) Modifier.border(2.dp, colors.accent, RoundedCornerShape(12.dp)) else Modifier,
                        )
                        .pointerInput(id) { detectTapGestures { editing.onSelect(id) } }
                        .pointerInput(id) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                editing.onSelect(id)
                                val (size, area) = measured[id] ?: return@detectTransformGestures
                                val current = placementOf(id)
                                val baseScale = current.scale
                                // Pan arrives in the element's own (scaled) coordinates.
                                val moved = LayoutGeometry.moved(current, pan.x * baseScale, pan.y * baseScale, size, area)
                                val (min, max) = scaleRange(id)
                                editing.onPlacementChange(id, moved.copy(scale = (baseScale * zoom).coerceIn(min, max)))
                            }
                        },
                )
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        LayoutBackground(spec.background, Modifier.fillMaxSize().glassBackdropSource(backdrop), animate)
        if (editing != null) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { editing.onSelect(null) } })
        }
        val ids = LayoutElementId.entries
        Layout(
            contents = listOf(
                {
                    Element(LayoutElementId.CLOCK, true) {
                        ClockFace(spec.clock.style, time, spec.clock.color?.let(::Color) ?: colors.content, colors.accent)
                    }
                },
                {
                    Element(LayoutElementId.DATE, spec.date.visible) {
                        Text(
                            date,
                            style = ClockTheme.typography.body,
                            color = spec.date.color?.let(::Color) ?: colors.contentMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.alphaIf(!spec.date.visible),
                        )
                    }
                },
                {
                    Element(LayoutElementId.LABEL, spec.label.visible) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alphaIf(!spec.label.visible)) {
                            val color = spec.label.color?.let(::Color) ?: colors.content
                            labels.forEach { label ->
                                Text(label.text, style = ClockTheme.typography.headline, color = color, textAlign = TextAlign.Center)
                                label.detail?.let {
                                    Text(it, style = ClockTheme.typography.body, color = color.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                },
                { Element(LayoutElementId.CONTROLS, true) { controls() } },
            ),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        ) { measurables, constraints ->
            val area = Size2(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            val controlsWidth = minOf(constraints.maxWidth, CONTROLS_WIDTH.roundToPx())
            val placed = ids.mapIndexed { index, id ->
                val child = measurables[index].firstOrNull()
                val maxWidth = if (id == LayoutElementId.CONTROLS) controlsWidth else constraints.maxWidth
                val placeable = child?.measure(
                    Constraints(
                        minWidth = if (id == LayoutElementId.CONTROLS) controlsWidth else 0,
                        maxWidth = maxWidth,
                        maxHeight = constraints.maxHeight,
                    ),
                )
                id to placeable
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placed.forEach { (id, placeable) ->
                    placeable ?: return@forEach
                    val placement = placementOf(id)
                    val base = Size2(placeable.width.toFloat(), placeable.height.toFloat())
                    val (min, max) = scaleRange(id)
                    val scale = LayoutGeometry.effectiveScale(placement.scale, base, area, min, max)
                    val scaled = Size2(base.width * scale, base.height * scale)
                    measured[id] = scaled to area
                    val topLeft = LayoutGeometry.topLeft(placement, scaled, area)
                    // The layer scales around the centre; shift so the scaled box lands on topLeft.
                    val x = topLeft.x - (base.width - scaled.width) / 2f
                    val y = topLeft.y - (base.height - scaled.height) / 2f
                    placeable.placeWithLayer(x.roundToInt(), y.roundToInt()) {
                        scaleX = scale
                        scaleY = scale
                    }
                }
            }
        }
    }
}

private fun scaleRange(id: LayoutElementId): Pair<Float, Float> =
    if (id == LayoutElementId.CONTROLS) {
        LayoutGeometry.CONTROLS_MIN_SCALE to LayoutGeometry.CONTROLS_MAX_SCALE
    } else {
        LayoutGeometry.MIN_SCALE to LayoutGeometry.MAX_SCALE
    }

/** Hidden elements are shown faded in the editor so they can be found and switched back on. */
private fun Modifier.alphaIf(faded: Boolean): Modifier = if (faded) alpha(0.3f) else this

private val CONTROLS_WIDTH = 340.dp
