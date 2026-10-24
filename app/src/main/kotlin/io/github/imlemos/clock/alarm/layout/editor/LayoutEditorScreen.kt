package io.github.imlemos.clock.alarm.layout.editor

import io.github.imlemos.clock.core.designsystem.Motion
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import io.github.imlemos.clock.core.designsystem.animatedColor
import io.github.imlemos.clock.core.designsystem.rememberHaptics
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import io.github.imlemos.clock.core.designsystem.component.DialogButtonStyle
import io.github.imlemos.clock.core.designsystem.component.DialogAction
import io.github.imlemos.clock.core.designsystem.component.GlassAlertDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.layout.Background
import io.github.imlemos.clock.alarm.layout.DateArrangement
import io.github.imlemos.clock.alarm.layout.DateParts
import io.github.imlemos.clock.alarm.layout.GradientDirection
import io.github.imlemos.clock.alarm.layout.ClockElement
import io.github.imlemos.clock.alarm.layout.ClockFont
import io.github.imlemos.clock.alarm.layout.ClockStyle
import io.github.imlemos.clock.alarm.layout.render.effectiveWeight
import io.github.imlemos.clock.alarm.layout.render.rememberClockFontFamily
import io.github.imlemos.clock.alarm.layout.render.spec
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.verticalScroll
import io.github.imlemos.clock.core.designsystem.component.DotDigits
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import io.github.imlemos.clock.alarm.layout.LayoutGeometry
import io.github.imlemos.clock.alarm.layout.PresetKind
import io.github.imlemos.clock.alarm.layout.render.AlarmLayoutView
import io.github.imlemos.clock.alarm.layout.render.LayoutEditing
import io.github.imlemos.clock.alarm.layout.render.LayoutElementId
import io.github.imlemos.clock.alarm.layout.render.LayoutLabel
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.ringing.AlarmActivity
import io.github.imlemos.clock.alarm.ringing.AlarmControls
import io.github.imlemos.clock.alarm.ui.Label
import io.github.imlemos.clock.alarm.ui.formatSnooze
import io.github.imlemos.clock.core.designsystem.component.ClockSwitch
import io.github.imlemos.clock.core.designsystem.component.ColorPickerDialog
import io.github.imlemos.clock.core.designsystem.component.GlassIconButton
import io.github.imlemos.clock.core.designsystem.component.TextInputDialog
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.glass.GlassBackdrop
import io.github.imlemos.clock.core.designsystem.glass.GlassSurface
import io.github.imlemos.clock.core.designsystem.glass.LocalGlassLevel
import io.github.imlemos.clock.core.designsystem.glass.rememberGlassBackdrop
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import java.time.LocalDateTime

/**
 * The layout editor (docs/PLANO.md 3.7): the alarm screen at full size, where elements are dragged
 * and pinched, with a floating glass panel for styles, colours, sizes and the background.
 */
