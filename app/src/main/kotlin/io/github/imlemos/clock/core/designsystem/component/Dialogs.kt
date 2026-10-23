package io.github.imlemos.clock.core.designsystem.component

import io.github.imlemos.clock.core.designsystem.rememberHaptics
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.glass.GlassLevel
import io.github.imlemos.clock.core.designsystem.glass.GlassSurface
import io.github.imlemos.clock.core.designsystem.glass.LocalGlassLevel
import io.github.imlemos.clock.core.designsystem.glass.rememberGlassBackdrop
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import kotlinx.coroutines.launch

/** How a [DialogButton] looks: the main action, a secondary one, or a destructive one. */
enum class DialogButtonStyle { Primary, Secondary, Destructive }

/** A button of a dialog's bottom row. */
data class DialogAction(
    val label: String,
    val style: DialogButtonStyle = DialogButtonStyle.Secondary,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** One row of an [OptionsSheet]. [destructive] actions (delete) are shown in the accent colour. */
data class SheetOption(
    val label: String,
    @param:DrawableRes val icon: Int? = null,
    val destructive: Boolean = false,
    /** Marks the current choice in a list of choices. */
    val selected: Boolean = false,
    val summary: String? = null,
    val onClick: () -> Unit,
)

/**
 * A centred glass dialog over a dimmed screen: title, [content], then the [actions] as real
 * buttons under a divider. Tapping outside or Back calls [onDismiss].
 */
@Composable
fun GlassDialog(
    title: String?,
    onDismiss: () -> Unit,
    actions: List<DialogAction>,
    maxWidth: Dp = 420.dp,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Overlay {
        val colors = ClockTheme.colors
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, tween(180)) }
        BackHandler(onBack = onDismiss)
        Scrim(appear.value, onDismiss)
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            OverlayGlass(
                shape = DialogShape,
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = appear.value
                        val scale = 0.94f + 0.06f * appear.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .semantics { title?.let { paneTitle = it } },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    if (title != null) {
                        Text(
                            title,
                            style = ClockTheme.typography.title,
                            color = colors.content,
                            modifier = Modifier
                                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)
                                .semantics { heading() },
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                            .padding(horizontal = 24.dp)
                            .padding(top = if (title == null) 24.dp else 0.dp, bottom = 20.dp),
                        content = content,
                    )
                    if (actions.isNotEmpty()) {
                        PanelDivider()
                        DialogButtons(actions, Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

/** A short message with buttons, e.g. "Discard changes?". */
@Composable
fun GlassAlertDialog(title: String, text: String?, onDismiss: () -> Unit, actions: List<DialogAction>) {
    GlassDialog(title = title, onDismiss = onDismiss, actions = actions) {
        if (text != null) Text(text, style = ClockTheme.typography.body, color = ClockTheme.colors.contentMuted)
    }
}

/** The dialog's buttons side by side, sharing the width; the main action is filled with the accent. */
@Composable
fun DialogButtons(actions: List<DialogAction>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.forEach { action -> DialogButton(action, Modifier.weight(1f)) }
    }
}

@Composable
fun DialogButton(action: DialogAction, modifier: Modifier = Modifier) {
    val colors = ClockTheme.colors
    val (background, border, text) = when {
        !action.enabled -> Triple(Color.Transparent, colors.content.copy(alpha = 0.12f), colors.contentMuted)
        action.style == DialogButtonStyle.Primary -> Triple(colors.accent, colors.accent, colors.onAccent)
        action.style == DialogButtonStyle.Destructive -> Triple(Color.Transparent, colors.accent, colors.accent)
        else -> Triple(colors.content.copy(alpha = 0.08f), colors.content.copy(alpha = 0.28f), colors.content)
    }
    val haptics = rememberHaptics()
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(CapsuleShape)
            .background(background, CapsuleShape)
            .border(1.dp, border, CapsuleShape)
            .clickable(enabled = action.enabled, role = Role.Button) {
                when (action.style) {
                    DialogButtonStyle.Primary -> haptics.confirm()
                    DialogButtonStyle.Destructive -> haptics.reject()
                    DialogButtonStyle.Secondary -> Unit
                }
                action.onClick()
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(action.label, style = ClockTheme.typography.label, color = text, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/**
 * A menu of actions or choices as a glass sheet from the bottom: big rows with icons separated by
 * dividers; dismissed by tapping outside, swiping it down or Back. [header] replaces the plain
 * [title], e.g. with a preview.
 */
@Composable
fun OptionsSheet(
    title: String,
    options: List<SheetOption>,
    onDismiss: () -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    GlassSheet(title = title, onDismiss = onDismiss, header = header) {
        options.forEachIndexed { index, option ->
            if (index > 0) PanelDivider(Modifier.padding(start = if (option.icon != null) 68.dp else 24.dp))
            SheetRow(option)
        }
    }
}

/** A glass sheet from the bottom holding any [content]; it scrolls when taller than the screen allows. */
@Composable
fun GlassSheet(
    title: String,
    onDismiss: () -> Unit,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Overlay {
        val colors = ClockTheme.colors
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val appear = remember { Animatable(0f) }
        val drag = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, tween(220)) }
        BackHandler(onBack = onDismiss)
        Scrim(appear.value, onDismiss)
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            val maxSheet = maxHeight * 0.85f
            val slide = with(density) { 120.dp.toPx() }
            OverlayGlass(
                shape = SheetShape,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 8.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxSheet)
                    .graphicsLayer {
                        translationY = drag.value + (1f - appear.value) * slide
                        alpha = appear.value
                    }
                    .semantics { paneTitle = title },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // The handle and title area drags the sheet down to close it.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }
                                },
                                onDragStopped = { velocity ->
                                    if (drag.value > with(density) { 96.dp.toPx() } || velocity > 2000f) {
                                        onDismiss()
                                    } else {
                                        drag.animateTo(0f)
                                    }
                                },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .padding(top = 10.dp, bottom = 6.dp)
                                .size(width = 40.dp, height = 4.dp)
                                .clip(CapsuleShape)
                                .background(colors.content.copy(alpha = 0.3f)),
                        )
                        Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 14.dp)) {
                            header?.invoke() ?: Text(
                                title,
                                style = ClockTheme.typography.title,
                                color = colors.content,
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                    }
                    PanelDivider()
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        content = content,
                    )
                    if (footer != null) {
                        PanelDivider()
                        Box(Modifier.padding(16.dp)) { footer() }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(option: SheetOption) {
    val colors = ClockTheme.colors
    val tint = when {
        option.destructive || option.selected -> colors.accent
        else -> colors.content
    }
    val haptics = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                if (option.destructive) haptics.reject() else haptics.tick()
                option.onClick()
            }
            .heightIn(min = 56.dp)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (option.icon != null) {
            Icon(painterResource(option.icon), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(option.label, style = ClockTheme.typography.body, color = tint)
            option.summary?.let { Text(it, style = ClockTheme.typography.caption, color = colors.contentMuted) }
        }
        if (option.selected) {
            Spacer(Modifier.width(12.dp))
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        }
    }
}

/** A list of plain choices; each closes the sheet through its own action. */
@Composable
fun OptionsDialog(title: String, options: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    OptionsSheet(title = title, options = options.map { (label, action) -> SheetOption(label, onClick = action) }, onDismiss = onDismiss)
}

/** A single-line text prompt, e.g. a group name. */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    maxLength: Int,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ClockTheme.colors
    var text by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    GlassDialog(
        title = title,
        onDismiss = onDismiss,
        actions = listOf(
            DialogAction(stringResource(R.string.action_cancel), onClick = onDismiss),
            DialogAction(stringResource(R.string.action_save), DialogButtonStyle.Primary, enabled = text.isNotBlank()) { onConfirm(text) },
        ),
    ) {
        // The field takes focus and the keyboard opens, so the name can be typed straight away.
        // This runs inside the overlay, once the field exists.
        LaunchedEffect(Unit) { focus.requestFocus() }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(maxLength) },
            label = { Text(label) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.content,
                unfocusedBorderColor = colors.content.copy(alpha = 0.3f),
                focusedLabelColor = colors.content,
                unfocusedLabelColor = colors.contentMuted,
                cursorColor = colors.accent,
            ),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

/** A hairline between parts of a dialog or sheet, visible on glass in both themes. */
@Composable
fun PanelDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = ClockTheme.colors.content.copy(alpha = 0.14f))
}

@Composable
private fun Scrim(progress: Float, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress }
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    )
}

/** Raised glass over the screen behind; solid when there is no overlay host to sample. */
@Composable
private fun OverlayGlass(shape: RoundedCornerShape, modifier: Modifier, content: @Composable () -> Unit) {
    val host = LocalOverlayHost.current
    val level = if (host == null) GlassLevel.Solid else LocalGlassLevel.current
    CompositionLocalProvider(LocalGlassLevel provides level) {
        GlassSurface(
            backdrop = host?.backdrop ?: rememberGlassBackdrop(),
            shape = shape,
            raised = true,
            contentAlignment = Alignment.TopStart,
            // Swallow taps so they do not reach the scrim.
            modifier = modifier.pointerInput(Unit) { detectTapGestures {} },
        ) { content() }
    }
}

private val DialogShape = RoundedCornerShape(30.dp)
private val SheetShape = RoundedCornerShape(32.dp)
