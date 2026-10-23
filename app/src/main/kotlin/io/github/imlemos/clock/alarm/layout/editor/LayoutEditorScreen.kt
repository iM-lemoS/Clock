package io.github.imlemos.clock.alarm.layout.editor

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
                    onSelect = viewModel::highlight,
                    // Tapping an element (or empty space, for the background) opens its options.
                    onOpen = { id ->
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
                PillButton(backdrop, stringResource(R.string.action_save), accent = true) { viewModel.save(onClose) }
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
                if (panelVisible) {
                    Spacer(Modifier.height(8.dp))
                    // At most about half the screen, so the preview above stays visible; the options scroll.
                    val maxPanel = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.45f).toDp() }
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
    val analog = clock.style == ClockStyle.ANALOG || clock.style == ClockStyle.ANALOG_LINES
    if (!analog) {
        Spacer(Modifier.height(12.dp))
        Label(stringResource(R.string.layout_font))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClockFont.entries.forEach { font ->
                val selected = font == clock.font
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) colors.accent.copy(alpha = 0.18f) else colors.content.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                        .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.content.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .selectable(selected = selected, role = Role.RadioButton) { viewModel.setClockFont(font) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Each font shows itself; Doto through DotDigits, which draws round colon dots.
                    val sample = TextStyle(fontFamily = rememberClockFontFamily(font, font.spec.defaultWeight), fontSize = 22.sp, lineHeight = 26.sp)
                    val sampleColor = colors.content
                    if (font == ClockFont.DOTO) {
                        DotDigits("12:30", style = sample, color = sampleColor, minFontSize = 22.sp)
                    } else {
                        Text("12:30", style = sample, color = sampleColor, maxLines = 1)
                    }
                    Text(
                        stringResource(font.nameRes),
                        style = ClockTheme.typography.caption,
                        color = if (selected) colors.content else colors.contentMuted,
                    )
                }
            }
        }
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