@Composable
fun LayoutEditorScreen(
    layoutId: Long,
    newName: String?,
    requestId: Long,
    onClose: () -> Unit,
    viewModel: LayoutEditorViewModel = hiltViewModel<LayoutEditorViewModel, LayoutEditorViewModel.Factory>(
        key = "layout-editor-$layoutId-$newName-$requestId",
        creationCallback = { it.create(layoutId, newName, requestId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
    var panelVisible by rememberSaveable { mutableStateOf(true) }
    val sampleLabel = stringResource(R.string.layout_sample_label)
    val haptics = rememberHaptics()
    BackHandler { if (state.unsaved) confirmingDiscard = true else onClose() }

    // The alarm screen is always dark, so the editor is too.
    ClockTheme(darkTheme = true, accent = ClockTheme.colors.accent, glassLevel = LocalGlassLevel.current) {
        val colors = ClockTheme.colors
        val backdrop = rememberGlassBackdrop()
        val now = remember { LocalDateTime.now() }
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().background(Color.Black))
            return@ClockTheme
        }
        Box(Modifier.fillMaxSize()) {
            AlarmLayoutView(
                spec = state.spec,
                time = TimeOfDay(now.hour, now.minute),
                date = now.toLocalDate(),
                labels = listOf(LayoutLabel(sampleLabel)),
                backdrop = backdrop,
                controls = {
                    AlarmControls(
                        backdrop = backdrop,
                        snoozeLabel = stringResource(
                            R.string.alarm_action_snooze,
                            formatSnooze(LocalResources.current, Alarm.DEFAULT_SNOOZE_MINUTES),
                        ),
                        chooseLabel = "",
                        dismissLabel = stringResource(R.string.alarm_action_dismiss),
                        onSnooze = {},
                        onChooseSnooze = {},
                        onDismiss = {},
                        interactive = false,
                    )
                },
                editing = LayoutEditing(
                    selected = state.selected,
                    onSelect = { id ->
                        // Called on every step of a drag: buzz once, when it starts on a new element.
                        if (id != state.selected) haptics.dragStart()
                        viewModel.highlight(id)
                    },
                    // Tapping an element (or empty space, for the background) opens its options.
                    onOpen = { id ->
                        haptics.tick()
                        viewModel.select(id)
                        panelVisible = true
                    },
                    onPlacementChange = viewModel::setPlacement,
                    // On the Background tab, empty space moves and zooms a photo or video.
                    onBackgroundTransform = viewModel::transformBackground.takeIf {
                        state.tab == null && state.spec.background !is Background.Preset
                    },
                ),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassIconButton(
                    backdrop = backdrop,
                    icon = R.drawable.ic_arrow_back,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = { if (state.unsaved) confirmingDiscard = true else onClose() },
                    size = 48.dp,
                    tint = colors.content,
                )
                GlassSurface(
                    backdrop = backdrop,
                    modifier = Modifier
                        .weight(1f)
                        .clip(CapsuleShape)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.layout_rename)) { renaming = true }
                        .heightIn(min = 48.dp),
                ) {
                    Text(state.name, style = ClockTheme.typography.body, color = colors.content, maxLines = 1, modifier = Modifier.padding(horizontal = 16.dp))
                }
                PillButton(backdrop, stringResource(R.string.layout_test), accent = false) {
                    // Previews what is on screen; nothing is saved.
                    context.startActivity(AlarmActivity.previewIntent(context, state.spec, sampleLabel))
                }
                PillButton(backdrop, stringResource(R.string.action_save), accent = true) {
                    haptics.confirm()
                    viewModel.save(onClose)
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PillButton(
                    backdrop,
                    stringResource(if (panelVisible) R.string.layout_panel_hide else R.string.layout_panel_show),
                    accent = false,
                ) { panelVisible = !panelVisible }
                // At most about half the screen, so the preview above stays visible; the options scroll.
                val maxPanel = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.45f).toDp() }
                AnimatedVisibility(
                    visible = panelVisible,
                    enter = fadeIn(Motion.medium()) + slideInVertically(Motion.medium()) { it / 3 },
                    exit = fadeOut(Motion.fast()) + slideOutVertically(Motion.fast()) { it / 3 },
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        GlassSurface(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = maxPanel),
                            contentAlignment = Alignment.TopStart,
                        ) {
                            EditorPanel(state, viewModel)
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = stringResource(R.string.layout_rename),
            label = stringResource(R.string.layout_name),
            initial = state.name,
            maxLength = MAX_NAME,
            onConfirm = { viewModel.rename(it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
    if (confirmingDiscard) {
        GlassAlertDialog(
            title = stringResource(R.string.layout_discard_title),
            text = null,
            onDismiss = { confirmingDiscard = false },
            actions = listOf(
                DialogAction(stringResource(R.string.action_cancel)) { confirmingDiscard = false },
                DialogAction(stringResource(R.string.layout_discard), DialogButtonStyle.Destructive) {
                    confirmingDiscard = false
                    onClose()
                },
            ),
        )
    }
}

@Composable
private fun PillButton(backdrop: GlassBackdrop, text: String, accent: Boolean, onClick: () -> Unit) {
    val colors = ClockTheme.colors
    val modifier = Modifier
        .clip(CapsuleShape)
        .clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 48.dp)
    if (accent) {
        Box(modifier.background(colors.accent, CapsuleShape), contentAlignment = Alignment.Center) {
            Text(text, style = ClockTheme.typography.label, color = colors.onAccent, modifier = Modifier.padding(horizontal = 18.dp))
        }
    } else {
        GlassSurface(backdrop = backdrop, modifier = modifier) {
            Text(text, style = ClockTheme.typography.label, color = colors.content, modifier = Modifier.padding(horizontal = 18.dp))
        }
    }
}

@Composable
private fun EditorPanel(state: LayoutEditorState, viewModel: LayoutEditorViewModel) {
    val context = LocalContext.current
    val failed = stringResource(R.string.layout_import_failed)
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importBackground(uri, video = false) { Toast.makeText(context, failed, Toast.LENGTH_SHORT).show() }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importBackground(uri, video = true) { Toast.makeText(context, failed, Toast.LENGTH_SHORT).show() }
    }
    val spec = state.spec
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        EditorTabs(selected = state.tab, onSelect = viewModel::showTab)
        Spacer(Modifier.height(16.dp))
        when (val tab = state.tab) {
            LayoutElementId.CLOCK -> {
                Label(stringResource(R.string.layout_style_title))
                OptionChips(
                    options = listOf(
                        ClockStyle.DIGITAL to stringResource(R.string.layout_style_digital),
                        ClockStyle.STACKED to stringResource(R.string.layout_style_stacked),
                        ClockStyle.ANALOG to stringResource(R.string.layout_style_analog),
                        ClockStyle.ANALOG_LINES to stringResource(R.string.layout_style_lines),
                    ),
                    selected = spec.clock.style,
                    onSelect = viewModel::setClockStyle,
                )
                ClockFontOptions(spec.clock, viewModel)
                SizeSlider(tab, state.spec.placementOf(tab).scale, viewModel)
                ColorSwatches(spec.clock.color, allowAuto = true) { viewModel.setColor(tab, it) }
            }
            LayoutElementId.DATE, LayoutElementId.LABEL -> {
                val element = if (tab == LayoutElementId.DATE) spec.date else spec.label
                ShowSwitch(element.visible) { viewModel.setVisible(tab, it) }
                if (tab == LayoutElementId.DATE) DateOptions(element.dateParts, viewModel::setDateParts)
                SizeSlider(tab, state.spec.placementOf(tab).scale, viewModel)
                ColorSwatches(element.color, allowAuto = true) { viewModel.setColor(tab, it) }
            }
            LayoutElementId.CONTROLS -> SizeSlider(tab, state.spec.placementOf(tab).scale, viewModel)
            null -> BackgroundOptions(
                background = spec.background,
                onPreset = viewModel::setPreset,
                onBackground = viewModel::setBackground,
                onPickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onPickVideo = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.layout_hint), style = ClockTheme.typography.caption, color = ClockTheme.colors.contentMuted)
    }
}

/** Font (digital clocks), thickness and, for the dot font, how round the dots are. */
@Composable
private fun ClockFontOptions(clock: ClockElement, viewModel: LayoutEditorViewModel) {
    val colors = ClockTheme.colors
    val haptics = rememberHaptics()
    val analog = clock.style == ClockStyle.ANALOG || clock.style == ClockStyle.ANALOG_LINES
    if (!analog) {
        Spacer(Modifier.height(12.dp))
        Label(stringResource(R.string.layout_font))
        FontChips(selected = clock.font, onSelect = viewModel::setClockFont)
    }
    val range = if (analog) 100..900 else clock.font.spec.minWeight..clock.font.spec.maxWeight
    Spacer(Modifier.height(8.dp))
    Label(stringResource(R.string.layout_weight))
    Slider(
        value = (if (analog) clock.weight ?: 400 else clock.effectiveWeight()).coerceIn(range).toFloat(),
        onValueChange = { viewModel.setClockWeight(it.roundToInt()) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        colors = sliderColors(),
    )
    if (!analog && clock.font == ClockFont.DOTO) {
        Label(stringResource(R.string.layout_roundness))
        Slider(
            value = clock.roundness.toFloat(),
            onValueChange = { viewModel.setClockRoundness(it.roundToInt()) },
            valueRange = 0f..100f,
            colors = sliderColors(),
        )
    }
}

@Composable
private fun SizeSlider(id: LayoutElementId, scale: Float, viewModel: LayoutEditorViewModel) {
    val range = if (id == LayoutElementId.CONTROLS) {
        LayoutGeometry.CONTROLS_MIN_SCALE..LayoutGeometry.CONTROLS_MAX_SCALE
    } else {
        LayoutGeometry.MIN_SCALE..LayoutGeometry.MAX_SCALE
    }
    Spacer(Modifier.height(8.dp))
    Label(stringResource(R.string.layout_size))
    Slider(
        value = scale.coerceIn(range),
        onValueChange = { viewModel.setScale(id, it) },
        valueRange = range,
        colors = sliderColors(),
    )
}

@Composable
private fun ShowSwitch(visible: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = visible, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.layout_show), style = ClockTheme.typography.body, color = ClockTheme.colors.content, modifier = Modifier.weight(1f))
        ClockSwitch(checked = visible, onCheckedChange = null)
    }
}

