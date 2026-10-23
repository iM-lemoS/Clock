package io.github.imlemos.clock.alarm.layout.render

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.layout.Box2
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

/**
 * Hooks for the layout editor: elements become selectable, draggable and pinch-resizable. With
 * [onBackgroundTransform], dragging and pinching empty space moves and zooms a photo or video.
 */
class LayoutEditing(
    val selected: LayoutElementId?,
    val onSelect: (LayoutElementId?) -> Unit,
    val onPlacementChange: (LayoutElementId, Placement) -> Unit,
    val onBackgroundTransform: ((dx: Float, dy: Float, zoom: Float, area: Size2, mediaAspect: Float) -> Unit)? = null,
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
    val elementNames = mapOf(
        LayoutElementId.CLOCK to stringResource(R.string.layout_tab_clock),
        LayoutElementId.DATE to stringResource(R.string.layout_tab_date),
        LayoutElementId.LABEL to stringResource(R.string.layout_tab_label),
        LayoutElementId.CONTROLS to stringResource(R.string.layout_tab_buttons),
    )
    val editActions = listOf(
        stringResource(R.string.layout_move_up) to EditStep(dy = -NUDGE),
        stringResource(R.string.layout_move_down) to EditStep(dy = NUDGE),
        stringResource(R.string.layout_move_left) to EditStep(dx = -NUDGE),
        stringResource(R.string.layout_move_right) to EditStep(dx = NUDGE),
        stringResource(R.string.layout_bigger) to EditStep(scale = 1.1f),
        stringResource(R.string.layout_smaller) to EditStep(scale = 1f / 1.1f),
    )


    fun placementOf(id: LayoutElementId): Placement = when (id) {
        LayoutElementId.CLOCK -> currentSpec.clock.placement
        LayoutElementId.DATE -> currentSpec.date.placement
        LayoutElementId.LABEL -> currentSpec.label.placement
        LayoutElementId.CONTROLS -> currentSpec.controls.placement
    }

    @Composable
    fun Element(id: LayoutElementId, visible: Boolean, content: @Composable () -> Unit) {
        // Hidden elements disappear in the editor too; the panel's switch brings them back.
        if (!visible) {
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
                        // The outline sits just outside the element so it never covers its edges.
                        .drawWithContent {
                            drawContent()
                            if (selected) {
                                val inset = 6.dp.toPx()
                                drawRoundRect(
                                    color = colors.accent,
                                    topLeft = Offset(-inset, -inset),
                                    size = Size(size.width + 2 * inset, size.height + 2 * inset),
                                    cornerRadius = CornerRadius(12.dp.toPx()),
                                    style = Stroke(2.dp.toPx()),
                                )
                            }
                        }
                        // TalkBack: every move and resize is also an action, no gestures needed.
                        .semantics {
                            contentDescription = elementNames.getValue(id)
                            this.selected = selected
                            customActions = editActions.map { (label, step) ->
                                CustomAccessibilityAction(label) {
                                    val (size, area) = measured[id] ?: return@CustomAccessibilityAction false
                                    val current = placementOf(id)
                                    val moved = LayoutGeometry.moved(current, step.dx * area.width, step.dy * area.height, size, area)
                                    val (min, max) = scaleRange(id)
                                    editing.onSelect(id)
                                    editing.onPlacementChange(id, moved.copy(scale = (current.scale * step.scale).coerceIn(min, max)))
                                    true
                                }
                            }
                        }
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
        var mediaAspect by remember { mutableFloatStateOf(0f) }
        LayoutBackground(
            background = spec.background,
            modifier = Modifier.fillMaxSize().glassBackdropSource(backdrop),
            animate = animate,
            onMediaAspect = { mediaAspect = it },
        )
        if (editing != null) {
            val onBackground = editing.onBackgroundTransform
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { editing.onSelect(null) } }
                    .then(
                        if (onBackground == null) {
                            Modifier
                        } else {
                            Modifier.pointerInput(onBackground) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    onBackground(pan.x, pan.y, zoom, Size2(size.width.toFloat(), size.height.toFloat()), mediaAspect)
                                }
                            }
                        },
                    ),
            )
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
                        )
                    }
                },
                {
                    Element(LayoutElementId.LABEL, spec.label.visible) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            val visible = placed.filter { (_, placeable) -> placeable != null && placeable.width > 0 && placeable.height > 0 }
            val scales = visible.map { (id, placeable) ->
                val base = Size2(placeable!!.width.toFloat(), placeable.height.toFloat())
                val (min, max) = scaleRange(id)
                LayoutGeometry.effectiveScale(placementOf(id).scale, base, area, min, max)
            }
            val boxes = visible.mapIndexed { index, (id, placeable) ->
                val scaled = Size2(placeable!!.width * scales[index], placeable.height * scales[index])
                measured[id] = scaled to area
                val topLeft = LayoutGeometry.topLeft(placementOf(id), scaled, area)
                Box2(topLeft.x, topLeft.y, scaled.width, scaled.height)
            }
            // Relative positions can collide on short screens or with large fonts: push apart.
            val separated = LayoutGeometry.separate(boxes, area, ELEMENT_GAP.toPx())
            layout(constraints.maxWidth, constraints.maxHeight) {
                visible.forEachIndexed { index, (_, placeable) ->
                    val box = separated[index]
                    val scale = scales[index]
                    // The layer scales around the centre; shift so the scaled box lands on the box.
                    val x = box.x - (placeable!!.width - box.width) / 2f
                    val y = box.y - (placeable.height - box.height) / 2f
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


private val CONTROLS_WIDTH = 340.dp
private val ELEMENT_GAP = 8.dp

/** One accessibility edit: a move as a fraction of the area and a scale factor. */
private data class EditStep(val dx: Float = 0f, val dy: Float = 0f, val scale: Float = 1f)

private const val NUDGE = 0.04f