private val ClockFont.nameRes: Int
    get() = when (this) {
        ClockFont.DOTO -> R.string.font_dots
        ClockFont.MONO -> R.string.font_mono
        ClockFont.OUTFIT -> R.string.font_geometric
        ClockFont.SERIF -> R.string.font_serif
        ClockFont.ORBITRON -> R.string.font_futuristic
        ClockFont.CONDENSED -> R.string.font_condensed
        ClockFont.ROUNDED -> R.string.font_rounded
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

/** The current colour (or "Automatic colour"); tapping opens the full colour picker. */
@Composable
private fun ColorSwatches(selected: Int?, allowAuto: Boolean, showLabel: Boolean = true, onSelect: (Int?) -> Unit) {
    val colors = ClockTheme.colors
    var picking by rememberSaveable { mutableStateOf(false) }
    if (showLabel) Label(stringResource(R.string.layout_color))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CapsuleShape)
            .border(1.dp, colors.outline, CapsuleShape)
            .clickable(role = Role.Button) { picking = true }
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(selected?.let(::Color) ?: Color.Transparent, CircleShape)
                .border(1.dp, colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected == null) Text("A", style = ClockTheme.typography.label, color = colors.content)
        }
        Spacer(Modifier.size(12.dp))
        Text(
            selected?.let { "#%06X · %d%%".format(it and 0xFFFFFF, ((it ushr 24) * 100 / 255)) }
                ?: stringResource(R.string.layout_color_auto),
            style = ClockTheme.typography.body,
            color = colors.content,
        )
    }
    Spacer(Modifier.height(8.dp))
    if (picking) {
        ColorPickerDialog(
            initial = selected,
            allowAuto = allowAuto,
            onPick = { onSelect(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = ClockTheme.colors.accent,
    activeTrackColor = ClockTheme.colors.accent,
    inactiveTrackColor = ClockTheme.colors.outline,
)

/**
 * The elements as tabs: icon and name, the chosen one underlined in the accent colour. They look
 * nothing like the option chips below them, which hold the chosen element's settings.
 */
@Composable
private fun EditorTabs(selected: LayoutElementId?, onSelect: (LayoutElementId?) -> Unit) {
    val colors = ClockTheme.colors
    val tabs = listOf(
        Triple(LayoutElementId.CLOCK, R.string.layout_tab_clock, R.drawable.ic_schedule),
        Triple(LayoutElementId.DATE, R.string.layout_tab_date, R.drawable.ic_calendar_today),
        Triple(LayoutElementId.LABEL, R.string.layout_tab_label, R.drawable.ic_label),
        Triple(LayoutElementId.CONTROLS, R.string.layout_tab_buttons, R.drawable.ic_touch_app),
        Triple(null, R.string.layout_tab_background, R.drawable.ic_wallpaper),
    )
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            tabs.forEach { (id, label, icon) ->
                val isSelected = id == selected
                val tint = if (isSelected) colors.content else colors.contentMuted
                Column(
                    Modifier
                        .weight(1f)
                        .selectable(selected = isSelected, role = Role.Tab) { onSelect(id) }
                        .padding(top = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(painterResource(icon), contentDescription = null, tint = if (isSelected) colors.accent else tint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(label),
                        style = ClockTheme.typography.caption,
                        color = tint,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = ClockTheme.typography.caption.fontSize),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .size(width = 28.dp, height = 3.dp)
                            .clip(CapsuleShape)
                            .background(if (isSelected) colors.accent else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.content.copy(alpha = 0.16f))
    }
}

/**
 * Which parts of the date show (at least one stays on) and, with the weekday and something else,
 * how they are arranged.
 */
@Composable
private fun DateOptions(parts: DateParts, onChange: (DateParts) -> Unit) {
    Spacer(Modifier.height(8.dp))
    Label(stringResource(R.string.layout_date_parts))
    val options = listOf(
        stringResource(R.string.layout_date_weekday) to parts.weekday,
        stringResource(R.string.layout_date_day) to parts.day,
        stringResource(R.string.layout_date_month) to parts.month,
        stringResource(R.string.layout_date_year) to parts.year,
    )
    ToggleChips(options) { index, on ->
        val next = when (index) {
            0 -> parts.copy(weekday = on)
            1 -> parts.copy(day = on)
            2 -> parts.copy(month = on)
            else -> parts.copy(year = on)
        }
        if (next.weekday || next.hasRest) onChange(next)
    }
    if (parts.weekday && parts.hasRest) {
        Spacer(Modifier.height(12.dp))
        Label(stringResource(R.string.layout_date_arrangement))
        OptionChips(
            options = listOf(
                DateArrangement.INLINE to stringResource(R.string.layout_date_inline),
                DateArrangement.WEEKDAY_ABOVE to stringResource(R.string.layout_date_weekday_above),
                DateArrangement.WEEKDAY_BELOW to stringResource(R.string.layout_date_weekday_below),
            ),
            selected = parts.arrangement,
            onSelect = { onChange(parts.copy(arrangement = it)) },
        )
    }
    Spacer(Modifier.height(4.dp))
}

/** Several options that can be on together, in the same style as [OptionChips]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToggleChips(options: List<Pair<String, Boolean>>, onToggle: (index: Int, on: Boolean) -> Unit) {
    val colors = ClockTheme.colors
    val shape = RoundedCornerShape(12.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, (label, on) ->
            Row(
                Modifier
                    .clip(shape)
                    .background(if (on) colors.accent.copy(alpha = 0.18f) else colors.content.copy(alpha = 0.06f), shape)
                    .border(if (on) 2.dp else 1.dp, if (on) colors.accent else colors.content.copy(alpha = 0.2f), shape)
                    .toggleable(value = on, role = Role.Checkbox) { onToggle(index, it) }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (on) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = ClockTheme.typography.label, color = colors.content)
            }
        }
    }
}

/**
 * Choices for the chosen element (clock style, background type): rounded rectangles, the chosen
 * one outlined in the accent colour with a check.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> OptionChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val colors = ClockTheme.colors
    val shape = RoundedCornerShape(12.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Row(
                Modifier
                    .clip(shape)
                    .background(if (isSelected) colors.accent.copy(alpha = 0.18f) else colors.content.copy(alpha = 0.06f), shape)
                    .border(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.accent else colors.content.copy(alpha = 0.2f), shape)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(value) }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelected) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = ClockTheme.typography.label, color = colors.content)
            }
        }
    }
}

private const val PHOTO = "photo"
private const val VIDEO = "video"
private const val MAX_NAME = 40