@Composable
private fun BackgroundOptions(
    background: Background,
    onPreset: (PresetKind) -> Unit,
    onBackground: (Background) -> Unit,
    onPickPhoto: () -> Unit,
    onPickVideo: () -> Unit,
) {
    val selected: Any = when (background) {
        is Background.Preset -> background.kind
        is Background.Image -> PHOTO
        is Background.Video -> VIDEO
    }
    Label(stringResource(R.string.layout_bg_type))
    OptionChips(
        options = listOf<Pair<Any, String>>(
            PresetKind.DOTS to stringResource(R.string.layout_bg_dots),
            PresetKind.WAVES to stringResource(R.string.layout_bg_waves),
            PresetKind.MATRIX to stringResource(R.string.layout_bg_matrix),
            PresetKind.SOLID to stringResource(R.string.layout_bg_solid),
            PresetKind.GRADIENT to stringResource(R.string.layout_bg_gradient),
            PHOTO to stringResource(R.string.layout_bg_photo),
            VIDEO to stringResource(R.string.layout_bg_video),
        ),
        selected = selected,
        onSelect = { choice ->
            when (choice) {
                is PresetKind -> onPreset(choice)
                PHOTO -> onPickPhoto()
                VIDEO -> onPickVideo()
            }
        },
    )
    Spacer(Modifier.height(8.dp))
    when (background) {
        is Background.Preset -> {
            if (background.kind == PresetKind.GRADIENT) {
                Label(stringResource(R.string.layout_gradient_direction))
                OptionChips(
                    options = listOf(
                        GradientDirection.DOWN to stringResource(R.string.layout_gradient_down),
                        GradientDirection.ACROSS to stringResource(R.string.layout_gradient_across),
                        GradientDirection.DIAGONAL_DOWN to stringResource(R.string.layout_gradient_diagonal_down),
                        GradientDirection.DIAGONAL_UP to stringResource(R.string.layout_gradient_diagonal_up),
                    ),
                    selected = background.direction,
                    onSelect = { onBackground(background.copy(direction = it)) },
                )
                Spacer(Modifier.height(12.dp))
            }
            ColorSwatches(background.colorA, allowAuto = true) { onBackground(background.copy(colorA = it)) }
            if (background.kind == PresetKind.GRADIENT || background.kind == PresetKind.DOTS ||
                background.kind == PresetKind.WAVES || background.kind == PresetKind.MATRIX
            ) {
                Label(stringResource(R.string.layout_bg_second_color))
                ColorSwatches(background.colorB, allowAuto = true, showLabel = false) { onBackground(background.copy(colorB = it)) }
            }
        }
        is Background.Image -> {
            DimSlider(background.dim) { onBackground(background.copy(dim = it)) }
            BackgroundHint()
        }
        is Background.Video -> {
            DimSlider(background.dim) { onBackground(background.copy(dim = it)) }
            BackgroundHint()
        }
    }
}

@Composable
private fun BackgroundHint() {
    Text(stringResource(R.string.layout_bg_hint), style = ClockTheme.typography.caption, color = ClockTheme.colors.content)
}

@Composable
private fun DimSlider(dim: Float, onChange: (Float) -> Unit) {
    Label(stringResource(R.string.layout_dim))
    Slider(value = dim, onValueChange = onChange, valueRange = 0f..0.8f, colors = sliderColors())
}

/** The layout's elements as tabs; the background is the `null` tab. */
@Composable
private fun EditorTabs(selected: LayoutElementId?, onSelect: (LayoutElementId?) -> Unit) {
    EditorTabRow(
        tabs = listOf(
            EditorTab(LayoutElementId.CLOCK, R.string.layout_tab_clock, R.drawable.ic_schedule),
            EditorTab(LayoutElementId.DATE, R.string.layout_tab_date, R.drawable.ic_calendar_today),
            EditorTab(LayoutElementId.LABEL, R.string.layout_tab_label, R.drawable.ic_label),
            EditorTab(LayoutElementId.CONTROLS, R.string.layout_tab_buttons, R.drawable.ic_touch_app),
            EditorTab(null, R.string.layout_tab_background, R.drawable.ic_wallpaper),
        ),
        selected = selected,
        onSelect = onSelect,
    )
}

private const val PHOTO = "photo"
private const val VIDEO = "video"
private const val MAX_NAME = 40

